[CmdletBinding()]
param(
    [switch]$CreateZip
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$serverRoot = Join-Path $repoRoot 'server'
$targetRoot = Join-Path $serverRoot 'target'
$stagingRoot = Join-Path $serverRoot '.eb-staging'

if (-not $env:JAVA_HOME) {
    throw 'JAVA_HOME must point to JDK 17 before packaging.'
}

$javaPath = (Get-Command java -ErrorAction Stop).Source
$javaVersion = [System.Diagnostics.FileVersionInfo]::GetVersionInfo($javaPath).ProductVersion
if ($javaVersion -notmatch '^17\.') {
    throw "JDK 17 is required; detected: $javaVersion"
}

Push-Location $serverRoot
try {
    & .\mvnw.cmd clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Maven package failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

$candidates = @(Get-ChildItem -LiteralPath $targetRoot -Filter '*.jar' -File |
    Where-Object { $_.Name -notmatch '\.original$|-(sources|javadoc)\.jar$' })
if ($candidates.Count -ne 1) {
    throw "Expected exactly one executable JAR in $targetRoot; found $($candidates.Count)."
}

if (Test-Path -LiteralPath $stagingRoot) {
    Remove-Item -LiteralPath $stagingRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $stagingRoot | Out-Null
Copy-Item -LiteralPath (Join-Path $serverRoot 'Procfile') -Destination (Join-Path $stagingRoot 'Procfile')
Copy-Item -LiteralPath $candidates[0].FullName -Destination (Join-Path $stagingRoot 'application.jar')

$bundleFiles = @(Get-ChildItem -LiteralPath $stagingRoot -File | Select-Object -ExpandProperty Name | Sort-Object)
if ($bundleFiles.Count -ne 2 -or $bundleFiles -notcontains 'Procfile' -or $bundleFiles -notcontains 'application.jar') {
    throw "Unexpected EB bundle layout: $($bundleFiles -join ', ')"
}

if ($CreateZip) {
    $zipPath = Join-Path $targetRoot 'lab-portal-eb.zip'
    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }
    Compress-Archive -Path (Join-Path $stagingRoot '*') -DestinationPath $zipPath
    Write-Output "Created $zipPath"
}

Write-Output "Bundle root: $stagingRoot"
Get-ChildItem -LiteralPath $stagingRoot -File | Select-Object Name, Length
