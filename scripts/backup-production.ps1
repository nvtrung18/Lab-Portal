[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path $env:ProgramData 'LabPortal\Backups'),
    [ValidateRange(1, 3650)]
    [int]$RetentionDays = 30
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot 'docker-compose.prod.yml'
$envFile = Join-Path $repoRoot '.env.production'

if (!(Test-Path -LiteralPath $envFile)) {
    throw "Missing $envFile."
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$outputRoot = (Resolve-Path -LiteralPath $OutputDirectory).ProviderPath
$outputRootPrefix = [System.IO.Path]::GetFullPath($outputRoot).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupDirectory = Join-Path $outputRoot "lab-portal-$stamp"
if (Test-Path -LiteralPath $backupDirectory) {
    throw "Backup directory already exists: $backupDirectory"
}
New-Item -ItemType Directory -Path $backupDirectory | Out-Null

# Dumps and uploads contain personal data. Protect each new backup directory
# before writing files, regardless of the ACL inherited from the output drive.
$backupAcl = [System.Security.AccessControl.DirectorySecurity]::new()
$backupAcl.SetAccessRuleProtection($true, $false)
$inheritance = [System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor
    [System.Security.AccessControl.InheritanceFlags]::ObjectInherit
$allowedSids = @(
    [System.Security.Principal.WindowsIdentity]::GetCurrent().User,
    [System.Security.Principal.SecurityIdentifier]::new('S-1-5-18'),
    [System.Security.Principal.SecurityIdentifier]::new('S-1-5-32-544')
)
foreach ($sid in $allowedSids) {
    $rule = [System.Security.AccessControl.FileSystemAccessRule]::new(
        $sid,
        [System.Security.AccessControl.FileSystemRights]::FullControl,
        $inheritance,
        [System.Security.AccessControl.PropagationFlags]::None,
        [System.Security.AccessControl.AccessControlType]::Allow
    )
    $backupAcl.AddAccessRule($rule)
}
[System.IO.Directory]::SetAccessControl($backupDirectory, $backupAcl)

$composeArgs = @('--env-file', $envFile, '-f', $composeFile)
$temporaryDumpPath = "/tmp/lab-portal-$stamp.sql"
$dumpCommand = 'exec mysqldump --single-transaction --routines --triggers -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" > "' + $temporaryDumpPath + '"'
$backupComplete = $false

Push-Location $repoRoot
try {
    & docker compose @composeArgs exec -T mysql sh -ec $dumpCommand
    if ($LASTEXITCODE -ne 0) { throw 'MySQL backup failed.' }

    $databaseBackup = Join-Path $backupDirectory 'database.sql'
    & docker compose @composeArgs cp "mysql:$temporaryDumpPath" $databaseBackup
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy the MySQL dump to the backup directory.' }
    if (!(Test-Path -LiteralPath $databaseBackup) -or (Get-Item -LiteralPath $databaseBackup).Length -eq 0) {
        throw 'The MySQL dump is missing or empty.'
    }

    foreach ($volume in @(
        @{ ContainerPath = '/app/storage'; BackupFolder = 'storage' },
        @{ ContainerPath = '/app/uploads'; BackupFolder = 'uploads' }
    )) {
        $destination = Join-Path $backupDirectory $volume.BackupFolder
        New-Item -ItemType Directory -Force -Path $destination | Out-Null
        & docker compose @composeArgs cp "backend:$($volume.ContainerPath)/." $destination
        if ($LASTEXITCODE -ne 0) { throw "Could not back up $($volume.ContainerPath)." }
    }

    $metadata = @{
        backupFormat = 'LabPortalBackupV1'
        createdAt = (Get-Date).ToString('o')
        contents = @('database.sql', 'storage/', 'uploads/')
    }
    $metadata | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $backupDirectory 'backup-complete.json') -Encoding UTF8
    $backupComplete = $true
}
finally {
    try {
        & docker compose @composeArgs exec -T mysql rm -f $temporaryDumpPath 2>$null | Out-Null
    }
    catch {
        # A temporary dump left in the MySQL container does not invalidate the host backup.
    }

    if (!$backupComplete -and (Test-Path -LiteralPath $backupDirectory)) {
        $failedBackup = Get-Item -LiteralPath $backupDirectory
        if (!$failedBackup.FullName.StartsWith($outputRootPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
            $failedBackup.Name -notmatch '^lab-portal-\d{8}-\d{6}$' -or
            ($failedBackup.Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
            throw "Refusing to remove unexpected backup path: $backupDirectory"
        }
        Remove-Item -LiteralPath $backupDirectory -Recurse -Force
    }
    Pop-Location
}

$retentionCutoff = (Get-Date).AddDays(-$RetentionDays)
$expiredBackups = Get-ChildItem -LiteralPath $outputRoot -Directory -Filter 'lab-portal-*' |
    Where-Object {
        $_.Name -match '^lab-portal-\d{8}-\d{6}$' -and
        -not ($_.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -and
        [System.IO.Path]::GetFullPath($_.FullName).StartsWith($outputRootPrefix, [System.StringComparison]::OrdinalIgnoreCase) -and
        $_.LastWriteTime -lt $retentionCutoff -and
        (Test-Path -LiteralPath (Join-Path $_.FullName 'backup-complete.json')) -and
        ((Get-Content -LiteralPath (Join-Path $_.FullName 'backup-complete.json') -Raw -ErrorAction SilentlyContinue | ConvertFrom-Json -ErrorAction SilentlyContinue).backupFormat -eq 'LabPortalBackupV1')
    }
foreach ($expiredBackup in $expiredBackups) {
    Remove-Item -LiteralPath $expiredBackup.FullName -Recurse -Force
}

Write-Output "Backup written to $backupDirectory"
