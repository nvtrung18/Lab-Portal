# AWS Local Preparation

This document records the local preparation completed before creating AWS infrastructure. No AWS resource is created or deployed by this branch.

## Target architecture

```text
React/Vite -> S3 + CloudFront
Spring Boot executable JAR -> Elastic Beanstalk
MySQL -> Amazon RDS
Redis -> Amazon ElastiCache
```

The application remains a local-filesystem application in this phase. S3 integration, AWS SDKs, Secrets Manager, CloudWatch configuration, CI/CD and infrastructure-as-code are deferred.

## Runtime prerequisites

- Use JDK 17 (`C:\Program Files\Java\jdk-17`) and Maven Wrapper 3.9.14.
- The Wrapper null-handling fix is in `server/mvnw.cmd`.
- Frontend dependencies are installed with `npm ci`; the lockfile must remain unchanged.

## Spring profiles

- `application.yml` contains shared settings and defaults the profile to `local`.
- `application-local.yml` imports optional `server/.env.properties`, uses local MySQL/Redis defaults, and keeps developer logging/Swagger behavior.
- `application-prod.yml` requires database, Redis, JWT, SMTP, CORS and storage environment variables; it has no localhost or secret fallbacks, disables development seeding and disables Swagger.
- The runtime port is `PORT` with a local default of `8080`.

Start locally from `server/` after providing values from `server/.env.example`:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd spring-boot:run
```

## Environment contract

Backend runtime variables:

```text
SPRING_PROFILES_ACTIVE, PORT
DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD
REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_SSL_ENABLED, REDIS_CONNECT_TIMEOUT
JWT_SECRET, JWT_EXPIRATION, JWT_REFRESH_EXPIRATION
MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM
MAIL_SMTP_AUTH, MAIL_SMTP_STARTTLS_ENABLE
APP_CORS_ALLOWED_ORIGINS
CV_STORAGE_PATH, REPORT_STORAGE_PATH, PRODUCT_STORAGE_PATH
```

`APP_FRONTEND_URL` is optional and currently has no Java consumer. It is not required for production startup. `MYSQL_ROOT_PASSWORD` is local Docker Compose-only; Spring Boot uses `DB_USERNAME`/`DB_PASSWORD` and never connects as MySQL root.

Frontend build configuration:

```text
VITE_API_BASE_URL
```

This is a public build-time value containing the backend origin only, without `/api`, a trailing slash, credentials, query string or hash. API methods continue to own their `/api/...` paths.

## Secrets and local Compose

- Copy examples to ignored local files; never commit `.env`, `.env.properties` or real credentials.
- `docker-compose.yml` passes `MYSQL_ROOT_PASSWORD` only to the MySQL container and creates the application database user from `DB_USERNAME`/`DB_PASSWORD`.
- Compose frontend services use `npm ci` and local `VITE_API_BASE_URL=http://localhost:8080`.

## Health and load balancer path

Actuator exposes only health endpoints under the existing `/api` context path:

```text
/api/actuator/health
/api/actuator/health/liveness
/api/actuator/health/readiness
```

Liveness contains only application/process state and is independent of MySQL and Redis. Readiness contains application readiness plus MySQL (`db`) and intentionally excludes Redis because Redis supports OTP, verification-token and password-reset flows rather than every request. The Elastic Beanstalk/ALB health path is:

```text
/api/actuator/health/readiness
```

`/api/system/health` remains as a deprecated compatibility endpoint and no longer claims that MySQL is connected without checking it.

## Redis

Redis host, port, password, TLS flag and connect timeout are profile-driven. Local defaults target `localhost:6379`; production requires `REDIS_HOST`, `REDIS_PORT` and `REDIS_SSL_ENABLED`. OTP key names and TTL/business behavior are unchanged. Redis is not part of readiness.

## CORS and authentication

`APP_CORS_ALLOWED_ORIGINS` is a comma-separated list of trimmed, deduplicated, exact origins. Wildcards and unknown origins are rejected. Allowed methods are `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`; allowed headers are `Authorization`, `Content-Type`, and `Accept`. Authentication remains stateless Bearer JWT, so Axios `withCredentials` is disabled.

## Local storage

`CV_STORAGE_PATH` controls both CV writes and the existing `/api/uploads/cv/...` static URL contract through `StaticResourceConfig`. Report and product adapters continue using `REPORT_STORAGE_PATH` and `PRODUCT_STORAGE_PATH`, with their existing API/download contracts and path-containment checks. This is not an S3 adapter. Tracked upload binaries are classified separately and are not removed by this branch.

Tracked-binary ledger (unchanged; removal requires explicit per-file approval):

```text
server/storage/products/29/groups/47/1.zip
server/storage/products/29/groups/47/2.zip
server/storage/products/37/1.pdf
server/storage/products/37/groups/54/2.pdf
server/uploads/reports/18f6309c-e5b9-4733-b3ec-4ca27e3520c6_Qu_n_l__NCKH_sinh_vi_n.pdf
server/uploads/cv/279ff9df-3ff3-4a40-8520-fb3c1308ff6e_NguyenVanTrung_CV_Intern_BackendDev.pdf
server/uploads/cv/51b9572a-1aa4-4e14-82ef-a5b96a9fa4d0_NguyenVanTrung_CV_Intern_BackendDev.pdf
server/uploads/cv/748fa20e-d86a-44dc-bdf5-15a92636680e_NguyenVanTrung_CV_Intern_BackendDev.pdf
server/uploads/cv/844953dc-306a-40b8-a318-4096f225deab_NguyenVanTrung_CV_Intern_BackendDev.pdf
server/uploads/cv/87446408-47c5-4956-af39-b32a9a91ff10_NguyenVanTrung_CV_Intern_BackendDev.pdf
server/uploads/cv/ba0c932e-3ebd-4124-8580-222ff4fd6f6b_BTL_Web.docx
```

## Frontend build

Production builds fail clearly when `VITE_API_BASE_URL` is missing, empty, malformed, contains credentials, has a path such as `/api`, or ends with `/`.

```powershell
cd client
npm ci
$env:VITE_API_BASE_URL = "https://backend.example.invalid"
npm run build
npm run preview
```

No production hostname or secret is committed.

## Elastic Beanstalk bundle

The repository Procfile is `server/Procfile` and uses the conservative Java SE command:

```text
web: java -jar application.jar --spring.profiles.active=prod
```

Create a local bundle with:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\scripts\package-eb.ps1
```

The ignored staging root is `server/.eb-staging/` and contains exactly:

```text
Procfile
application.jar
```

The script excludes `.jar.original`, source and javadoc JARs and can optionally create `server/target/lab-portal-eb.zip` with `-CreateZip`.

## Migration validation and deployment gate

Migrations V1-V55 are unchanged and must be validated only against a disposable MySQL 8 instance. V2/V4 seed a default admin credential; V24/V25 add known demo/test accounts. This branch documents the risk but does not remove data, add V56, alter checksums or repair Flyway.

An internet-facing or production deployment is **blocked** until `feature/production-database-seed-safety` is completed, reviewed and approved. A private/restricted AWS smoke deployment may be considered later for infrastructure validation only.

## Deferred AWS work

S3/CloudFront, RDS, ElastiCache, Elastic Beanstalk resources, Secrets Manager/Parameter Store, CloudWatch, Terraform/CloudFormation, CI/CD, SPA fallback and public deployment are separate approved tasks.
