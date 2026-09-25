[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidateNotNullOrEmpty()] [string] $Region,
    [string] $StackName = 'lab-portal-ai-test',
    [ValidateRange(1024, 65535)] [int] $LocalPort = 8090,
    [string] $AwsProfile = ''
)

$ErrorActionPreference = 'Stop'
$awsCliDirectory = Join-Path $env:ProgramFiles 'Amazon\AWSCLIV2'
$sessionManagerDirectory = Join-Path $env:ProgramFiles 'Amazon\SessionManagerPlugin\bin'
foreach ($directory in @($awsCliDirectory, $sessionManagerDirectory)) {
    if ((Test-Path -LiteralPath $directory) -and $env:Path -notlike "*$directory*") {
        $env:Path = "$directory;$env:Path"
    }
}
if (-not (Get-Command aws -ErrorAction SilentlyContinue)) { throw 'AWS CLI is required.' }
if (-not (Get-Command session-manager-plugin -ErrorAction SilentlyContinue)) { throw 'AWS Session Manager plugin is required.' }
$common = @('--region', $Region)
if ($AwsProfile) { $common += @('--profile', $AwsProfile) }
$instanceId = (& aws @common cloudformation describe-stacks --stack-name $StackName --query "Stacks[0].Outputs[?OutputKey=='InstanceId'].OutputValue | [0]" --output text).Trim()
if ($LASTEXITCODE -ne 0 -or -not $instanceId -or $instanceId -eq 'None') { throw 'Cannot resolve the AI EC2 instance from CloudFormation.' }

& aws @common ssm start-session --target $instanceId --document-name AWS-StartPortForwardingSession --parameters "portNumber=8090,localPortNumber=$LocalPort"
