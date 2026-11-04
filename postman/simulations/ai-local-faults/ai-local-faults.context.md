# Simulation context - ai-local-faults

This local stand-in owns no real credentials, model, quota or application data.
The actual request/response contract is app/models/contracts.py, app/security.py
and AiGatewayClientImpl.java. The mock response contains a synthetic authorized
snapshot; it does not validate business authority.

The verified claims, source pointers and unknowns are recorded in
[ai-local-faults.context.yaml](ai-local-faults.context.yaml).
Run commands and scope are in [README.md](README.md).

The latest 2026-11-04 run passed 13 real HTTP tests. Error and retry assertions were checked against the mock's
attempt counters, not just the response status. The Postman router converts a
mock socket close into proxy502, so a separate direct JDK HTTP socket-close test
establishes native NETWORK classification. GPU/model results require the independent
Kaggle evidence in `docs/testing/AI_SERVICE_HYBRID_REPORT_20261104.json`.

Nothing was published to Postman cloud. The CLI in this environment has no
status command; no authenticated owner-workspace discovery is claimed. The
simulation process used during verification was stopped after its tests.
