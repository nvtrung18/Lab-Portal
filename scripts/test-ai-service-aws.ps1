[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidateNotNullOrEmpty()] [string] $Region,
    [string] $StackName = 'lab-portal-ai-test',
    [ValidateRange(1024, 65535)] [int] $LocalPort = 8090,
    [string] $AwsProfile = ''
)

$ErrorActionPreference = 'Stop'
$awsCliDirectory = Join-Path $env:ProgramFiles 'Amazon\AWSCLIV2'
if (-not (Get-Command aws -ErrorAction SilentlyContinue) -and (Test-Path -LiteralPath (Join-Path $awsCliDirectory 'aws.exe'))) {
    $env:Path = "$awsCliDirectory;$env:Path"
}
$repoRoot = Split-Path -Parent $PSScriptRoot
$contractPath = Join-Path $repoRoot 'contracts/ai-runtime-contract.json'
$common = @('--region', $Region)
if ($AwsProfile) { $common += @('--profile', $AwsProfile) }
if (-not (Get-Command aws -ErrorAction SilentlyContinue)) { throw 'AWS CLI is required.' }

$secretArn = (& aws @common cloudformation describe-stacks --stack-name $StackName --query "Stacks[0].Outputs[?OutputKey=='InternalServiceTokenArn'].OutputValue | [0]" --output text).Trim()
if ($LASTEXITCODE -ne 0 -or -not $secretArn -or $secretArn -eq 'None') { throw 'Cannot resolve the internal token secret from CloudFormation.' }
$token = (& aws @common secretsmanager get-secret-value --secret-id $secretArn --query SecretString --output text).Trim()
if ($LASTEXITCODE -ne 0 -or -not $token) { throw 'Cannot retrieve the internal service token.' }

$baseUri = "http://127.0.0.1:$LocalPort"
$headers = @{
    'X-Internal-Service-Token' = $token
    'X-Request-Id' = "aws-acceptance-$([guid]::NewGuid().ToString('N'))"
}
$health = Invoke-RestMethod -Uri "$baseUri/health" -Headers $headers -TimeoutSec 10
if ($health.status -ne 'UP') { throw "Unexpected health status: $($health.status)" }
$ready = Invoke-RestMethod -Uri "$baseUri/ready" -Headers $headers -TimeoutSec 10
foreach ($field in @('profileLoaded', 'artifactValidated', 'modelLoaded', 'adapterLoaded', 'ready')) {
    if ($ready.$field -ne $true) { throw "Readiness field is not true: $field" }
}

$contract = Get-Content -Raw -Encoding utf8 -LiteralPath $contractPath | ConvertFrom-Json
$body = $contract.researchMvpRequest | ConvertTo-Json -Depth 20
$chat = Invoke-RestMethod -Method Post -Uri "$baseUri$($contract.routes.chat.path)" -Headers $headers -ContentType 'application/json' -Body $body -TimeoutSec 180
if ($chat.assistantKey -ne 'RESEARCH_ASSISTANT' -or -not $chat.answer) { throw 'Chat response does not match the runtime contract.' }
if ($chat.promptTokens -lt 1 -or $chat.completionTokens -lt 1) { throw 'Chat response token counts are invalid.' }

[pscustomobject]@{
    Health = $health.status
    Ready = $ready.ready
    Assistant = $chat.assistantKey
    PromptTokens = $chat.promptTokens
    CompletionTokens = $chat.completionTokens
    Answer = $chat.answer
} | Format-List
