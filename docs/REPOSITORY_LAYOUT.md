# Folder policy for main and dev

Both branches keep the same application layout. Retain source, tests, migrations, contracts, dependency declarations and lockfiles, runtime configuration, CI, deployment tools and current operational documentation.

| Folder | Purpose |
|---|---|
| `.github/` | Frontend/backend CI and Windows production deployment |
| `client/` | Frontend, integrated website, public assets and build configuration |
| `server/` | Backend source, tests, migrations and Maven build |
| `ai-service/` | AI API, tests, runtime configuration, requirements, artifact tooling and approval provenance |
| `face-service/` | Face API, tests, requirements and model download tooling |
| `contracts/` | Shared website and AI contracts consumed by source/build/tests |
| `docker/` | Dockerfiles, Caddy and container configuration |
| `scripts/` | Deployment, backup, demo import and AI infrastructure operations |
| `docs/` | Deployment runbooks, repository policy and website content templates |
| `infra/` | AWS template consumed by the retained AI deployment helper |

The retained `AI_Service_Kaggle.ipynb` starts the AI API on Kaggle. It is an operational notebook. Its presence does not establish which platform is currently hosting production AI.

## Local files

Keep installed dependencies, build outputs, caches, IDE settings, private environment files, uploaded documents, generated storage, model weights, transfer bundles and backups outside Git. Provision AI model payloads separately using the runtime manifest and its checksums. Production Compose configures named volumes for backend storage and uploads.

Track `client/package-lock.json` so `npm ci` remains reproducible. Track environment examples and the reviewed frontend development/production mode files. Do not add a broad ignore rule for all lockfiles, JSON, YAML, Markdown or notebooks.

## Research snapshot and approval provenance

The complete pre-cleanup snapshot is commit `257b01ab9eb4e8789a1be88c28e18d7586baf66d`. It contains the root `config/`, `datasets/`, `evals/`, `evidence/`, `requirements/`, research scripts/tests and historical documentation with their original layout. These form a separate research pipeline; they are excluded from the current main/dev application snapshot together.

Inspect an original file without changing the checkout:

```powershell
git show 257b01ab9eb4e8789a1be88c28e18d7586baf66d:config/p7-t5-research-promotion/model-registry.json
```

For the complete research pipeline, use a separate checkout of that commit. If the clone has shallow history, obtain that historical commit before accessing it. Retain the original layout when running its research scripts and tests.

Approval metadata still referenced by the AI descriptors is retained byte for byte under `ai-service/config/provenance/`. Runtime descriptors point to those new repository paths. See its README for the interpretation of historical references and identities.

## Updating the two branches

Prepare and review the cleanup from the latest main. After the change is approved and committed to main, bring that same commit into dev through the normal merge workflow. Keep the ignore policy and source layout consistent so later dev-to-main changes do not reintroduce archived folders. Pushing main triggers the existing production workflow.
