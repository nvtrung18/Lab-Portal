# Phase 14 - Spring-Python contract regression

## Scope

This regression checks the active Spring-to-AI and Spring-to-Face boundaries,
including fixed routes and headers, typed request/response payloads, bounded
timeouts, retry classification, unavailable services, model readiness failures,
artifact validation, and sanitized failure messages.

## Spring contract evidence

Command:

```powershell
cd server
.\mvnw.cmd "-Dtest=SpringPythonContractTest,AiGatewayClientImplTest,AiAssistantGatewayServiceImplTest,AiAssistantControllerTest,FaceProcessingContractTest,FaceProfileServiceImplTest,FaceCheckinServiceImplTest" test
```

Result on 2026-09-01: `BUILD SUCCESS`; 74 tests run, 0 failures, 0 errors,
0 skipped.

The suite verifies:

- the shared AI contract and typed AI gateway responses;
- bounded AI timeout/retry behavior and safe error translation;
- generic 503 responses without internal token or downstream response leakage;
- sanitized AI audit failure details;
- the frozen Face paths `/v1/face/embed` and `/v1/face/match`;
- trusted Face headers `X-Internal-Service-Token` and `X-Request-Id`;
- typed Face payloads, generated request identifiers, three-second connect/read
  timeouts, retryable 5xx classification, and sanitized downstream failures;
- the Spring Face profile and check-in service behavior around processing
  responses and unavailable processing.

## Face service evidence

Command:

```powershell
cd face-service
python -m pytest -q
```

Result on 2026-09-01: 6 passed in 0.78 seconds.

This includes the frozen processing contract, internal-token enforcement,
rejection of forwarded authorization, invalid input handling, and truthful
`FACE_MODEL_NOT_READY` behavior when no model is configured.

## AI service local-model limitation

Attempted command:

```powershell
cd ai-service
python -m pytest -q tests/test_api.py tests/test_security.py tests/test_artifacts.py tests/test_spring_contract.py
```

Collection stopped with four `ARTIFACT_FILE_MISSING` errors because the
approved Research adapter files referenced by `config/model-artifacts.json` are
deployment artifacts and `ai-service/artifacts/` is intentionally excluded from
Git. No model or adapter file was synthesized, substituted, or marked ready.

The AI Python local-model portion therefore remains an explicit environment
exception until the approved artifact bundle is mounted on the deployment
target. The Spring contract suite still covers AI timeout, unavailable service,
malformed protocol, safe 503 translation, and log/audit sanitization. The
Python artifact suite itself contains checksum mismatch, missing/corrupt
artifact, unsafe path, model-load failure, and truthful readiness cases, but
those cases are not recorded as passed in this local run.

## Conclusion

The executable Spring and Face contract layers pass with no observed contract
or privilege-boundary regression. AI model-backed Python verification is not a
pass and must be rerun with the approved immutable artifact bundle in the
deployment environment.
