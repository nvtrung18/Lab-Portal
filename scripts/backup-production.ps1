[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'backups')
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot 'docker-compose.prod.yml'
$envFile = Join-Path $repoRoot '.env.production'

if (!(Test-Path -LiteralPath $envFile)) {
    throw "Missing $envFile."
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupFile = Join-Path $OutputDirectory "lab-portal-$stamp.sql"

Push-Location $repoRoot
try {
    docker compose --env-file $envFile -f $composeFile exec -T mysql sh -c 'exec mysqldump --single-transaction --routines --triggers -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' | Set-Content -LiteralPath $backupFile -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw 'MySQL backup failed.' }
    Write-Output "Backup written to $backupFile"
}
finally {
    Pop-Location
}
