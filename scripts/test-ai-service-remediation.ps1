[CmdletBinding()]
param(
    [ValidateSet('Unit','Fixtures','Integration','UI','Runtime','RuntimeFixtures','All')][string]$Group = 'All',
    [string]$Python = 'python',
    [string]$OutputDirectory,
    [string]$KaggleUrl,
    [string]$RuntimeEvidence,
    [ValidatePattern('^[A-Za-z0-9_*+]+$')][string]$FixtureMethods = '*'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$runId = 'ai-hybrid-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8)
if (!$OutputDirectory) { $OutputDirectory = Join-Path $repo ('.artifacts/' + $runId) }
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw 'Use a new output directory; previous evidence is immutable.' }
New-Item -ItemType Directory -Path $output | Out-Null
$pythonExe = (Get-Command $Python -ErrorAction Stop).Source
$stages = New-Object System.Collections.Generic.List[object]
$ownedProcesses = New-Object System.Collections.Generic.List[object]
$savedEnvironment = @{}
$dbCreated = $false
$redisCreated = $false
$redisName = $runId + '-redis'
$simulation = $null
$exitCode = 0
$runtimeToken = [Environment]::GetEnvironmentVariable('AI_INTERNAL_SERVICE_TOKEN', 'Process')
$isRuntime = $Group -in @('Runtime','RuntimeFixtures')

function Set-TestEnvironment([string]$Name, [string]$Value) {
    if (!$savedEnvironment.ContainsKey($Name)) { $savedEnvironment[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process') }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}
function Invoke-Stage([string]$Name, [string]$Exe, [string[]]$CommandArgs, [string]$Directory) {
    $log = Join-Path $output ($Name + '.log')
    Write-Host ('Running ' + $Name)
    Push-Location -LiteralPath $Directory
    $oldPreference = $ErrorActionPreference
    $stageStarted = [DateTime]::UtcNow
    try {
        $ErrorActionPreference = 'Continue'
        & $Exe @CommandArgs *> $log
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $oldPreference; Pop-Location }
    $stages.Add(@{name=$Name; exitCode=$code; log=$log})
    if ($Exe -like '*mvnw.cmd') {
        $reportDirectory = Join-Path $output ('junit/' + $Name)
        New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
        Get-ChildItem -LiteralPath (Join-Path $repo 'server/target/surefire-reports') -Filter 'TEST-*.xml' |
            Where-Object { $_.LastWriteTimeUtc -ge $stageStarted } |
            Copy-Item -Destination $reportDirectory
    }
    if ($code -ne 0) { throw ($Name + ' failed; see ' + $log) }
    Write-Host ($Name + ': PASS')
}
function Assert-FreePort([int]$Port) {
    if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) {
        throw ('Port ' + $Port + ' is occupied; refusing to stop or reuse another service.')
    }
}
function Start-Owned([string]$Exe, [string[]]$CommandArgs, [string]$Directory, [string]$Name) {
    $process = Start-Process -FilePath $Exe -ArgumentList $CommandArgs -WorkingDirectory $Directory -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $output ($Name + '.stdout.log')) `
        -RedirectStandardError (Join-Path $output ($Name + '.stderr.log')) -PassThru
    $ownedProcesses.Add($process)
    return $process
}

try {
    $sourceFiles = @(& git -C $repo ls-files --cached --others --exclude-standard) |
        Where-Object { $_ -match '^(ai-service/(app|config|scripts|tests)/|server/src/|client/(src|tests)/|scripts/|contracts/|AI_Service_Kaggle\.ipynb$)' } |
        Sort-Object -Unique
    $fingerprints = [ordered]@{}
    foreach ($relativePath in $sourceFiles) {
        $sourcePath = Join-Path $repo $relativePath
        if (Test-Path -LiteralPath $sourcePath -PathType Leaf) {
            $fingerprints[$relativePath] = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
    @{capturedAtUtc=[DateTime]::UtcNow.ToString('o'); branch=(& git -C $repo branch --show-current);
      gitRevision=(& git -C $repo rev-parse HEAD); files=$fingerprints} |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output 'source-fingerprint.json') -Encoding UTF8
    if ($isRuntime) {
        if (!$runtimeToken -or !$KaggleUrl -or !$RuntimeEvidence) { throw 'Runtime requires KaggleUrl, RuntimeEvidence and AI_INTERNAL_SERVICE_TOKEN in the environment.' }
        $uri = [uri]$KaggleUrl
        if ($uri.Scheme -ne 'https' -or !$uri.Host.EndsWith('.trycloudflare.com') -or $uri.UserInfo -or $uri.Query) { throw 'Expected the supplied HTTPS Cloudflare origin without credentials/query.' }
        $runtime = Get-Content -LiteralPath $RuntimeEvidence -Raw -Encoding UTF8 | ConvertFrom-Json
        $notebook = Get-Content -LiteralPath (Join-Path $repo 'AI_Service_Kaggle.ipynb') -Raw -Encoding UTF8 | ConvertFrom-Json
        $corpus = Get-Content -LiteralPath (Join-Path $repo 'ai-service/tests/fixtures/runtime-corpus.json') -Raw -Encoding UTF8 | ConvertFrom-Json
        $expectedIds = @($corpus.cases.id | Sort-Object)
        $actualIds = @($runtime.results.id | Sort-Object)
        if ($runtime.backend -ne 'RUNTIME_HTTP' -or !$runtime.physicalArtifactsVerified -or
            $runtime.blocked -or $runtime.failed -ne 0 -or $runtime.passed -ne $expectedIds.Count -or
            @($runtime.results | Where-Object { !$_.passed -or $_.repeat -ne 1 }).Count -gt 0 -or
            (Compare-Object $expectedIds $actualIds) -or
            $runtime.sourceZipSha256 -ne $notebook.metadata.lab_portal_source.sha256) { throw 'Kaggle evidence is incomplete, failed, or belongs to another source bundle.' }
        Copy-Item -LiteralPath $RuntimeEvidence -Destination (Join-Path $output 'kaggle-runtime-evidence.json')
    }
    Copy-Item -LiteralPath (Join-Path $repo 'docs/testing/AI_SERVICE_REMEDIATION_CHECKLIST_20261104.csv') -Destination (Join-Path $output 'checklist-before.csv')
    Set-TestEnvironment 'AI_INTERNAL_SERVICE_TOKEN' 'hybrid-test-token'
    Set-TestEnvironment 'AI_RUNTIME_LOAD_ENABLED' 'false'
    Set-TestEnvironment 'LAB_PORTAL_LIVE_AI_IT' 'false'
    Set-TestEnvironment 'LAB_PORTAL_MYSQL_IT' 'false'
    Set-TestEnvironment 'LAB_PORTAL_LOCAL_AI_IT' 'false'
    Set-TestEnvironment 'LAB_PORTAL_AI_UI_IT' 'false'
    Set-TestEnvironment 'LAB_PORTAL_AI_SIMULATION' 'false'
    if ($Group -in @('Unit','All')) {
        Invoke-Stage 'python-unit' $pythonExe @('-m','pytest','tests','-q','--ignore=tests/test_kaggle_notebook.py',('--junitxml=' + (Join-Path $output 'python-unit.xml'))) (Join-Path $repo 'ai-service')
        Invoke-Stage 'java-unit' (Join-Path $repo 'server/mvnw.cmd') @('-Dtest=Ai*Test,SpringPythonContractTest,TimeSlotServiceImplTest','test') (Join-Path $repo 'server')
    }
    if ($Group -in @('Fixtures','Integration','UI','Runtime','RuntimeFixtures','All')) {
        Assert-FreePort 3307
        Assert-FreePort 8091
        Assert-FreePort 6380
        $dbPassword = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
        Set-TestEnvironment 'MYSQL_ROOT_PASSWORD' $dbPassword
        & docker run --detach --name $runId --label ('lab-portal-test-run=' + $runId) --publish '127.0.0.1:3307:3306' --env MYSQL_ROOT_PASSWORD --env MYSQL_DATABASE=lab_portal_it mysql:8.0 > (Join-Path $output 'mysql-container.txt')
        if ($LASTEXITCODE -ne 0) { throw 'Could not create disposable MySQL' }
        $dbCreated = $true
        & docker run --detach --name $redisName --label ('lab-portal-test-run=' + $runId) --publish '127.0.0.1:6380:6379' redis:7-alpine > (Join-Path $output 'redis-container.txt')
        if ($LASTEXITCODE -ne 0) { throw 'Could not create disposable Redis' }
        $redisCreated = $true
        Set-TestEnvironment 'SPRING_DATA_REDIS_HOST' '127.0.0.1'
        Set-TestEnvironment 'SPRING_DATA_REDIS_PORT' '6380'
        for ($attempt=0; $attempt -lt 45; $attempt++) {
            & docker exec $runId sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin ping -uroot --silent' *> $null
            if ($LASTEXITCODE -eq 0) { break }
            Start-Sleep -Seconds 2
        }
        if ($LASTEXITCODE -ne 0) { throw 'Disposable MySQL readiness failed' }
        Set-TestEnvironment 'LAB_PORTAL_MYSQL_IT' 'true'
        Set-TestEnvironment 'LAB_PORTAL_MYSQL_IT_URL' 'jdbc:mysql://127.0.0.1:3307/lab_portal_it?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia%2FHo_Chi_Minh'
        Set-TestEnvironment 'LAB_PORTAL_MYSQL_IT_USERNAME' 'root'
        Set-TestEnvironment 'LAB_PORTAL_MYSQL_IT_PASSWORD' $dbPassword
        Set-TestEnvironment 'LAB_PORTAL_LOCAL_AI_IT' 'true'
        if ($isRuntime) {
            Set-TestEnvironment 'LAB_PORTAL_LIVE_AI_IT' 'true'
            Set-TestEnvironment 'LAB_PORTAL_LIVE_AI_EVIDENCE_DIR' $output
            Set-TestEnvironment 'AI_GATEWAY_BASE_URL' $KaggleUrl
            Set-TestEnvironment 'AI_INTERNAL_SERVICE_TOKEN' $runtimeToken
            if ($Group -eq 'Runtime') {
                Assert-FreePort 4173
                Set-TestEnvironment 'LAB_PORTAL_AI_UI_IT' 'true'
                Set-TestEnvironment 'AI_TEST_OUTPUT_DIR' $output
            }
        }
        Set-TestEnvironment 'AI_TEST_SCENARIO_FILE' (Join-Path $output 'scenarios.json')
        [IO.File]::WriteAllText($env:AI_TEST_SCENARIO_FILE, '{}', (New-Object Text.UTF8Encoding($false)))
        Set-TestEnvironment 'PYTHONPATH' (Join-Path $repo 'ai-service')
        if (!$isRuntime) {
        $pythonProcess = Start-Owned $pythonExe @('-m','uvicorn','local_http_app:app','--app-dir',(Join-Path $repo 'ai-service/tests'),'--host','127.0.0.1','--port','8091','--log-level','warning') $repo 'python-http'
        $upstreamReady = $false
        for ($attempt=0; $attempt -lt 30; $attempt++) {
            if ($pythonProcess.HasExited) { throw 'Scripted Python process exited; see python-http.stderr.log' }
            try {
                $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8091/health' -Headers @{'X-Internal-Service-Token'='hybrid-test-token'} -TimeoutSec 2
                if ($health.status -eq 'UP') { $upstreamReady = $true; break }
            } catch { Start-Sleep -Seconds 1 }
        }
        if (!$upstreamReady) { throw 'Scripted Python health check failed' }
        }
        if ($Group -eq 'Runtime') {
            Invoke-Stage 'spring-kaggle' (Join-Path $repo 'server/mvnw.cmd') @('-Dtest=AiRemediationIntegrationTest#A07_*+A08_*+researchDates*+L07_*+A09_A10_*+A11_A12_*+S04_*+R12_*+G03_*+G06_*+G11_*+unsupportedWorkflow*+liveReportGeneration*+liveShiftFields*+liveC14_*+C18_*+browserFlows','test') (Join-Path $repo 'server')
        }
        if ($Group -eq 'RuntimeFixtures') {
            $runtimeSelection = if ($FixtureMethods -eq '*') {
                '-Dtest=AiRemediationIntegrationTest$FixtureExpansion#G04_G16_*,AiRemediationIntegrationTest#liveReportGeneration*+liveShiftFields*+liveC14_*+databaseGuardRejectsOtherHostsPortsAndSchemas'
            } else {
                '-Dtest=AiRemediationIntegrationTest$FixtureExpansion#' + $FixtureMethods + ',AiRemediationIntegrationTest#databaseGuardRejectsOtherHostsPortsAndSchemas'
            }
            Invoke-Stage 'spring-kaggle-fixtures' (Join-Path $repo 'server/mvnw.cmd') @($runtimeSelection,'test') (Join-Path $repo 'server')
        }
        if ($Group -eq 'Fixtures') {
            $fixtureSelection = '-Dtest=AiRemediationIntegrationTest$FixtureExpansion#' + $FixtureMethods + ',AiRemediationIntegrationTest#databaseGuardRejectsOtherHostsPortsAndSchemas'
            Invoke-Stage 'mysql-fixtures' (Join-Path $repo 'server/mvnw.cmd') @($fixtureSelection,'test') (Join-Path $repo 'server')
        }
        if ($Group -in @('Integration','All')) {
            Invoke-Stage 'mysql-http' (Join-Path $repo 'server/mvnw.cmd') @('-Dtest=AiRemediationIntegrationTest,AiRepositoryMySqlIntegrationTest,AiPersistenceCompatibilityMigrationMySqlIntegrationTest,AiContextFreshAuthorizationMySqlIntegrationTest','test') (Join-Path $repo 'server')
            Assert-FreePort 4501
            $simulation = Start-Owned (Get-Command postman).Source @('simulation','run','postman/simulations/ai-local-faults/ai-local-faults.sim.yaml','--port','4501','--no-history','--output','ndjson') $repo 'postman-simulation'
            for ($attempt=0; $attempt -lt 30; $attempt++) {
                if (Get-NetTCPConnection -State Listen -LocalPort 4501 -ErrorAction SilentlyContinue) { break }
                if ($simulation.HasExited) { throw 'Postman simulation exited' }
                Start-Sleep -Seconds 1
            }
            Set-TestEnvironment 'LAB_PORTAL_AI_SIMULATION' 'true'
            Invoke-Stage 'http-faults' (Join-Path $repo 'server/mvnw.cmd') @('-Dtest=AiGatewayPostmanSimulationTest','test') (Join-Path $repo 'server')
        }
        if ($Group -in @('UI','All')) {
            Assert-FreePort 4173
            Set-TestEnvironment 'LAB_PORTAL_AI_UI_IT' 'true'
            Set-TestEnvironment 'AI_TEST_OUTPUT_DIR' $output
            Invoke-Stage 'browser' (Join-Path $repo 'server/mvnw.cmd') @('-Dtest=AiRemediationIntegrationTest#browserFlows','test') (Join-Path $repo 'server')
        }
    }
} catch {
    $exitCode = 1
    Write-Host $_.Exception.Message
    $stages.Add(@{name='runner'; exitCode=1; reason=$_.Exception.Message})
} finally {
    foreach ($process in $ownedProcesses) {
        if (!$process.HasExited) { & taskkill /PID $process.Id /T /F *> $null }
    }
    if ($dbCreated) {
        $labelsJson = & docker inspect $runId --format '{{json .Config.Labels}}'
        if ($LASTEXITCODE -eq 0) {
            $labels = $labelsJson | ConvertFrom-Json
            if ($labels.'lab-portal-test-run' -eq $runId) { & docker rm --force --volumes $runId *> $null }
        }
    }
    if ($redisCreated) {
        $labelsJson = & docker inspect $redisName --format '{{json .Config.Labels}}'
        if ($LASTEXITCODE -eq 0 -and ($labelsJson | ConvertFrom-Json).'lab-portal-test-run' -eq $runId) {
            & docker rm --force --volumes $redisName *> $null
        }
    }
    foreach ($name in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process') }
    $backend = if ($isRuntime) { 'REAL_KAGGLE_MODEL' } else { 'SCRIPTED_TEST' }
    @{runId=$runId; group=$Group; backend=$backend; exitCode=$exitCode; stages=@($stages.ToArray());
      scope='Local code, HTTP, DB and browser evidence; model inference requires Kaggle runtime results'} |
        ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $output 'summary.json') -Encoding UTF8
    Write-Host ('Evidence: ' + $output)
}
exit $exitCode
