# AI Service AWS acceptance environment

This runbook deploys only the Python AI service to one private GPU-backed EC2 instance. Spring Boot, MySQL, Redis, and the frontends may remain local and reach port 8090 through AWS Systems Manager port forwarding. No inbound security-group rule exposes the AI API.

## Architecture

```text
Local browser -> local Spring Boot -> localhost:8090
                                      |
                                      +-- SSM port-forwarding session
                                          -> EC2 loopback:8090
                                             -> AI container + NVIDIA T4

Private S3 bucket -> immutable runtime artifacts -> encrypted EC2 volume
Private ECR       -> pinned AI container image
Secrets Manager   -> X-Internal-Service-Token
Container stdout  -> CloudWatch Logs (7-day retention)
```

This is an acceptance-test environment, not a production high-availability design. The EC2 instance is a single point of failure and has no public AI listener.

## Prerequisites

Install locally:

- AWS CLI v2, authenticated to the target account;
- AWS Session Manager plugin;
- Docker Desktop;
- Python 3.12;
- permissions for CloudFormation, EC2, IAM, S3, ECR, Secrets Manager, CloudWatch Logs and SSM.

In the selected AWS Region:

1. Request enough `Running On-Demand G and VT instances` quota for the selected instance.
2. Select an existing VPC and a subnet with outbound internet through a public IP, NAT gateway, or the required VPC endpoints.
3. Select a GPU-ready Ubuntu AMI with NVIDIA driver, Docker, NVIDIA Container Toolkit, AWS CLI and SSM Agent. Record its Region-specific AMI ID. Do not assume an AMI ID from another Region.
4. Confirm the model license permits the intended test and storage in the selected Region.

Recommended first test size: `g4dn.2xlarge`. The template also allows `g4dn.xlarge` and `g4dn.4xlarge` because all use one NVIDIA T4; `g4dn.2xlarge` provides more host memory during model loading.

## Runtime artifact preparation

The deployment never downloads model files while serving. By default, the deployment script finds the approved Research adapter under `.artifacts`, verifies every adapter checksum, uploads only those files, and downloads the pinned base model on EC2. EC2 then verifies all 19 manifest files, stores the complete bundle in private S3, enables offline mode, and starts serving.

Check this mode locally without AWS calls, image builds, or model downloads:

```powershell
.\scripts\deploy-ai-service-aws-test.ps1 `
  -Region ap-southeast-1 `
  -VpcId vpc-preflight `
  -SubnetId subnet-preflight `
  -AmiId ami-preflight `
  -PreflightOnly
```

Use `-AdapterSearchRoot` only when the approved adapter is stored outside the repository's ignored `.artifacts` directory.

### Optional local artifact mode

If a sufficiently large local artifact root already exists, it may still be supplied with `-ArtifactRoot`. Its directory must mirror the paths in `ai-service/config/model-artifacts.runtime-t4.json`:

```text
<source-root>/
  shared-base/cdbee75f17c01a7cc42f958dc650907174af0554/...
  research-assistant/1.0.0/...
```

The approved Research `adapter_model.safetensors` currently has SHA-256:

```text
0565e19232204afc409e3384bb430e55c51daefa61f1a07bbbe9eb9320ba5b18
```

Do not substitute a training checkpoint or similarly named file. Assemble into a new empty destination:

```powershell
cd D:\CV\Lab-Portal\ai-service
.\.venv\Scripts\python.exe scripts\prepare_runtime_artifacts.py `
  --manifest config\model-artifacts.runtime-t4.json `
  assemble `
  --source-root D:\secure\lab-portal-ai-source `
  --output-root D:\secure\lab-portal-ai-runtime
```

The command copies only manifest-listed files, validates each SHA-256 and does not leave a partial output after a failure. Re-verify at any time:

```powershell
.\.venv\Scripts\python.exe scripts\prepare_runtime_artifacts.py `
  --manifest config\model-artifacts.runtime-t4.json `
  verify `
  --artifact-root D:\secure\lab-portal-ai-runtime
```

The assembled artifact directory is ignored by Git and must never be copied into the Docker image.

## Local preflight and dependency status

The production image installs API/runtime dependencies only; `pytest` remains a development dependency. Build it locally before touching AWS:

```powershell
docker build --file docker/ai-service/Dockerfile --tag lab-portal-ai-service:aws-test-preflight .
```

The current image uses FastAPI `0.141.1`, Starlette `1.6.0`, pip `26.2`, PyTorch `2.7.1+cu118`, Transformers `4.51.3`, and PEFT `0.15.2`. The latest local `pip-audit` found no issue in FastAPI, Starlette, pip, or the production API dependencies, but still reported known findings against Transformers `4.51.3`; the CUDA-specific PyTorch wheel was not present in pip-audit's PyPI advisory source.

Transformers is deliberately not upgraded across the model-runtime compatibility boundary without a real GPU regression run. For this private acceptance environment, reduce exposure by accepting only the pinned, checksum-verified safetensors bundle, keeping Hugging Face/Transformers offline, running as the non-root container user, binding the service to EC2 loopback, and allowing access only through SSM. Do not expose this pinned image publicly or promote it to production. Re-run dependency audit and model-backed tests before any wider deployment.

## Deploy

Without `-ArtifactRoot`, the deployment script validates the local approved adapter and runtime manifest before creating or updating AWS resources. It then:

1. builds the commit-tagged image locally;
2. deploys the CloudFormation stack;
3. uploads the checksum-verified Research adapter to a versioned private S3 prefix;
4. pushes the image to private ECR and waits for the EC2 instance to become available in SSM;
5. downloads the exact base-model files and revision declared by the runtime manifest on EC2;
6. verifies the complete bundle, uploads it to S3, and starts one offline GPU container;
7. waits up to ten minutes for authenticated `/ready` to return HTTP 200.

Run from the repository root:

```powershell
.\scripts\deploy-ai-service-aws-test.ps1 `
  -Region ap-southeast-1 `
  -VpcId vpc-REPLACE_ME `
  -SubnetId subnet-REPLACE_ME `
  -AmiId ami-REPLACE_ME
```

Optional parameters include `-AwsProfile`, `-StackName`, `-InstanceType`, `-RemoteTimeoutMinutes`, `-ArtifactRoot`, and an immutable `-ImageTag`. The default image tag is the current Git commit SHA. Reusing an existing ECR tag intentionally fails because the repository is immutable.

The CloudFormation stack creates:

- one encrypted EC2 GPU instance with IMDSv2 required;
- a security group with no inbound rules;
- a private, encrypted and versioned S3 bucket;
- a private, scan-on-push and immutable ECR repository;
- a generated Secrets Manager internal token;
- a seven-day CloudWatch log group;
- a least-privilege instance role plus SSM managed-instance access.

The S3 bucket and ECR repository are retained when the stack is deleted to avoid accidental loss. Remove retained data explicitly after the test when it is no longer needed.

## Connect local Spring Boot

Start the SSM tunnel and leave its terminal open:

```powershell
.\scripts\start-ai-service-aws-tunnel.ps1 -Region ap-southeast-1
```

Resolve the secret ARN and load the value into the current PowerShell process without printing it:

```powershell
$region = 'ap-southeast-1'
$stack = 'lab-portal-ai-test'
$secretArn = aws --region $region cloudformation describe-stacks `
  --stack-name $stack `
  --query "Stacks[0].Outputs[?OutputKey=='InternalServiceTokenArn'].OutputValue | [0]" `
  --output text
$env:AI_INTERNAL_SERVICE_TOKEN = aws --region $region secretsmanager get-secret-value `
  --secret-id $secretArn --query SecretString --output text
$env:AI_GATEWAY_BASE_URL = 'http://localhost:8090'
$env:AI_GATEWAY_ATTEMPT_TIMEOUT = '180s'
```

Restart Spring Boot after setting those variables. Do not place the token in `RUN_LOCAL_SERVICES.txt`, Git, screenshots, shell history or logs.

With the tunnel still open, run the contract smoke test from another terminal:

```powershell
.\scripts\test-ai-service-aws.ps1 -Region ap-southeast-1
```

This requires `/health` and every readiness flag to pass, then submits the repository's non-sensitive Research contract fixture and prints the real model answer and token counts.

## Acceptance gates

All gates must pass before calling the AI service operational:

1. Deployment output shows `nvidia-smi` and one assigned NVIDIA T4.
2. Container starts without CPU or disk offload.
3. `/health` returns HTTP 200.
4. `/ready` returns HTTP 200 with `artifactValidated`, `modelLoaded`, `adapterLoaded`, and `ready` all `true`.
5. Direct Python chat returns HTTP 200 with a bounded answer and token counts.
6. Authenticated Spring chat succeeds for Admin, Lab Manager and Student paths.
7. RAG citations remain inside the actor's authorized resource scope.
8. A cross-role or cross-resource request is denied by Spring before model generation.
9. CloudWatch contains operational logs without JWTs, internal tokens or full authorized context.
10. Record startup duration, prompt/completion tokens, p50/p95 latency, GPU memory and any safe refusals.

`/health = 200` alone is not acceptance evidence. A missing, corrupt or mismatched artifact must keep `/ready` at HTTP 503.

## Stop, rollback and cleanup

Stop GPU compute as soon as testing ends:

```powershell
.\scripts\stop-ai-service-aws-test.ps1 -Region ap-southeast-1
```

Use `-WhatIf` to preview the stop operation. Stopping removes EC2 compute charges but EBS, S3, ECR, Secrets Manager and CloudWatch storage can still incur charges.

Application rollback is a redeploy with a previous immutable image tag and its matching artifact prefix. Never combine an old image with a new manifest or artifact bundle.

Before deleting the stack, export any required test evidence, remove ECR images and empty all S3 object versions. CloudFormation intentionally retains the artifact bucket and ECR repository; locate them by the stack outputs and delete them separately only after explicit confirmation.

## Known blockers in the current workspace

- `ai-service/artifacts/` does not contain the approved shared base model; this no longer blocks the default AWS-preparation mode.
- AWS CLI `2.36.39` and Session Manager plugin `1.2.835.0` are installed. CloudFormation lint tooling is not installed natively; the template has passed `cfn-lint` in an ephemeral container, but AWS-side validation has not run.
- AWS credentials and a default Region are not configured on the current workstation.
- No AWS account, Region, VPC, subnet or GPU-ready AMI has yet been selected.
- Transformers `4.51.3` retains known audit findings; a compatible upgrade requires a real Qwen/PEFT GPU regression run.
- A live AWS deployment and model-backed end-to-end test have not yet been performed.
