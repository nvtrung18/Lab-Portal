[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidateNotNullOrEmpty()] [string] $Region,
    [Parameter(Mandatory)] [ValidateNotNullOrEmpty()] [string] $VpcId,
    [Parameter(Mandatory)] [ValidateNotNullOrEmpty()] [string] $SubnetId,
    [Parameter(Mandatory)] [ValidateNotNullOrEmpty()] [string] $AmiId,
    [string] $ArtifactRoot = '',
    [string] $AdapterSearchRoot = '',
    [string] $StackName = 'lab-portal-ai-test',
    [ValidateSet('g4dn.xlarge', 'g4dn.2xlarge', 'g4dn.4xlarge')] [string] $InstanceType = 'g4dn.2xlarge',
    [ValidateRange(15, 120)] [int] $RemoteTimeoutMinutes = 45,
    [ValidatePattern('^[A-Za-z0-9._-]{1,128}$')] [string] $ImageTag = '',
    [string] $AwsProfile = '',
    [switch] $PreflightOnly
)

$ErrorActionPreference = 'Stop'
$awsCliDirectory = Join-Path $env:ProgramFiles 'Amazon\AWSCLIV2'
if (-not (Get-Command aws -ErrorAction SilentlyContinue) -and (Test-Path -LiteralPath (Join-Path $awsCliDirectory 'aws.exe'))) {
    $env:Path = "$awsCliDirectory;$env:Path"
}
$repoRoot = Split-Path -Parent $PSScriptRoot
$templatePath = Join-Path $repoRoot 'infra/aws/ai-service-test/template.yml'
$manifestPath = Join-Path $repoRoot 'ai-service/config/model-artifacts.runtime-t4.json'
$validatorPath = Join-Path $repoRoot 'ai-service/scripts/prepare_runtime_artifacts.py'
if (-not $AdapterSearchRoot) { $AdapterSearchRoot = Join-Path $repoRoot '.artifacts' }
$awsCommon = @('--region', $Region)
if ($AwsProfile) { $awsCommon += @('--profile', $AwsProfile) }

function Assert-Command([string] $Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command is not installed or not in PATH: $Name"
    }
}

function Invoke-Aws([string[]] $Arguments) {
    $output = & aws @awsCommon @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "AWS CLI failed: $($output -join [Environment]::NewLine)" }
    return ($output -join [Environment]::NewLine).Trim()
}

function Get-StackOutput([string] $Key) {
    return Invoke-Aws @(
        'cloudformation', 'describe-stacks', '--stack-name', $StackName,
        '--query', "Stacks[0].Outputs[?OutputKey=='$Key'].OutputValue | [0]", '--output', 'text'
    )
}

function Get-Sha256([string] $Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Find-ApprovedResearchAdapter([object] $Manifest, [string] $SearchRoot) {
    if (-not (Test-Path -LiteralPath $SearchRoot -PathType Container)) {
        throw "Adapter search root does not exist: $SearchRoot"
    }
    $adapter = $Manifest.assistantAdapters.RESEARCH_ASSISTANT
    if ($adapter.status -ne 'APPROVED' -or -not $adapter.artifact) {
        throw 'RESEARCH_ASSISTANT does not have an approved physical artifact in the runtime manifest.'
    }
    $descriptors = @($adapter.artifact.files)
    $anchor = $descriptors | Where-Object { [string]$_.path -match '/adapter_model\.safetensors$' } | Select-Object -First 1
    if (-not $anchor) { throw 'Approved Research adapter does not declare adapter_model.safetensors.' }

    $candidates = Get-ChildItem -LiteralPath $SearchRoot -Recurse -File -Filter 'adapter_model.safetensors' |
        Sort-Object FullName
    foreach ($candidate in $candidates) {
        if ((Get-Sha256 $candidate.FullName) -ne ([string]$anchor.sha256).ToLowerInvariant()) { continue }
        $allMatch = $true
        foreach ($descriptor in $descriptors) {
            $name = [System.IO.Path]::GetFileName(([string]$descriptor.path).Replace('/', [System.IO.Path]::DirectorySeparatorChar))
            $source = Join-Path $candidate.Directory.FullName $name
            if (-not (Test-Path -LiteralPath $source -PathType Leaf) -or
                (Get-Sha256 $source) -ne ([string]$descriptor.sha256).ToLowerInvariant()) {
                $allMatch = $false
                break
            }
        }
        if ($allMatch) { return $candidate.Directory.FullName }
    }
    throw "No complete approved Research adapter matching the runtime manifest was found under: $SearchRoot"
}

Assert-Command 'python'

$runtimeManifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$baseModel = $runtimeManifest.baseModel
$baseDescriptors = @($baseModel.artifact.files)
$basePaths = @($baseDescriptors | ForEach-Object { ([string]$_.path).Replace('\\', '/') })
$basePrefixes = @($basePaths | ForEach-Object { $_.Substring(0, $_.LastIndexOf('/')) } | Select-Object -Unique)
$baseFiles = @($basePaths | ForEach-Object { $_.Substring($_.LastIndexOf('/') + 1) })
if ($baseModel.status -ne 'APPROVED' -or -not $baseModel.artifact -or
    $baseDescriptors.Count -eq 0 -or
    $baseModel.identifier -notmatch '^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$' -or
    $baseModel.revision -notmatch '^[0-9a-f]{40}$' -or
    $basePrefixes.Count -ne 1 -or
    $basePrefixes[0] -match '(^|/)\.\.(/|$)' -or
    ($baseFiles | Where-Object { $_ -notmatch '^[A-Za-z0-9._-]+$' })) {
    throw 'Runtime base-model descriptor contains an unsupported status, identifier, revision, or path.'
}
$prepareArtifactsOnAws = [string]::IsNullOrWhiteSpace($ArtifactRoot)
if ($prepareArtifactsOnAws) {
    $adapterSource = Find-ApprovedResearchAdapter $runtimeManifest $AdapterSearchRoot
    Write-Host "Using approved Research adapter: $adapterSource"
}
else {
    if (-not (Test-Path -LiteralPath $ArtifactRoot -PathType Container)) {
        throw "ArtifactRoot does not exist: $ArtifactRoot"
    }
    & python $validatorPath --manifest $manifestPath verify --artifact-root $ArtifactRoot
    if ($LASTEXITCODE -ne 0) { throw 'Runtime artifact verification failed.' }
}

if (-not $ImageTag) {
    $ImageTag = (& git -C $repoRoot rev-parse --short=12 HEAD).Trim()
}
if ($ImageTag -notmatch '^[A-Za-z0-9._-]{1,128}$') { throw 'ImageTag contains unsupported characters.' }

if ($PreflightOnly) {
    $mode = if ($prepareArtifactsOnAws) { 'AWS preparation' } else { 'local artifact root' }
    Write-Host "Preflight passed: mode=$mode baseFiles=$($baseDescriptors.Count) imageTag=$ImageTag"
    return
}

Assert-Command 'aws'
Assert-Command 'docker'

$localImage = "lab-portal-ai-service:$ImageTag"
& docker build --pull --file (Join-Path $repoRoot 'docker/ai-service/Dockerfile') --tag $localImage $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'AI service image build failed.' }

$accountId = Invoke-Aws @('sts', 'get-caller-identity', '--query', 'Account', '--output', 'text')

Invoke-Aws @(
    'cloudformation', 'deploy', '--stack-name', $StackName, '--template-file', $templatePath,
    '--capabilities', 'CAPABILITY_IAM', '--no-fail-on-empty-changeset', '--parameter-overrides',
    "VpcId=$VpcId", "SubnetId=$SubnetId", "AmiId=$AmiId", "InstanceType=$InstanceType"
) | Write-Host

$bucket = Get-StackOutput 'ArtifactBucketName'
$repositoryUri = Get-StackOutput 'RepositoryUri'
$instanceId = Get-StackOutput 'InstanceId'
$secretArn = Get-StackOutput 'InternalServiceTokenArn'
$logGroup = Get-StackOutput 'LogGroupName'
$artifactPrefix = "runtime-artifacts/$ImageTag"

if ($prepareArtifactsOnAws) {
    foreach ($descriptor in @($runtimeManifest.assistantAdapters.RESEARCH_ASSISTANT.artifact.files)) {
        $relative = ([string]$descriptor.path).Replace('\\', '/')
        if ($relative -notmatch '^research-assistant/1\.0\.0/[A-Za-z0-9._-]+$') {
            throw "Unsupported Research adapter path in runtime manifest: $relative"
        }
        $source = Join-Path $adapterSource ([System.IO.Path]::GetFileName($relative))
        Invoke-Aws @('s3', 'cp', $source, "s3://$bucket/$artifactPrefix/$relative", '--only-show-errors') | Write-Host
    }
    Invoke-Aws @('s3', 'cp', $manifestPath, "s3://$bucket/$artifactPrefix/runtime-manifest.json", '--only-show-errors') | Write-Host
}
else {
    Invoke-Aws @('s3', 'sync', $ArtifactRoot, "s3://$bucket/$artifactPrefix/", '--delete', '--only-show-errors') | Write-Host
}

$registry = "$accountId.dkr.ecr.$Region.amazonaws.com"
$password = Invoke-Aws @('ecr', 'get-login-password')
$password | & docker login --username AWS --password-stdin $registry
if ($LASTEXITCODE -ne 0) { throw 'Docker login to ECR failed.' }

$image = "${repositoryUri}:$ImageTag"
& docker tag $localImage $image
if ($LASTEXITCODE -ne 0) { throw 'AI service image tagging failed.' }
& docker push $image
if ($LASTEXITCODE -ne 0) { throw 'AI service image push failed.' }

$deadline = (Get-Date).AddMinutes(10)
do {
    $ping = Invoke-Aws @(
        'ssm', 'describe-instance-information', '--filters', "Key=InstanceIds,Values=$instanceId",
        '--query', 'InstanceInformationList[0].PingStatus', '--output', 'text'
    )
    if ($ping -eq 'Online') { break }
    Start-Sleep -Seconds 10
} while ((Get-Date) -lt $deadline)
if ($ping -ne 'Online') { throw "EC2 instance did not become available in SSM: $instanceId" }

$remoteArtifactRoot = "/opt/lab-portal-ai/artifacts/$ImageTag"
$artifactCommands = @(
    "sudo mkdir -p $remoteArtifactRoot",
    "sudo aws s3 sync s3://$bucket/$artifactPrefix/ $remoteArtifactRoot/ --delete --only-show-errors"
)
if ($prepareArtifactsOnAws) {
    $baseFileArguments = $baseFiles -join ' '
    $artifactCommands += @(
        "sudo mkdir -p $remoteArtifactRoot/$($basePrefixes[0])",
        ("sudo docker run --rm --user 0 --mount type=bind,src=$remoteArtifactRoot,dst=/opt/ai-artifacts " +
            "$image hf download $($baseModel.identifier) $baseFileArguments --revision $($baseModel.revision) " +
            "--local-dir /opt/ai-artifacts/$($basePrefixes[0])"),
        "sudo rm -rf $remoteArtifactRoot/$($basePrefixes[0])/.cache",
        ("sudo docker run --rm --user 10001 --mount type=bind,src=$remoteArtifactRoot,dst=/opt/ai-artifacts,readonly " +
            "$image python /app/scripts/prepare_runtime_artifacts.py --manifest /app/config/model-artifacts.runtime-t4.json " +
            'verify --artifact-root /opt/ai-artifacts'),
        "sudo aws s3 sync $remoteArtifactRoot/ s3://$bucket/$artifactPrefix/ --delete --only-show-errors"
    )
}

$commands = @(
    'set -euo pipefail',
    'command -v nvidia-smi >/dev/null && nvidia-smi',
    'command -v docker >/dev/null',
    'command -v nvidia-container-cli >/dev/null',
    "aws ecr get-login-password --region $Region | sudo docker login --username AWS --password-stdin $registry",
    "sudo docker pull $image"
)
$commands += $artifactCommands
$commands += @(
    'TOKEN=$(aws secretsmanager get-secret-value --secret-id ''' + $secretArn + ''' --region ' + $Region + ' --query SecretString --output text)',
    'sudo docker rm -f lab-portal-ai-service 2>/dev/null || true',
    ('sudo docker run -d --name lab-portal-ai-service --restart unless-stopped --gpus all ' +
        '--publish 127.0.0.1:8090:8090 ' +
        '--mount type=bind,src=' + $remoteArtifactRoot + ',dst=/opt/ai-artifacts,readonly ' +
        '--env AI_INTERNAL_SERVICE_TOKEN="$TOKEN" ' +
        '--env AI_MODEL_ARTIFACT_ROOT=/opt/ai-artifacts ' +
        '--env AI_MODEL_ARTIFACTS_PATH=/app/config/model-artifacts.runtime-t4.json ' +
        '--env AI_RUNTIME_LOAD_ENABLED=true --env AI_RUNTIME_DEVICE=cuda:0 ' +
        '--env AI_REQUEST_TIMEOUT_SECONDS=170 ' +
        '--env HF_HUB_OFFLINE=1 --env TRANSFORMERS_OFFLINE=1 --env HF_DATASETS_OFFLINE=1 ' +
        '--log-driver awslogs --log-opt awslogs-region=' + $Region + ' --log-opt awslogs-group=' + $logGroup + ' ' +
        '--log-opt awslogs-stream=' + $instanceId + ' ' + $image),
    'for attempt in $(seq 1 60); do STATUS=$(curl -sS -o /tmp/ready.json -w "%{http_code}" -H "X-Internal-Service-Token: $TOKEN" http://127.0.0.1:8090/ready || true); [ "$STATUS" = "200" ] && cat /tmp/ready.json && exit 0; sleep 10; done',
    'sudo docker logs --tail 200 lab-portal-ai-service',
    'exit 1'
)
$parametersFile = Join-Path ([System.IO.Path]::GetTempPath()) "lab-portal-ai-ssm-$([guid]::NewGuid().ToString('N')).json"
try {
    $parametersJson = @{ commands = $commands } | ConvertTo-Json -Depth 4
    [System.IO.File]::WriteAllText(
        $parametersFile,
        $parametersJson,
        [System.Text.UTF8Encoding]::new($false)
    )
    $commandId = Invoke-Aws @(
        'ssm', 'send-command', '--instance-ids', $instanceId, '--document-name', 'AWS-RunShellScript',
        '--comment', "Deploy Lab Portal AI $ImageTag", '--timeout-seconds', ($RemoteTimeoutMinutes * 60),
        '--parameters', "file://$parametersFile",
        '--query', 'Command.CommandId', '--output', 'text'
    )
    $remoteDeadline = (Get-Date).AddMinutes($RemoteTimeoutMinutes)
    do {
        Start-Sleep -Seconds 10
        $invocationJson = Invoke-Aws @('ssm', 'get-command-invocation', '--command-id', $commandId, '--instance-id', $instanceId, '--output', 'json')
        $invocation = $invocationJson | ConvertFrom-Json
    } while ($invocation.Status -in @('Pending', 'InProgress', 'Delayed') -and (Get-Date) -lt $remoteDeadline)
    if ($invocation.Status -in @('Pending', 'InProgress', 'Delayed')) {
        throw "Remote deployment exceeded $RemoteTimeoutMinutes minutes. CommandId=$commandId"
    }
    if ($invocation.StandardOutputContent) { $invocation.StandardOutputContent | Write-Host }
    if ($invocation.StandardErrorContent) { $invocation.StandardErrorContent | Write-Warning }
    if ($invocation.Status -ne 'Success') { throw "Remote deployment failed with status $($invocation.Status)." }
}
finally {
    Remove-Item -LiteralPath $parametersFile -Force -ErrorAction SilentlyContinue
}

Write-Host "AI service is ready on instance $instanceId with image $image"
Write-Host "Open a private tunnel: .\scripts\start-ai-service-aws-tunnel.ps1 -Region $Region -StackName $StackName"
