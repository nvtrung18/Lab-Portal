[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)] [ValidateNotNullOrEmpty()] [string] $Region,
    [string] $StackName = 'lab-portal-ai-test',
    [string] $AwsProfile = ''
)

$ErrorActionPreference = 'Stop'
$awsCliDirectory = Join-Path $env:ProgramFiles 'Amazon\AWSCLIV2'
if (-not (Get-Command aws -ErrorAction SilentlyContinue) -and (Test-Path -LiteralPath (Join-Path $awsCliDirectory 'aws.exe'))) {
    $env:Path = "$awsCliDirectory;$env:Path"
}
if (-not (Get-Command aws -ErrorAction SilentlyContinue)) { throw 'AWS CLI is required.' }
$common = @('--region', $Region)
if ($AwsProfile) { $common += @('--profile', $AwsProfile) }
$instanceId = (& aws @common cloudformation describe-stacks --stack-name $StackName --query "Stacks[0].Outputs[?OutputKey=='InstanceId'].OutputValue | [0]" --output text).Trim()
if ($LASTEXITCODE -ne 0 -or -not $instanceId -or $instanceId -eq 'None') { throw 'Cannot resolve the AI EC2 instance from CloudFormation.' }
if ($PSCmdlet.ShouldProcess($instanceId, 'Stop EC2 instance to stop GPU compute charges')) {
    & aws @common ec2 stop-instances --instance-ids $instanceId
    if ($LASTEXITCODE -ne 0) { throw 'Failed to stop the AI EC2 instance.' }
}
