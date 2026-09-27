# NCKH demo data on the production test instance

This is an **opt-in, synthetic test dataset**. It is not a record of completed research or real user accounts. The application does not import it on startup. Keep `DEV_SEED_ENABLED=false`.

The import creates five D35 research projects across the existing lab names plus three projects in `NCKH Thesis Demo 2026`. All eight projects start as `DRAFT`, with 32 `NOT_STARTED` milestones and 96 `TODO` tasks. The D35 groups have four members each; the thesis groups also have four members each. It creates no reports, products, evaluations, research logs, bookings, check-ins, or completed tasks.

## Before importing

1. Finish the normal deployment and confirm Flyway `V72` succeeded. The import refuses to run before that migration. This matters because `V72` removes the old V25 demo labs and accounts. If the current database already has research or user data tied to those rows, inspect it before redeploying; `V72` may fail on foreign keys.
2. Make and retain a backup of MySQL, storage, and uploads using `scripts/backup-production.ps1`. A successful backup message does not prove a restore will work; test restoration separately.
3. Check whether demo identities or research already exist. The import refuses collisions; it does not delete or overwrite existing rows.
4. Confirm the production Caddy and MySQL containers are running. The import uses Caddy to generate BCrypt hashes and sends SQL to MySQL through Docker Compose. It requires no publicly exposed database port.

From `D:\Lab-Portal` in CMD, after the new files are available on the PC:

```cmd
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\Lab-Portal\scripts\import-production-nckh-demo.ps1"
```

The script creates a different random password for each of its 38 accounts. It keeps the passwords in an access-controlled JSON file under `C:\ProgramData\LabPortal\DemoCredentials` and prints the file path when import succeeds. It does not write plaintext passwords to Git, SQL, process arguments, or the console. Share individual credentials only with the intended testers and rotate or disable these accounts when testing ends.

Sign in with the `email` and `password` fields from that JSON file. The login API validates an email address, so the `username` field is only a reference. The development credentials in `docs/demo/mockdb-accounts.md` are not valid for this production import.

All production demo addresses use `@example.invalid` to avoid sending mail to real inboxes. Password-reset emails cannot be received for these accounts; keep the private credential file until the test period ends.

This import is intended for a fresh post-`V72` demo state. It combines the foundation and the two research-plan fixtures into one MySQL transaction. MySQL stops on a failed precondition or postcondition; do not use `mysql --force`. The import is not safe to rerun over research that testers have edited. Later normal `docker compose up` deployments keep MySQL's named volume, but a future data migration or volume deletion could change or remove rows.

After import, inspect the production API/UI for a manager and a student account and compare project, group, milestone, and task counts with the expected values above. Do not treat this document or a successful SQL import as browser-level proof.
