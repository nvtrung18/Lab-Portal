# Windows Docker production deployment

This runbook deploys the Lab Portal on one Windows PC with Docker Desktop and
WSL2. It is intentionally separate from the development `docker-compose.yml`.

## Production topology

```text
Internet :80/:443
        -> Caddy
           -> frontend static files
           -> Spring Boot /api
                 -> MySQL, Redis, Face service, AI service
```

Only Caddy publishes host ports. MySQL, Redis, Spring Boot, Face and AI stay
on the private Compose network.

## Before deployment

1. Install Docker Desktop with the WSL2 engine and verify:

   ```powershell
   docker version
   docker compose version
   ```

2. Assign the PC a stable LAN address and forward TCP ports 80 and 443 from
   the router to this PC. Do not forward 3306, 6379, 8080, 8090 or 8091.
3. Create DNS records for `APP_DOMAIN` and `API_DOMAIN` that resolve to the
   public IP, or use a supported tunnel when the ISP uses CGNAT.
4. Complete the production database seed-safety review before opening the
   application to the Internet.

## Configure secrets

From the repository root:

```powershell
Copy-Item .env.production.example .env.production
notepad .env.production
```

Replace all placeholders. The file is ignored by Git. Use separate, random
values for the database, JWT, internal-service, face and encryption secrets.
Do not put secrets in `VITE_*` variables.

The `AI_MODEL_ARTIFACT_ROOT_HOST` directory must contain the approved runtime
artifact tree referenced by `config/model-artifacts.runtime-t4.json`. The AI
service will remain unhealthy when the manifest or checksums are incomplete.
Set `AI_RUNTIME_DEVICE` to the device actually supported by the PC; `cpu` is a
configuration choice, not a performance guarantee.

## Validate and deploy

```powershell
docker compose --env-file .env.production -f docker-compose.prod.yml config --quiet
.\scripts\deploy-production.ps1 -Build
```

The deploy script does not run `down`, so a normal update keeps existing
volumes. Inspect the result with:

```powershell
docker compose --env-file .env.production -f docker-compose.prod.yml ps
docker compose --env-file .env.production -f docker-compose.prod.yml logs -f backend
```

Expected external checks:

```powershell
Invoke-WebRequest https://app.example.com
Invoke-RestMethod https://api.example.com/api/actuator/health/readiness
```

Replace the example domains with the values in `.env.production`.

## Backup

Run the database backup daily and copy the output outside the Docker volume:

```powershell
.\scripts\backup-production.ps1
```

Back up the generated SQL files, the backend upload/storage volumes, and the
secret material through a protected mechanism. Test restoring a backup into a
separate MySQL instance before relying on it.

## Rollback

Keep the previous Git revision and image tags available. If a deployment is
unhealthy, restore the previous revision, rebuild, and run the same deploy
script. Do not roll back a database migration blindly; verify the migration's
data impact first.

## Current boundaries

- Docker is required for actual image build/startup verification.
- Domain DNS, router forwarding, public IP/CGNAT, SMTP, Google OAuth and the
  AI model artifact directory are external configuration and are not inferred
  by this repository.
- A successful `docker compose config` proves syntax and interpolation only;
  it does not prove that the PC, DNS, certificates, database, model runtime or
  user flows work.
