[CmdletBinding()]
param(
    [string]$EnvFile,
    [string]$CredentialDirectory = (Join-Path $env:ProgramData 'LabPortal\DemoCredentials')
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot 'docker-compose.prod.yml'
$envFile = if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    Join-Path $repoRoot '.env.production'
} else {
    [System.IO.Path]::GetFullPath($EnvFile)
}
$foundationPath = Join-Path $repoRoot 'server\src\main\resources\db\demo\seed-production-nckh-foundation.sql'
$existingLabsPath = Join-Path $repoRoot 'server\src\main\resources\db\dev\seed-existing-labs-research.sql'
$thesisPath = Join-Path $repoRoot 'server\src\main\resources\db\dev\seed-nckh-thesis.sql'

foreach ($path in @($envFile, $composeFile, $foundationPath, $existingLabsPath, $thesisPath)) {
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing required file: $path" }
}

function Quote-ProcessArgument([string]$value) {
    return '"' + ($value -replace '(\\*)"', '$1$1\"' -replace '(\\+)$', '$1$1') + '"'
}

function Invoke-DockerInput([string[]]$dockerArguments, [string]$inputText) {
    $start = [System.Diagnostics.ProcessStartInfo]::new()
    $start.FileName = 'docker'
    $start.Arguments = ($dockerArguments | ForEach-Object { Quote-ProcessArgument $_ }) -join ' '
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.StandardOutputEncoding = [System.Text.UTF8Encoding]::new($false)
    $start.StandardErrorEncoding = [System.Text.UTF8Encoding]::new($false)
    $process = [System.Diagnostics.Process]::Start($start)
    try {
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $inputBytes = [System.Text.UTF8Encoding]::new($false).GetBytes($inputText)
        $process.StandardInput.BaseStream.Write($inputBytes, 0, $inputBytes.Length)
        $process.StandardInput.BaseStream.Close()
        $process.WaitForExit()
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            # MySQL errors may contain portions of SQL. Keep the failure local to
            # the operator's console; never put passwords in the SQL stream.
            throw "Docker command failed (exit $($process.ExitCode)): $stderr"
        }
        return $stdout.Trim()
    } finally {
        $process.Dispose()
    }
}

$composeArgs = @('compose', '--env-file', $envFile, '-f', $composeFile)
$mysqlShell = 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot "$MYSQL_DATABASE" --default-character-set=utf8mb4 --batch --raw --skip-column-names'
function Invoke-Mysql([string]$sql) {
    return Invoke-DockerInput ($composeArgs + @('exec', '-T', 'mysql', 'sh', '-ec', $mysqlShell)) $sql
}

function Read-FixtureBody([string]$path) {
    $raw = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    $starts = [regex]::Matches($raw, '(?im)^\s*START TRANSACTION;\s*$')
    $commits = [regex]::Matches($raw, '(?im)^\s*COMMIT;\s*$')
    if ($starts.Count -ne 1 -or $commits.Count -ne 1 -or $starts[0].Index -ge $commits[0].Index) {
        throw "Fixture must contain one ordered transaction pair: $path"
    }
    $body = $raw.Substring(0, $commits[0].Index)
    $body = [regex]::Replace($body, '(?im)^\s*START TRANSACTION;\s*$', '')
    if ([regex]::IsMatch($body, '(?im)^\s*(COMMIT|ROLLBACK|SET\s+FOREIGN_KEY_CHECKS)\b')) {
        throw "Unexpected transaction or FK control in fixture: $path"
    }
    return $body
}

function New-DemoPassword {
    $bytes = [byte[]]::new(24)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Protect-CredentialDirectory([string]$path) {
    if (Test-Path -LiteralPath $path) { throw "Credential directory already exists: $path" }
    New-Item -ItemType Directory -Path $path | Out-Null
    $acl = [System.Security.AccessControl.DirectorySecurity]::new()
    $acl.SetAccessRuleProtection($true, $false)
    $inheritance = [System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor
        [System.Security.AccessControl.InheritanceFlags]::ObjectInherit
    foreach ($sid in @(
        [System.Security.Principal.WindowsIdentity]::GetCurrent().User,
        [System.Security.Principal.SecurityIdentifier]::new('S-1-5-18'),
        [System.Security.Principal.SecurityIdentifier]::new('S-1-5-32-544')
    )) {
        $rule = [System.Security.AccessControl.FileSystemAccessRule]::new(
            $sid, [System.Security.AccessControl.FileSystemRights]::FullControl,
            $inheritance, [System.Security.AccessControl.PropagationFlags]::None,
            [System.Security.AccessControl.AccessControlType]::Allow)
        $acl.AddAccessRule($rule)
    }
    [System.IO.Directory]::SetAccessControl($path, $acl)
}

Push-Location $repoRoot
try {
    & docker compose --env-file $envFile -f $composeFile config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Production Compose configuration is invalid.' }

    # Nothing is written until the live database is confirmed to be a fresh,
    # post-V72 fixture target. Existing users or research must be reviewed by hand.
    $preflight = @'
SELECT CASE WHEN
    (SELECT COUNT(*) FROM flyway_schema_history WHERE version='72' AND success=1)=1
    AND (SELECT COUNT(*) FROM users WHERE username REGEXP '^(manager0[1-5]|student(0[1-9]|1[0-9]|20)|nckh_.*)$'
         OR email REGEXP '^(manager0[1-5]|student(0[1-9]|1[0-9]|20))@example[.]invalid$'
         OR email LIKE 'nckh.%@example.invalid')=0
    AND (SELECT COUNT(*) FROM laboratories WHERE lab_name IN ('AI Research Lab','Robotics Lab','Data Science Lab','Cybersecurity Lab','IoT Innovation Lab','NCKH Thesis Demo 2026'))=0
    AND (SELECT COUNT(*) FROM projects WHERE code IN ('D35-AI-FACE','D35-ROBOT','D35-DS-ANALYTICS','D35-CYBER-IDS','D35-IoT-ENV','THESIS-NCKH-2026','THESIS-NCKH-2026-B','THESIS-NCKH-2026-C'))=0
    THEN 'READY' ELSE 'BLOCKED' END;
'@
    if ((Invoke-Mysql $preflight).Trim() -ne 'READY') {
        throw 'Production data preflight blocked import. Review Flyway V72 and existing mock identities; no data was written.'
    }

    $foundation = Read-FixtureBody $foundationPath
    $existingLabs = Read-FixtureBody $existingLabsPath
    $thesis = Read-FixtureBody $thesisPath
    $oldManagerHash = '$2b$12$NcgrOVryzefwVh/wB1fytOBzAeE4VDyHqyTh9dtoivIPxCK07B3wm'
    $oldStudentHash = '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y'
    if ([regex]::Matches($thesis, [regex]::Escape($oldManagerHash)).Count -ne 1 -or
        [regex]::Matches($thesis, [regex]::Escape($oldStudentHash)).Count -ne 4) {
        throw 'Unexpected development password hash locations; import stopped.'
    }
    $thesis = $thesis.Replace("'$oldManagerHash'", '@demo_manager_hash').Replace("'$oldStudentHash'", '@demo_student_hash')
    if ($thesis.Contains($oldManagerHash) -or $thesis.Contains($oldStudentHash)) {
        throw 'A development password hash remained in the thesis fixture.'
    }

    $accounts = @()
    foreach ($n in 1..5) { $accounts += ('manager{0:d2}' -f $n) }
    foreach ($n in 1..20) { $accounts += ('student{0:d2}' -f $n) }
    $accounts += @('nckh_manager','nckh_leader','nckh_member1','nckh_member2','nckh_member3',
        'nckh_g2_leader','nckh_g2_member1','nckh_g2_member2','nckh_g2_member3',
        'nckh_g3_leader','nckh_g3_member1','nckh_g3_member2','nckh_g3_member3')
    New-Item -ItemType Directory -Force -Path $CredentialDirectory | Out-Null
    $privateDirectory = Join-Path $CredentialDirectory ('nckh-demo-{0}' -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
    Protect-CredentialDirectory $privateDirectory
    $credentialFile = Join-Path $privateDirectory 'credentials.json'
    $pendingCredentialFile = "$credentialFile.pending"
    if ((Test-Path -LiteralPath $credentialFile) -or (Test-Path -LiteralPath $pendingCredentialFile)) {
        throw 'Credential file already exists.'
    }

    $credentials = @()
    $hashes = @{}
    foreach ($username in $accounts) {
        $password = New-DemoPassword
        $hash = Invoke-DockerInput ($composeArgs + @('exec','-T','caddy','caddy','hash-password','--algorithm','bcrypt','--bcrypt-cost','12')) ($password + "`n")
        if ($hash -notmatch '^\$2[aby]\$12\$[./A-Za-z0-9]{53}$') { throw "Invalid BCrypt hash for $username" }
        $hashes[$username] = $hash
        $email = if ($username -like 'nckh_*') {
            switch ($username) {
                'nckh_manager' { 'nckh.manager@example.invalid' }
                'nckh_leader' { 'nckh.leader@example.invalid' }
                default { ($username -replace '^nckh_g([23])_', 'nckh.g$1.' -replace '^nckh_', 'nckh.' -replace '_', '.') + '@example.invalid' }
            }
        } else { "$username@example.invalid" }
        $credentials += [pscustomobject]@{ username=$username; email=$email; password=$password }
    }

    $credentials | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $pendingCredentialFile -Encoding UTF8

    $updates = foreach ($username in $accounts) {
        "UPDATE users SET password = '$($hashes[$username])' WHERE username = '$username';"
    }
    $sql = @"
SET NAMES utf8mb4;
SET @demo_manager_hash = '$($hashes['manager01'])';
SET @demo_student_hash = '$($hashes['student01'])';
START TRANSACTION;
$foundation
$existingLabs
$thesis
$($updates -join "`n")
CREATE TEMPORARY TABLE nckh_import_guard (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO nckh_import_guard (ok)
SELECT CASE WHEN
    (SELECT COUNT(*) FROM users WHERE username REGEXP '^(manager0[1-5]|student(0[1-9]|1[0-9]|20)|nckh_.*)$' AND active=TRUE AND deleted=FALSE)=38
    AND (SELECT COUNT(DISTINCT password) FROM users WHERE username REGEXP '^(manager0[1-5]|student(0[1-9]|1[0-9]|20)|nckh_.*)$')=38
    AND (SELECT COUNT(*) FROM projects WHERE code IN ('D35-AI-FACE','D35-ROBOT','D35-DS-ANALYTICS','D35-CYBER-IDS','D35-IoT-ENV','THESIS-NCKH-2026','THESIS-NCKH-2026-B','THESIS-NCKH-2026-C') AND status='DRAFT')=8
    AND (SELECT COUNT(*) FROM research_topics WHERE lab_id=(SELECT id FROM laboratories WHERE lab_name='NCKH Thesis Demo 2026') AND status='RECRUITING')=3
    AND (SELECT COUNT(*) FROM memberships WHERE lab_id=(SELECT id FROM laboratories WHERE lab_name='NCKH Thesis Demo 2026') AND active=TRUE AND deleted=FALSE)=12
    AND (SELECT COUNT(*) FROM research_groups WHERE project_id IN (SELECT id FROM projects WHERE code IN ('THESIS-NCKH-2026','THESIS-NCKH-2026-B','THESIS-NCKH-2026-C')) AND status='ACTIVE')=3
    AND (SELECT COUNT(*) FROM group_members WHERE group_id IN (SELECT id FROM research_groups WHERE project_id IN (SELECT id FROM projects WHERE code IN ('THESIS-NCKH-2026','THESIS-NCKH-2026-B','THESIS-NCKH-2026-C'))) AND active=TRUE AND deleted=FALSE)=12
    AND (SELECT COUNT(*) FROM research_groups WHERE project_id IN (SELECT id FROM projects WHERE code IN ('D35-AI-FACE','D35-ROBOT','D35-DS-ANALYTICS','D35-CYBER-IDS','D35-IoT-ENV')) AND active=TRUE AND deleted=FALSE)=6
    AND (SELECT COUNT(*) FROM group_members WHERE group_id IN (SELECT id FROM research_groups WHERE project_id IN (SELECT id FROM projects WHERE code IN ('D35-AI-FACE','D35-ROBOT','D35-DS-ANALYTICS','D35-CYBER-IDS','D35-IoT-ENV'))) AND active=TRUE AND deleted=FALSE)=24
    AND NOT EXISTS (SELECT 1 FROM laboratories l WHERE l.lab_name IN ('AI Research Lab','Robotics Lab','Data Science Lab','Cybersecurity Lab','IoT Innovation Lab','NCKH Thesis Demo 2026') AND ((SELECT COUNT(*) FROM memberships m WHERE m.lab_id=l.id AND m.role='DEPUTY' AND m.active=TRUE AND m.deleted=FALSE)<>1 OR (SELECT COUNT(*) FROM memberships m WHERE m.lab_id=l.id AND m.role='SECRETARY' AND m.active=TRUE AND m.deleted=FALSE)<>1))
    AND (SELECT COUNT(*) FROM milestones WHERE project_id IN (SELECT id FROM projects WHERE code IN ('D35-AI-FACE','D35-ROBOT','D35-DS-ANALYTICS','D35-CYBER-IDS','D35-IoT-ENV','THESIS-NCKH-2026','THESIS-NCKH-2026-B','THESIS-NCKH-2026-C')) AND status='NOT_STARTED' AND progress_percent=0)=32
    AND (SELECT COUNT(*) FROM tasks WHERE project_id IN (SELECT id FROM projects WHERE code IN ('D35-AI-FACE','D35-ROBOT','D35-DS-ANALYTICS','D35-CYBER-IDS','D35-IoT-ENV','THESIS-NCKH-2026','THESIS-NCKH-2026-B','THESIS-NCKH-2026-C')) AND status='TODO' AND progress_percent=0)=96
    AND (SELECT COUNT(*) FROM reports WHERE project_id IN (SELECT id FROM projects WHERE code IN ('D35-AI-FACE','D35-ROBOT','D35-DS-ANALYTICS','D35-CYBER-IDS','D35-IoT-ENV','THESIS-NCKH-2026','THESIS-NCKH-2026-B','THESIS-NCKH-2026-C')))=0
    AND (SELECT COUNT(*) FROM products WHERE project_id IN (SELECT id FROM projects WHERE code IN ('D35-AI-FACE','D35-ROBOT','D35-DS-ANALYTICS','D35-CYBER-IDS','D35-IoT-ENV','THESIS-NCKH-2026','THESIS-NCKH-2026-B','THESIS-NCKH-2026-C')))=0
    AND (SELECT COUNT(*) FROM evaluations WHERE project_id IN (SELECT id FROM projects WHERE code IN ('D35-AI-FACE','D35-ROBOT','D35-DS-ANALYTICS','D35-CYBER-IDS','D35-IoT-ENV','THESIS-NCKH-2026','THESIS-NCKH-2026-B','THESIS-NCKH-2026-C')))=0
    THEN 1 ELSE 0 END;
COMMIT;
SELECT 'IMPORTED', (SELECT COUNT(*) FROM projects WHERE code IN ('D35-AI-FACE','D35-ROBOT','D35-DS-ANALYTICS','D35-CYBER-IDS','D35-IoT-ENV','THESIS-NCKH-2026','THESIS-NCKH-2026-B','THESIS-NCKH-2026-C'));
"@
    $result = Invoke-Mysql $sql
    if ($result -notmatch '^IMPORTED\s+8\s*$') { throw 'Import result was unexpected; inspect database state before retrying.' }
    Move-Item -LiteralPath $pendingCredentialFile -Destination $credentialFile
    Write-Output "NCKH production mock imported. Credentials saved to $credentialFile"
} finally {
    Pop-Location
}
