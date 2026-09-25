# GitHub Actions deployment to the Windows production PC

## Pipeline behavior

- Pull requests targeting main run frontend lint/build and backend Maven verification on GitHub-hosted runners.
- A push to main runs the same checks, then deploys only when the production Windows runner is online and healthy.
- workflow_dispatch can deploy manually from main after the runner is ready.
- The deployment job does not listen to pull-request events and is isolated in production.yml; do not add the production runner to the CI workflow.

## Prepare the production PC

1. Register a repository-level Windows x64 self-hosted runner from **Settings → Actions → Runners → New self-hosted runner**. Use the custom label lab-portal-production.
2. Install the runner under a separate directory such as D:\actions-runner, not inside the app checkout or .env.production directory. Configure it for this repository only.
3. The Windows account that runs the Actions runner must be able to call Docker Desktop and access D:\Lab-Portal\.env.production.
4. Keep .env.production and runtime-artifacts at D:\Lab-Portal; the workflow checks out main into the runner's own workspace and passes the env-file path explicitly.
5. Replace every template value in .env.production. Keep BOOTSTRAP_ADMIN_USERNAME set; configure its email and a unique password of at least 16 characters for the first startup. After the admin account is created, the email/password values can be cleared.
6. Make sure DNS, router forwarding, SMTP settings, and the model-artifact directory are ready before the first deployment. The Compose health checks gate backend startup on MySQL, Redis, AI, and Face.

## Activate and operate

After the workflow files reach main, wait until the runner appears **Idle** in GitHub. The next push to main will deploy automatically after CI passes. For a first deployment or retry, open **Actions → Production CI/CD → Run workflow** and select main.

The deployment script validates that the env file has no template values, builds the Compose images, starts the stack without deleting named volumes, waits up to 30 minutes for services to report healthy/running, then prints Compose status. A failed health check fails the workflow; inspect container logs before retrying.

Protect main with required CI status checks and review requirements in repository settings. GitHub recommends keeping self-hosted runners away from untrusted pull-request code; the PR CI workflow uses GitHub-hosted runners only.
