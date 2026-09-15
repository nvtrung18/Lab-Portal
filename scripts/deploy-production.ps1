[CmdletBinding()]
param(
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot 'docker-compose.prod.yml'
$envFile = Join-Path $repoRoot '.env.production'

if (!(Test-Path -LiteralPath $envFile)) {
    throw "Missing $envFile. Copy .env.production.example and fill every production value first."
}

Push-Location $repoRoot
try {
    docker compose --env-file $envFile -f $composeFile config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Production Compose configuration is invalid.' }

    if ($Build) {
        docker compose --env-file $envFile -f $composeFile build
        if ($LASTEXITCODE -ne 0) { throw 'Production image build failed.' }
    }

    docker compose --env-file $envFile -f $composeFile up -d --remove-orphans
    if ($LASTEXITCODE -ne 0) { throw 'Production Compose startup failed.' }

    docker compose --env-file $envFile -f $composeFile ps
}
finally {
    Pop-Location
}
