# Local AI upstream fault tests

Run from the repository root in PowerShell:

```powershell
postman simulation run postman/simulations/ai-local-faults/ai-local-faults.sim.yaml --port 4501 --no-history --output ndjson
```

In another terminal:

```powershell
$env:LAB_PORTAL_AI_SIMULATION='true'
.\server\mvnw.cmd -f server/pom.xml '-Dtest=AiGatewayPostmanSimulationTest' test
```

Stop the simulation with Ctrl+C in its terminal. The mock binds localhost; it contains synthetic data and a test-only header value. It does not use the user's internal token. Unmodeled routes return501. `/__test/scenario` and `/__test/metrics` exist only in this stand-in.

The Spring client uses the simulation's single-port route `http://127.0.0.1:4501/ai` because it is explicitly environment-configurable. Thirteen tests exercise actual HTTP, including Cloudflare-style HTML 502/524, 504 with and without retry permission, timeout, and reset. The direct-reset test owns and closes a separate ephemeral JDK HTTP server. The mock does not prove Python authorization, model inference, business writes, quota accounting or GPU throughput. Evidence and remaining uncertainty are in `ai-local-faults.context.yaml`.
