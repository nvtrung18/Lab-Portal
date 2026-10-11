# P6-T6 Adapter Strategy Decision

## Task and status

- Task: `P6-T6` — Adapter Strategy Decision
- Status: `APPROVED`
- Acceptance result: `PASS`
- Evidence dependency: `P6_T5_COMPLETE_WITH_ARTIFACT_RETENTION_EXCEPTION`
- P6-T5 review result: `PASS_WITH_NON_BLOCKING_NOTES`

## P6-T5 evidence summary

The approved H01 results are:

| Candidate | PASS | FAIL | NEEDS_REVIEW | Downstream disposition |
| --- | ---: | ---: | ---: | --- |
| `qwen3_4b` | 19 | 14 | 0 | Primary candidate |
| `qwen3_1_7b` | 19 | 14 | 0 | Efficiency fallback |
| `qwen25_1_5b` | 8 | 25 | 0 | Rejected candidate |

`qwen3_4b` and `qwen3_1_7b` are tied on H01. The selection of `qwen3_4b`
as the primary downstream candidate does not mean that it beat `qwen3_1_7b`;
`qwen3_1_7b` remains the efficiency fallback.

## Shared base-model decision

Start every assistant from the approved shared base model. Specialize behavior
first through the assistant-specific system/profile prompt, tool schemas,
authorized Spring context, structured-output enforcement, and later
permission-aware RAG.

An adapter must address demonstrated domain behavior that these controls do not
adequately resolve. An adapter must not be trained merely to demonstrate that
AI functionality exists.

## Per-assistant adapter decision

| Assistant | Strategy |
| --- | --- |
| `RESEARCH_ASSISTANT` | `ADAPTER_REQUIRED` |
| `LAB_ASSISTANT` | `BASE_ONLY_APPROVED` |
| `ADMIN_ASSISTANT` | `BASE_ONLY_APPROVED` |

## Rationale

### RESEARCH_ASSISTANT — ADAPTER_REQUIRED

Research has the strongest structured-drafting requirements, and the P6-T5
evidence shows material failures in structured Research behavior. Phase 7 is
therefore allowed to build, train, and evaluate a Research adapter candidate.

This decision does not automatically promote an adapter. The candidate must
later demonstrate improvement over the base Research configuration under the
relevant evaluation criteria before it can be promoted.

### LAB_ASSISTANT — BASE_ONLY_APPROVED

Use the shared base model with the `LAB_ASSISTANT`-specific prompt, profile, and
tools first. Do not train a Lab adapter by default. The Lab adapter path may be
activated later only if domain evaluation demonstrates a material baseline
deficiency that prompt, profile, tool, schema, and RAG behavior cannot
adequately address.

### ADMIN_ASSISTANT — BASE_ONLY_APPROVED

Use the shared base model with the `ADMIN_ASSISTANT`-specific prompt, profile,
and tools first. Do not train an Admin adapter by default. The Admin adapter
path may be activated later only if domain evaluation demonstrates a material
baseline deficiency.

## Phase 7 handoff

Phase 7 may establish a reusable adapter-training pipeline. At this stage, only
the Research adapter path is activated:

- Research: `ADAPTER_REQUIRED`
- Lab: `BASE_ONLY_APPROVED`
- Admin: `BASE_ONLY_APPROVED`

Lab and Admin remain conditional future adapter paths. Establishing reusable
training capability does not authorize Lab or Admin adapter training and does
not authorize automatic promotion of the Research adapter candidate.

## Explicit limitations and non-claims

- This record does not claim that `qwen3_4b` beat `qwen3_1_7b`; their approved
  H01 results are tied.
- The historical official GPU benchmark was V5-R8-R3/A2. Its raw execution tree
  was lost after execution.
- This record does not reconstruct or claim missing latency, RAM, or VRAM
  measurements.
- V5-R8-R4 recovery is not represented as the historical R3/A2 execution.
- This record does not train, evaluate, or promote an adapter and does not add
  runtime or model artifacts.

## Acceptance result

`PASS`

All three assistants have an explicit strategy, the shared-base-first boundary
is retained, and the activated Phase 7 scope is limited to a Research adapter
candidate whose promotion remains evidence-dependent.
