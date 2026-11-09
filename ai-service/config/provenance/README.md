# AI approval provenance

The P6-T6 and P7-T5 records listed below are immutable copies from repository commit `257b01ab9eb4e8789a1be88c28e18d7586baf66d`. Their original relative layout is preserved under this provenance directory:

- `config/p6-t6-adapter-decisions.json` and the associated strategy document under `docs/architecture/`;
- `config/p7-t5-research-promotion/`;
- `evidence/p7-t5-research-promotion/decision.json`;
- `evidence/p7-t5-research-promotion-approval.json`.

The runtime descriptors in the parent config directory now use repository paths beginning with `ai-service/config/provenance/`. Their model file inventories, checksums, approval outcomes and source identities are unchanged. Moving a descriptor reference changes the descriptor's own computed identity, not the physical model or adapter identity.

References inside these copied approval records retain their original values so the content and recorded identities remain unchanged. Resolve local record references relative to this provenance directory. References to training/evaluation evidence not retained here belong to the original research snapshot; inspect them with `git show 257b01ab9eb4e8789a1be88c28e18d7586baf66d:<original-path>`.

The serving loader validates these as logical references and validates physical model files separately under `AI_MODEL_ARTIFACT_ROOT`. These records do not replace the model payloads required for runtime readiness. Regenerating research evidence belongs to a separate checkout of the original research pipeline.

P8-T3 serving authorization is recorded separately under `config/p8-t3-research-serving/` and `evidence/p8-t3-research-serving/`. The original P7-T5 snapshots remain unchanged. Runtime descriptors reference the P8-T3 decision and carry its `servingLoadAllowed` value; runtime activation fails closed when the value is missing or false. Serving still requires `AI_RUNTIME_LOAD_ENABLED=true`, valid artifact checksums, and a ready runtime.
