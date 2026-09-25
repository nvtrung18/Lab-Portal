[CmdletBinding()]
param(
    [switch]$Build,
    [string]$EnvFile
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot 'docker-compose.prod.yml'
$envFile = if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    Join-Path $repoRoot '.env.production'
} else {
    [System.IO.Path]::GetFullPath($EnvFile)
}

if (!(Test-Path -LiteralPath $envFile)) {
    throw "Missing $envFile. Copy .env.production.example and fill every production value first."
}

$placeholderKeys = @()
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*([A-Z][A-Z0-9_]*)\s*=\s*(.*?)\s*$') {
        $key = $matches[1]
        $value = $matches[2].Trim().Trim('"').Trim("'")
        if ($value -match '(?i)^replace_with_' -or $value -match '(?i)(^|[.@])example\.com$') {
            $placeholderKeys += $key
        }
    }
}
if ($placeholderKeys.Count -gt 0) {
    throw "Production env still has template values for: $($placeholderKeys -join ', '). Replace them before deployment."
}

Push-Location $repoRoot
try {
    docker compose --env-file $envFile -f $composeFile config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Production Compose configuration is invalid.' }

    if ($Build) {
        docker compose --env-file $envFile -f $composeFile build
        if ($LASTEXITCODE -ne 0) { throw 'Production image build failed.' }
    }

    docker compose --env-file $envFile -f $composeFile up -d --remove-orphans --wait --wait-timeout 1800
    if ($LASTEXITCODE -ne 0) { throw 'Production Compose startup failed.' }

    docker compose --env-file $envFile -f $composeFile ps
}
finally {
    Pop-Location
}
