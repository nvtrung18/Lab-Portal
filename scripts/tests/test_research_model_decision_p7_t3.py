import hashlib
import importlib.util
import json
import tempfile
import unittest
import zipfile
from copy import deepcopy
from pathlib import Path
from unittest.mock import Mock, patch

import yaml


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("p7t3", ROOT / "scripts" / "research-model-decision-p7-t3.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class P7T3ResearchModelDecisionTests(unittest.TestCase):
    def test_main_reports_invalid_governance_request_without_uncaught_exception(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            request_path = Path(temporary_directory) / "request.json"
            output_path = Path(temporary_directory) / "decision.json"
            request_path.write_text('{"status":"PENDING_USER_APPROVAL"}\n', encoding="utf-8")
            with patch.object(
                MODULE.sys,
                "argv",
                [
                    "research-model-decision-p7-t3.py",
                    "--governance-request",
                    str(request_path),
                    "--output",
                    str(output_path),
                ],
            ), patch.object(MODULE.sys, "stderr", Mock()):
                result = MODULE.main()

        self.assertEqual(2, result)

    def suite(self):
        def case(case_id, use_case, tags, mode="DRAFT_PRESENTATION"):
            return {
                "evalCaseId": case_id,
                "assistantKey": "RESEARCH_ASSISTANT",
                "useCaseId": use_case,
                "suiteTags": tags,
                "caseState": "ACTIVE",
                "humanProfileId": "REFUSAL" if mode == "SAFE_REFUSAL" else "DRAFT_RESEARCH",
                "responseContract": {"mode": mode, "language": "VI", "markers": []},
            }

        return {
            "suiteId": "P6-T4-EVALUATION-SUITES",
            "suiteVersion": "1.0.0",
            "EVALUATION_ONLY": True,
            "TRAINING_PROHIBITED": True,
            "caseInventory": [
                case("CASE-PROPOSAL", "RESEARCH_UC_004", ["FUNCTIONAL"]),
                case("CASE-SUGGESTION", "RESEARCH_UC_005", ["FUNCTIONAL"]),
                case("CASE-REPORT", "RESEARCH_UC_006", ["FUNCTIONAL"]),
                case("CASE-REFUSAL", "RESEARCH_UC_003", ["SAFE_REFUSAL"], mode="SAFE_REFUSAL"),
                case("CASE-STRUCTURED", "RESEARCH_UC_001", ["STRUCTURED_OUTPUT"]),
            ],
        }

    def benchmark_config(self):
        return {
            "assistantProfiles": {
                "RESEARCH_ASSISTANT": {"profile": "research", "prompt": "research-v2"},
            },
            "suite": {
                "id": "P6-T4-EVALUATION-SUITES",
                "version": "1.0.0",
                "digest": "8b75d356890a8a5c2318305589301b6ee6d73fbd3665b9af2063f98e13ea7417",
            },
            "candidates": [
                {
                    "id": "qwen3_4b",
                    "repository": "Qwen/Qwen3-4B-Instruct-2507",
                    "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
                }
            ],
        }

    def evidence(self, suite=None, default_result="PASS"):
        suite = suite or self.suite()
        return {
            "artifactType": "P7-T3-RESEARCH-BASELINE-EVIDENCE-BINDING",
            "schemaVersion": "1.0.0",
            "decisionRuleVersion": "P7-T3-RESEARCH-GATES-2.0.0",
            "assistantKey": "RESEARCH_ASSISTANT",
            "baseModel": {
                "identifier": "Qwen/Qwen3-4B-Instruct-2507",
                "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
            },
            "promptProfile": {"profile": "research", "promptVersion": "research-v2"},
            "evaluationSuite": {
                "id": suite["suiteId"],
                "version": suite["suiteVersion"],
                "digest": "8b75d356890a8a5c2318305589301b6ee6d73fbd3665b9af2063f98e13ea7417",
            },
            "evidenceReference": "retained/p7-t3-research-baseline-evidence.json",
            "sourceEvidence": {
                "artifactType": "P6-T5-H01-USER-APPROVED-FROZEN-CONTRACT",
                "reference": "p6-t5-v5-r8-r3-h01-user-approved-frozen-contract.json",
                "sha256": "1" * 64,
                "sizeBytes": 1,
                "lineage": "P6-T5-V5-R8-R3",
                "executionAttempt": "A2",
                "reviewCheckpoint": "H01",
                "approvalDecision": "APPROVED",
                "proposalRevision": "R3",
                "reviewInputReference": "p6-t5-v5-r8-r3-h01-review-input.json",
                "reviewInputSha256": "2" * 64,
                "manifestReference": "p6-t5-final-evidence-manifest-v1.json",
                "manifestSha256": "3" * 64,
            },
            "candidate": {
                "id": "qwen3_4b",
                "runId": "qwen3_4b-R01",
                "outputDigest": "4" * 64,
            },
            "sourceCommit": "test-source-commit",
            "caseResults": [
                {
                    "evalCaseId": case["evalCaseId"],
                    "result": default_result,
                    "evidenceSha256": hashlib.sha256(case["evalCaseId"].encode()).hexdigest(),
                    "sourceRecordReference": (
                        "p6-t5-v5-r8-r3-h01-user-approved-frozen-contract.json"
                        f"#/candidates/qwen3_4b/records/{case['evalCaseId']}"
                    ),
                }
                for case in suite["caseInventory"]
                if case.get("assistantKey") == "RESEARCH_ASSISTANT"
            ],
        }

    def gap_case(self, case_id, scenario_id, *, report=False):
        if report:
            checked = json.loads(
                (ROOT / "evals" / "p7-t3-research-gap-evaluation-suite.json").read_text(encoding="utf-8")
            )
            case = deepcopy(checked["proposedCaseInventory"][0])
            return case, deepcopy(checked["expectedObservations"][case["expectedObservationId"]])
        action_risk = "DRAFT_ONLY" if report else "PROHIBITED"
        mode = "DRAFT_PRESENTATION" if report else "SAFE_REFUSAL"
        use_case_id = "RESEARCH_UC_006" if report else None
        structured_output = "RESEARCH_REPORT_REVIEW_DRAFT" if report else None
        tool = (
            {"kind": "NONE"}
            if report
            else {
                "kind": "REJECTED",
                "group": "RESEARCH_READ",
                "name": "synthetic.authorization.denied",
                "intent": scenario_id,
                "reason": "PROHIBITED",
            }
        )
        case = {
            "evalCaseId": case_id,
            "mandatoryScenarioId": scenario_id,
            "suiteTags": ["FUNCTIONAL", "HUMAN_EVAL", "STRUCTURED_OUTPUT"] if report else ["AUTHORIZATION", "SAFE_REFUSAL"],
            "caseState": "ACTIVE" if report else "NULL_CONTEXT_ASSERTION",
            "assistantKey": "RESEARCH_ASSISTANT",
            "useCaseId": use_case_id,
            "input": "Review synthetic report fixture." if report else None,
            "authorizedContext": {"p6t3FixtureCaseId": "POS-RESEARCH-006"} if report else None,
            "p6t3Root": "research" if report else None,
            "expectedObservationId": f"OBS-{case_id}",
            "allowedTool": None,
            "rejectedTool": None if report else tool,
            "structuredOutputContract": structured_output,
            "referencedContextIds": ["POS-RESEARCH-006"] if report else [],
            "humanProfileId": "DRAFT_RESEARCH" if report else "REFUSAL",
            "responseContract": {
                "mode": mode,
                "language": "VI",
                "markers": ["HUMAN_REVIEW_NEEDED"] if report else ["NO_DISCLOSURE", "NO_EXECUTION"],
            },
        }
        observation = {
            "behavior": "SUCCESS" if report else "SAFE_REFUSAL",
            "actionRisk": action_risk,
            "toolRequest": tool,
            "structuredOutput": (
                {
                    "kind": "RESEARCH_REPORT_REVIEW_DRAFT",
                    "reportRef": "synthetic-report",
                    "review": "bounded draft",
                    "requiresHumanReview": True,
                }
                if report
                else None
            ),
            "referencedContextIds": case["referencedContextIds"],
            "responseContract": case["responseContract"],
        }
        return case, observation

    def gap_suite(self, *, scenarios=None, include_report=False):
        scenarios = scenarios or (
            ("E-AUTH-011", "RESEARCH_GROUP_OUTSIDE_DENY"),
            ("E-AUTH-012", "RESEARCH_TASK_UNAUTHORIZED_DENY"),
        )
        pairs = [self.gap_case(case_id, scenario_id) for case_id, scenario_id in scenarios]
        cases = [pair[0] for pair in pairs]
        report_pair = self.gap_case("E-FUNC-RESEARCH-006", "RESEARCH_UC_006", report=True)
        observations = {
            **{pair[0]["expectedObservationId"]: pair[1] for pair in pairs},
            report_pair[0]["expectedObservationId"]: report_pair[1],
        }
        suite = {
            "artifactType": "P7-T3-RESEARCH-GAP-EVALUATION-SUITE",
            "schemaVersion": "1.0.0",
            "suiteId": "P7-T3-RESEARCH-GAP-EVALUATION",
            "suiteVersion": "1.1.0",
            "suiteDigest": "",
            "baseSuite": {
                "id": "P6-T4-EVALUATION-SUITES",
                "version": "1.0.0",
                "digest": "8b75d356890a8a5c2318305589301b6ee6d73fbd3665b9af2063f98e13ea7417",
            },
            "EVALUATION_ONLY": True,
            "TRAINING_PROHIBITED": True,
            "caseInventory": cases,
            "proposedCaseInventory": [report_pair[0]],
            "expectedObservations": observations,
            "matrices": {
                "humanApplicabilityBinding": {
                    "DRAFT_RESEARCH": ["E-FUNC-RESEARCH-006"],
                    "REFUSAL": [case["evalCaseId"] for case in cases if case["humanProfileId"] == "REFUSAL"],
                    "NONE": [],
                }
            },
            "governanceBlockers": [
                {
                    "evalCaseId": "E-DEFERRED-RESEARCH-006",
                    "useCaseId": "RESEARCH_UC_006",
                    "categoryId": "CAT_RESEARCH_REPORT_METADATA",
                    "status": "AWAITING_GOVERNANCE_APPROVAL",
                    "useDecision": "DEFERRED",
                    "permittedPurposes": [],
                    "prohibitedPurposes": ["BENCHMARK", "DEVELOPMENT_TEST", "EVALUATION", "HUMAN_EVALUATION", "TRAINING"],
                    "sanitizationDisposition": "DEFERRED_NO_EXPORT",
                    "reason": "REPORT_CONTENT_NOT_PROJECTED_AND_EVALUATION_EXPORT_PROHIBITED",
                    "governanceRequestReference": "config/p7-t3-research-report-eval-governance-request.json",
                    "proposedEvalCaseId": "E-FUNC-RESEARCH-006",
                }
            ],
            "executionPolicy": {
                "candidateId": "qwen3_4b",
                "sourceRunId": "qwen3_4b-R01",
                "model": deepcopy(MODULE.BASE_MODEL),
                "caseIds": [case["evalCaseId"] for case in cases],
                "postApprovalCaseIds": sorted([case["evalCaseId"] for case in cases] + ["E-FUNC-RESEARCH-006"]),
                "governanceApprovalRequiredCaseIds": ["E-FUNC-RESEARCH-006"],
                "governanceRequestReference": "config/p7-t3-research-report-eval-governance-request.json",
                "executionScope": "TARGETED_CASES_ONLY",
                "networkAccess": "PROHIBITED",
                "humanReviewRequired": True,
                "command": "python scripts/research-gap-evidence-p7-t3.py --run --model-path PREPROVISIONED_QWEN3_4B_SNAPSHOT",
                "approvedCommand": "python scripts/research-gap-evidence-p7-t3.py --run --governance-approval APPROVED_GOVERNANCE_ARTIFACT --model-path PREPROVISIONED_QWEN3_4B_SNAPSHOT",
                "outputReference": "evidence/p7-t3-gap-run/qwen3_4b-R01-P7T3-GAP01.json",
                "reviewReference": "evidence/p7-t3-gap-run/qwen3_4b-R01-P7T3-GAP01-review-input.json",
                "approvedOutputReference": "evidence/p7-t3-gap-run-approved/qwen3_4b-R01-P7T3-GAP01.json",
                "approvedReviewReference": "evidence/p7-t3-gap-run-approved/qwen3_4b-R01-P7T3-GAP01-review-input.json",
                "frozenEvidenceReference": "evidence/p7-t3-research-gap-evidence-v1.json",
            },
        }
        suite["suiteDigest"] = MODULE.gap_suite_identity(suite)
        return suite

    def governance_authorization(self, gap_suite):
        request = json.loads(
            (ROOT / "config" / "p7-t3-research-report-eval-governance-request.json").read_text(encoding="utf-8")
        )
        report_case = gap_suite["proposedCaseInventory"][0]
        request["evaluationCase"]["sha256"] = MODULE.sha256_bytes(MODULE.canonical_bytes(report_case))
        request["suiteLineage"]["gap"] = {
            "id": gap_suite["suiteId"],
            "version": gap_suite["suiteVersion"],
            "digest": gap_suite["suiteDigest"],
        }
        request["requestIdentity"] = MODULE.GOVERNANCE.request_identity(request)
        approval = MODULE.GOVERNANCE.finalize_approval(
            request,
            approved_by="synthetic-test-governance-owner",
            approved_at="2026-08-21T00:00:00Z",
            gap_suite=gap_suite,
        )
        return request, approval

    def gap_evidence(self, gap_suite=None, result="PASS", *, include_report=False):
        gap_suite = gap_suite or self.gap_suite()
        execution_cases = [*gap_suite["caseInventory"]]
        governance_binding = None
        if include_report:
            execution_cases.extend(gap_suite["proposedCaseInventory"])
            request, approval = self.governance_authorization(gap_suite)
            governance_binding = {
                "requestIdentity": request["requestIdentity"],
                "approvalIdentity": approval["artifactIdentity"],
            }
        evidence = {
            "artifactType": "P7-T3-RESEARCH-GAP-EVIDENCE",
            "schemaVersion": "1.0.0",
            "decisionRuleVersion": "P7-T3-RESEARCH-GATES-3.0.0",
            "assistantKey": "RESEARCH_ASSISTANT",
            "baseModel": deepcopy(MODULE.BASE_MODEL),
            "promptProfile": deepcopy(MODULE.PROMPT_PROFILE),
            "suiteLineage": {
                "base": deepcopy(gap_suite["baseSuite"]),
                "gap": {
                    "id": gap_suite["suiteId"],
                    "version": gap_suite["suiteVersion"],
                    "digest": gap_suite["suiteDigest"],
                },
            },
            "candidate": {
                "id": "qwen3_4b",
                "sourceRunId": "qwen3_4b-R01",
                "gapRunId": "qwen3_4b-R01-P7T3-GAP01",
                "outputDigest": "7" * 64,
            },
            "approval": {
                "status": "USER_APPROVED",
                "reference": "p7-t3-gap-review.json",
                "sha256": "8" * 64,
            },
            "evidenceReference": "evidence/p7-t3-research-gap-evidence-v1.json",
            "sourceCommit": "test-source-commit",
            "executionCaseIds": sorted(case["evalCaseId"] for case in execution_cases),
            "governanceApproval": governance_binding,
            "caseResults": [
                {
                    "evalCaseId": case["evalCaseId"],
                    "result": result,
                    "caseDigest": MODULE.sha256_bytes(MODULE.canonical_bytes(case)),
                    "evidenceSha256": hashlib.sha256(case["evalCaseId"].encode()).hexdigest(),
                    "sourceRecordReference": f"qwen3_4b-R01-P7T3-GAP01.json#/cases/{case['evalCaseId']}",
                    "humanReviewStatus": "USER_APPROVED",
                }
                for case in execution_cases
            ],
            "artifactIdentity": "",
        }
        evidence["artifactIdentity"] = MODULE.gap_evidence_identity(evidence)
        return evidence

    def decisions(self, research_decision="ADAPTER_REQUIRED"):
        decisions = json.loads(
            (ROOT / "config" / "p6-t6-adapter-decisions.json").read_text(encoding="utf-8")
        )
        decisions["decisions"]["RESEARCH_ASSISTANT"] = research_decision
        return decisions

    def training_config(self):
        return json.loads((ROOT / "config" / "p7-t2-training-pipeline.json").read_text(encoding="utf-8"))

    def decide(self, evidence=None, decision_manifest=None, **kwargs):
        return MODULE.decide_research_model(
            self.suite(),
            self.benchmark_config(),
            decision_manifest or self.decisions(),
            self.training_config(),
            self.evidence() if evidence is None else evidence,
            frozen_evidence=[],
            source_commit="test-source-commit",
            **kwargs,
        )

    def frozen_inputs(self, suite=None, result_by_case=None):
        suite = suite or self.suite()
        result_by_case = result_by_case or {}
        records = []
        review_records = []
        for case in suite["caseInventory"]:
            if case.get("caseState") == "DEFERRED_ASSERTION_ONLY":
                continue
            candidate_case = {"evalCaseId": case["evalCaseId"], "raw": "frozen"}
            case_digest = MODULE.sha256_bytes(MODULE.canonical_bytes(candidate_case))
            outcome = result_by_case.get(case["evalCaseId"], "PASS")
            records.append(
                {
                    "evidenceRefs": [f"evalCaseId:{case['evalCaseId']}"],
                    "profileId": case.get("humanProfileId") or "DRAFT_RESEARCH",
                    "reviewerRationale": "Approved frozen result.",
                    "overall": outcome,
                    "dimensions": [],
                    "candidateCaseDigest": case_digest,
                    "evalCaseId": case["evalCaseId"],
                }
            )
            review_records.append(
                {
                    "evalCaseId": case["evalCaseId"],
                    "suiteCase": deepcopy(case),
                    "candidateCase": candidate_case,
                    "rawOutput": {"rawTextDigest": "5" * 64},
                    "humanReview": {"decision": None, "notes": None},
                }
            )
        pass_count = sum(record["overall"] == "PASS" for record in records)
        fail_count = sum(record["overall"] == "FAIL" for record in records)
        needs_review_count = sum(record["overall"] == "NEEDS_REVIEW" for record in records)
        review_input = {
            "lineage": "P6-T5-V5-R8-R3",
            "executionAttempt": "A2",
            "reviewCheckpoint": "H01",
            "candidates": {
                "qwen3_4b": {
                    "candidateRunId": "qwen3_4b-R01",
                    "candidateOutputDigest": "4" * 64,
                    "applicableCaseCount": len(records),
                    "missingRawOutputCaseIds": [],
                    "records": review_records,
                }
            },
        }
        review_input_sha256 = MODULE.sha256_bytes(MODULE.canonical_bytes(review_input))
        contract = {
            "artifactType": "P6-T5-H01-USER-APPROVED-FROZEN-CONTRACT",
            "materializationRevision": "R2-FROZEN-CONTRACT",
            "lineage": "P6-T5-V5-R8-R3",
            "executionAttempt": "A2",
            "reviewCheckpoint": "H01",
            "approval": {
                "decision": "APPROVED",
                "source": "user-current-chat",
                "approvedAt": "2026-08-19T05:46:17.142028Z",
                "proposalRevision": "R3",
                "proposalSha256": "6" * 64,
            },
            "sourceReviewInputSha256": review_input_sha256,
            "candidates": {
                "qwen3_4b": {
                    "candidateRunId": "qwen3_4b-R01",
                    "candidateOutputDigest": "4" * 64,
                    "recordCount": len(records),
                    "summary": {
                        "PASS": pass_count,
                        "FAIL": fail_count,
                        "NEEDS_REVIEW": needs_review_count,
                    },
                    "records": records,
                }
            },
            "contractAdaptation": {
                "semanticDecisionChanged": False,
                "outcomesChanged": False,
                "rationalesChanged": False,
                "evidenceRefPolicy": "Use only evalCaseId:<case_id>.",
                "candidateCaseDigestPolicy": "Rebind mechanically.",
                "sidecarPolicy": "Frozen.",
                "removedAssumptions": [],
            },
        }
        source_sha256 = "1" * 64
        source_size = 123
        manifest = {
            "artifacts": {
                "approvedH01": {
                    "path": "evidence/p6-t5-v5-r8-r3-h01-user-approved-frozen-contract.json",
                    "sha256": source_sha256,
                    "sizeBytes": source_size,
                }
            }
        }
        return contract, review_input, review_input_sha256, manifest, source_sha256, source_size

    def import_contract(self, suite=None, result_by_case=None, contract=None, **overrides):
        suite = suite or self.suite()
        generated_contract, review_input, review_sha, manifest, source_sha, source_size = self.frozen_inputs(
            suite, result_by_case
        )
        contract = contract or generated_contract
        values = {
            "source_reference": "p6-t5-v5-r8-r3-h01-user-approved-frozen-contract.json",
            "source_sha256": source_sha,
            "source_size": source_size,
            "evidence_reference": "evidence/p7-t3-research-baseline-evidence.json",
            "review_input": review_input,
            "review_input_reference": "p6-t5-v5-r8-r3-h01-review-input.json",
            "review_input_sha256": review_sha,
            "evidence_manifest": manifest,
            "evidence_manifest_reference": "p6-t5-final-evidence-manifest-v1.json",
            "evidence_manifest_sha256": "3" * 64,
            "source_commit": "test-source-commit",
        }
        values.update(overrides)
        return MODULE.import_frozen_h01_contract(
            contract,
            suite,
            self.benchmark_config(),
            **values,
        )

    def write_approved_dataset(self, root):
        governance = yaml.safe_load(
            (ROOT / "docs" / "architecture" / "ai" / "data-governance.yml").read_text(encoding="utf-8")
        )["contract"]
        manifest = deepcopy(governance["validation_fixtures"]["approved_adapter_training_export"]["manifest"])
        dataset_directory = Path(root) / "approved-research-dataset"
        dataset_directory.mkdir()
        artifacts = []
        for split in ("train", "validation", "evaluation"):
            content = (json.dumps({"contentId": split}, sort_keys=True, separators=(",", ":")) + "\n").encode()
            filename = f"{split}.jsonl"
            (dataset_directory / filename).write_bytes(content)
            artifacts.append(
                {"filename": filename, "recordCount": 1, "sha256": hashlib.sha256(content).hexdigest()}
            )
        manifest.update(
            {
                "pipeline_schema_version": "1.0.0",
                "pipeline_version": "1.0.0",
                "checksum_algorithm": "SHA-256",
                "artifacts": artifacts,
            }
        )
        manifest["checksum"] = MODULE.P7T2.dataset_identity(manifest)
        manifest_path = dataset_directory / "manifest.json"
        manifest_path.write_text(json.dumps(manifest, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        return manifest_path, manifest

    def configured_dataset(self, manifest):
        config = self.training_config()
        config["dataset"] = {
            "manifestReference": "approved-research-dataset/manifest.json",
            "identity": manifest["checksum"],
        }
        return config

    def candidate_metadata(self, config, manifest):
        return {
            "schemaVersion": "1.0.0",
            "pipelineVersion": "1.0.0",
            "assistantKey": "RESEARCH_ASSISTANT",
            "decision": "ADAPTER_REQUIRED",
            "status": "COMPLETED",
            "backend": "REAL_QLORA",
            "qualityEvidence": "REAL_TRAINING_EXECUTION",
            "adapterDisposition": "CANDIDATE_ONLY",
            "baseModel": config["baseModel"],
            "datasetIdentity": manifest["checksum"],
            "trainingConfigIdentity": MODULE.P7T2.training_config_identity(config),
            "seed": config["seed"],
            "trainingRunIdentity": MODULE.P7T2.training_run_identity(config),
            "adapterMethod": config["adapter"]["method"],
            "checkpoints": [{"checkpointName": "checkpoint-00000002", "globalStep": 2}],
            "exportedArtifacts": [{"filename": "adapter_model.safetensors", "sha256": "1" * 64}],
            "sourceCommit": "test-source-commit",
        }

    def test_all_required_research_baseline_gates_are_represented(self):
        baseline = MODULE.evaluate_research_baseline(self.suite(), self.evidence())

        self.assertEqual(
            {
                "TASK_PROPOSAL_DRAFT",
                "TASK_SUGGESTION",
                "REPORT_REVIEW_DRAFT",
                "SAFE_REFUSAL",
                "STRUCTURED_OUTPUT",
            },
            {gate["gate"] for gate in baseline["gates"]},
        )

    def test_passing_all_required_gates_produces_base_only_when_upstream_policy_allows_it(self):
        decision = self.decide(decision_manifest=self.decisions("BASE_ONLY_APPROVED"))

        self.assertEqual("PASS", decision["overallBaselineResult"])
        self.assertEqual("BASE_ONLY_APPROVED", decision["decision"])
        self.assertEqual("BASE_ONLY_APPROVED", decision["outcome"])
        self.assertFalse(decision["training"]["invoked"])

    def test_passing_baseline_cannot_override_authoritative_adapter_required_decision(self):
        decision = self.decide()

        self.assertEqual("PASS", decision["overallBaselineResult"])
        self.assertEqual("ADAPTER_REQUIRED", decision["decision"])
        self.assertEqual("UPSTREAM_DECISION_CONFLICT", decision["candidateBuild"]["reason"])
        self.assertFalse(decision["training"]["invoked"])

    def test_material_required_gate_failure_produces_adapter_required(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"

        decision = self.decide(evidence=evidence)

        self.assertEqual("FAIL", decision["overallBaselineResult"])
        self.assertEqual("ADAPTER_REQUIRED", decision["decision"])

    def test_missing_evaluation_evidence_fails_closed(self):
        evidence = self.evidence()
        evidence["caseResults"].pop()

        decision = self.decide(evidence=evidence)

        self.assertEqual("UNRESOLVED", decision["overallBaselineResult"])
        self.assertEqual("ADAPTER_REQUIRED+CANDIDATE_BUILD_BLOCKED", decision["outcome"])
        self.assertEqual("BASELINE_EVIDENCE_INCOMPLETE", decision["candidateBuild"]["reason"])
        self.assertFalse(decision["training"]["invoked"])

    def test_base_only_approved_never_invokes_training(self):
        training_invoker = Mock(side_effect=AssertionError("training must not run"))

        decision = self.decide(
            decision_manifest=self.decisions("BASE_ONLY_APPROVED"),
            training_invoker=training_invoker,
        )

        training_invoker.assert_not_called()
        self.assertEqual("NOT_REQUIRED", decision["training"]["status"])

    def test_base_only_path_does_not_validate_adapter_prerequisites(self):
        config = self.training_config()
        config["assistantKey"] = "LAB_ASSISTANT"

        decision = MODULE.decide_research_model(
            self.suite(),
            self.benchmark_config(),
            self.decisions("BASE_ONLY_APPROVED"),
            config,
            self.evidence(),
            frozen_evidence=[],
            source_commit="test-source-commit",
        )

        self.assertEqual("BASE_ONLY_APPROVED", decision["outcome"])

    def test_valid_user_approved_frozen_contract_imports_exact_research_cases(self):
        evidence = self.import_contract()

        self.assertEqual("P7-T3-RESEARCH-BASELINE-EVIDENCE-BINDING", evidence["artifactType"])
        self.assertEqual("qwen3_4b-R01", evidence["candidate"]["runId"])
        self.assertEqual(
            {case["evalCaseId"] for case in self.suite()["caseInventory"]},
            {result["evalCaseId"] for result in evidence["caseResults"]},
        )

    def test_json_loader_rejects_duplicate_keys(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            evidence_path = Path(temporary_directory) / "duplicate.json"
            evidence_path.write_text('{"artifactType":"draft","artifactType":"frozen"}', encoding="utf-8")

            with self.assertRaisesRegex(MODULE.ResearchDecisionError, "duplicate JSON key"):
                MODULE._load_document(evidence_path, "baseline evidence")

    def test_bundle_rejects_closure_that_does_not_reference_frozen_contract(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            contract_name = "p6-t5-v5-r8-r3-h01-user-approved-frozen-contract.json"
            contract_path = root / contract_name
            contract_bytes = b'{"approved":true}\n'
            contract_path.write_bytes(contract_bytes)
            contract_sha = hashlib.sha256(contract_bytes).hexdigest()
            closure = {
                "humanEvaluation": {
                    "artifact": contract_name,
                    "sha256": "9" * 64,
                    "state": "USER_APPROVED",
                }
            }
            closure_bytes = json.dumps(closure, sort_keys=True).encode()
            manifest = {
                "artifacts": {
                    "approvedH01": {
                        "path": f"evidence/{contract_name}",
                        "sha256": contract_sha,
                        "sizeBytes": len(contract_bytes),
                    },
                    "closure": {
                        "path": "evidence/p6-t5-final-closure-v1.json",
                        "sha256": hashlib.sha256(closure_bytes).hexdigest(),
                        "sizeBytes": len(closure_bytes),
                    },
                }
            }
            bundle_path = root / "bundle.zip"
            with zipfile.ZipFile(bundle_path, "w") as bundle:
                bundle.writestr(contract_name, contract_bytes)
                bundle.writestr("p6-t5-final-closure-v1.json", closure_bytes)
                bundle.writestr("p6-t5-final-evidence-manifest-v1.json", json.dumps(manifest))

            with self.assertRaisesRegex(MODULE.ResearchDecisionError, "closure"):
                MODULE._load_p6_t5_evidence_bundle(bundle_path, contract_path)

    def test_frozen_contract_rejects_wrong_candidate_identity(self):
        contract, review_input, review_sha, manifest, source_sha, source_size = self.frozen_inputs()
        contract["candidates"]["qwen3_4b"]["candidateRunId"] = "qwen3_1_7b-R01"

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "candidate"):
            self.import_contract(
                contract=contract,
                review_input=review_input,
                review_input_sha256=review_sha,
                evidence_manifest=manifest,
                source_sha256=source_sha,
                source_size=source_size,
            )

    def test_frozen_contract_rejects_wrong_suite_version(self):
        suite = self.suite()
        suite["suiteVersion"] = "2.0.0"

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "suite"):
            self.import_contract(suite=suite)

    def test_frozen_contract_rejects_duplicate_case_ids(self):
        contract, review_input, review_sha, manifest, source_sha, source_size = self.frozen_inputs()
        contract["candidates"]["qwen3_4b"]["records"].append(
            deepcopy(contract["candidates"]["qwen3_4b"]["records"][0])
        )
        contract["candidates"]["qwen3_4b"]["recordCount"] += 1

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "duplicate evalCaseId"):
            self.import_contract(
                contract=contract,
                review_input=review_input,
                review_input_sha256=review_sha,
                evidence_manifest=manifest,
                source_sha256=source_sha,
                source_size=source_size,
            )

    def test_draft_review_cannot_override_user_approved_frozen_contract(self):
        contract, review_input, review_sha, manifest, source_sha, source_size = self.frozen_inputs()
        contract["artifactType"] = "P6-T5-H01-AI-REVIEW-PROPOSAL"

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "user-approved frozen contract"):
            self.import_contract(
                contract=contract,
                review_input=review_input,
                review_input_sha256=review_sha,
                evidence_manifest=manifest,
                source_sha256=source_sha,
                source_size=source_size,
            )

    def test_aggregate_totals_cannot_satisfy_case_level_gates(self):
        contract, review_input, review_sha, manifest, source_sha, source_size = self.frozen_inputs()
        candidate = contract["candidates"]["qwen3_4b"]
        candidate["records"] = []
        candidate["recordCount"] = 0
        candidate["summary"] = {"PASS": 19, "FAIL": 14, "NEEDS_REVIEW": 0}

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "aggregate|record"):
            self.import_contract(
                contract=contract,
                review_input=review_input,
                review_input_sha256=review_sha,
                evidence_manifest=manifest,
                source_sha256=source_sha,
                source_size=source_size,
            )

    def test_admin_refusal_evidence_cannot_satisfy_research_safe_refusal_gate(self):
        suite = self.suite()
        refusal = next(case for case in suite["caseInventory"] if case["evalCaseId"] == "CASE-REFUSAL")
        refusal["assistantKey"] = "ADMIN_ASSISTANT"
        refusal["mandatoryScenarioId"] = "RESEARCH_TASK_UNAUTHORIZED_DENY"
        evidence = self.import_contract(suite=suite)

        baseline = MODULE.evaluate_research_baseline(suite, evidence)
        safe_refusal = next(gate for gate in baseline["gates"] if gate["gate"] == "SAFE_REFUSAL")
        self.assertEqual("UNRESOLVED", safe_refusal["result"])
        self.assertIn("CASE-REFUSAL", safe_refusal["incompatibleCaseIds"])

    def test_research_group_and_task_refusal_cases_are_accepted_without_private_context(self):
        gap_suite = self.gap_suite()

        MODULE.validate_gap_suite(gap_suite, self.suite())

        by_scenario = {case["mandatoryScenarioId"]: case for case in gap_suite["caseInventory"]}
        self.assertEqual("RESEARCH_ASSISTANT", by_scenario["RESEARCH_GROUP_OUTSIDE_DENY"]["assistantKey"])
        self.assertEqual("RESEARCH_ASSISTANT", by_scenario["RESEARCH_TASK_UNAUTHORIZED_DENY"]["assistantKey"])
        self.assertTrue(all(case["authorizedContext"] is None for case in gap_suite["caseInventory"]))
        self.assertTrue(all(case["referencedContextIds"] == [] for case in gap_suite["caseInventory"]))

    def test_gap_suite_rejects_wrong_assistant_and_wrong_base_suite(self):
        wrong_assistant = self.gap_suite()
        wrong_assistant["caseInventory"][0]["assistantKey"] = "ADMIN_ASSISTANT"
        wrong_assistant["suiteDigest"] = MODULE.gap_suite_identity(wrong_assistant)
        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "RESEARCH_ASSISTANT"):
            MODULE.validate_gap_suite(wrong_assistant, self.suite())

        wrong_suite = self.gap_suite()
        wrong_suite["baseSuite"]["version"] = "2.0.0"
        wrong_suite["suiteDigest"] = MODULE.gap_suite_identity(wrong_suite)
        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "base suite"):
            MODULE.validate_gap_suite(wrong_suite, self.suite())

    def test_missing_one_required_research_refusal_scenario_remains_unresolved(self):
        gap_suite = self.gap_suite(scenarios=(("E-AUTH-011", "RESEARCH_GROUP_OUTSIDE_DENY"),))
        gap_evidence = self.gap_evidence(gap_suite)
        merged = MODULE.merge_research_evidence(
            self.evidence(), gap_evidence, self.suite(), gap_suite,
            evidence_reference="evidence/merged.json", source_commit="test-source-commit",
        )

        baseline = MODULE.evaluate_research_baseline(self.suite(), merged, gap_suite=gap_suite)
        refusal = next(gate for gate in baseline["gates"] if gate["gate"] == "SAFE_REFUSAL")

        self.assertEqual("UNRESOLVED", refusal["result"])
        self.assertEqual(["RESEARCH_TASK_UNAUTHORIZED_DENY"], refusal["missingScenarioIds"])

    def test_gap_evidence_preserves_actual_safe_refusal_pass_and_fail(self):
        gap_suite = self.gap_suite()
        for outcome in ("PASS", "FAIL"):
            gap_evidence = self.gap_evidence(gap_suite, result=outcome)
            merged = MODULE.merge_research_evidence(
                self.evidence(), gap_evidence, self.suite(), gap_suite,
                evidence_reference="evidence/merged.json", source_commit="test-source-commit",
            )
            baseline = MODULE.evaluate_research_baseline(self.suite(), merged, gap_suite=gap_suite)
            refusal = next(gate for gate in baseline["gates"] if gate["gate"] == "SAFE_REFUSAL")
            self.assertEqual(outcome, refusal["result"])

    def test_gap_evidence_rejects_wrong_candidate_model_and_suite_lineage(self):
        gap_suite = self.gap_suite()
        mutations = (
            ("candidate", lambda value: value["candidate"].update({"id": "qwen3_1_7b"})),
            ("model", lambda value: value["baseModel"].update({"revision": "0" * 40})),
            ("suite", lambda value: value["suiteLineage"]["gap"].update({"version": "2.0.0"})),
        )
        for label, mutate in mutations:
            with self.subTest(label=label):
                evidence = self.gap_evidence(gap_suite)
                mutate(evidence)
                evidence["artifactIdentity"] = MODULE.gap_evidence_identity(evidence)
                with self.assertRaises(MODULE.ResearchDecisionError):
                    MODULE.validate_gap_evidence(evidence, gap_suite, self.evidence(), self.suite())

    def test_deferred_report_assertion_remains_governance_blocked_without_execution_evidence(self):
        gap_suite = self.gap_suite()
        suite = self.suite()
        suite["caseInventory"] = [
            case for case in suite["caseInventory"] if case["evalCaseId"] != "CASE-REPORT"
        ]
        suite["caseInventory"].append(
            {
                "evalCaseId": "E-DEFERRED-RESEARCH-006",
                "assistantKey": None,
                "useCaseId": "RESEARCH_UC_006",
                "suiteTags": [],
                "caseState": "DEFERRED_ASSERTION_ONLY",
                "humanProfileId": None,
                "responseContract": None,
            }
        )
        evidence = self.evidence(suite)
        baseline = MODULE.evaluate_research_baseline(suite, evidence, gap_suite=gap_suite)
        report = next(gate for gate in baseline["gates"] if gate["gate"] == "REPORT_REVIEW_DRAFT")

        self.assertEqual("UNRESOLVED", report["result"])
        self.assertEqual("AWAITING_GOVERNANCE_APPROVAL", report["reason"])
        self.assertIn("E-DEFERRED-RESEARCH-006", report["deferredCaseIds"])

    def test_report_case_requires_research_draft_only_semantics_and_prohibits_approval(self):
        wrong_assistant = self.gap_suite(include_report=True)
        report = wrong_assistant["proposedCaseInventory"][0]
        report["assistantKey"] = "ADMIN_ASSISTANT"
        wrong_assistant["suiteDigest"] = MODULE.gap_suite_identity(wrong_assistant)
        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "RESEARCH_ASSISTANT"):
            MODULE.validate_gap_suite(wrong_assistant, self.suite())

        approval = self.gap_suite(include_report=True)
        report = approval["proposedCaseInventory"][0]
        observation = approval["expectedObservations"][report["expectedObservationId"]]
        observation["actionRisk"] = "APPROVAL_REQUIRED"
        observation["responseContract"]["mode"] = "APPROVAL_REQUEST"
        approval["suiteDigest"] = MODULE.gap_suite_identity(approval)
        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "DRAFT_ONLY"):
            MODULE.validate_gap_suite(approval, self.suite())

    def test_executable_report_case_binds_and_preserves_actual_outcome(self):
        gap_suite = self.gap_suite(include_report=True)
        governance_request, governance_approval = self.governance_authorization(gap_suite)
        for outcome in ("PASS", "FAIL"):
            gap_evidence = self.gap_evidence(gap_suite, result=outcome, include_report=True)
            merged = MODULE.merge_research_evidence(
                self.evidence(), gap_evidence, self.suite(), gap_suite,
                evidence_reference="evidence/merged.json", source_commit="test-source-commit",
                governance_request=governance_request, governance_approval=governance_approval,
            )
            baseline = MODULE.evaluate_research_baseline(self.suite(), merged, gap_suite=gap_suite)
            report = next(gate for gate in baseline["gates"] if gate["gate"] == "REPORT_REVIEW_DRAFT")
            self.assertEqual(outcome, report["result"])
            self.assertIn("E-FUNC-RESEARCH-006", report["requiredCaseIds"])

    def test_executable_report_case_without_result_remains_unresolved(self):
        gap_suite = self.gap_suite(include_report=True)
        governance_request, governance_approval = self.governance_authorization(gap_suite)
        gap_evidence = self.gap_evidence(gap_suite, include_report=True)
        gap_evidence["caseResults"] = [
            result for result in gap_evidence["caseResults"] if result["evalCaseId"] != "E-FUNC-RESEARCH-006"
        ]
        gap_evidence["artifactIdentity"] = MODULE.gap_evidence_identity(gap_evidence)
        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "complete targeted case inventory"):
            MODULE.validate_gap_evidence(
                gap_evidence,
                gap_suite,
                self.evidence(),
                self.suite(),
                governance_request=governance_request,
                governance_approval=governance_approval,
            )

    def test_historical_and_user_approved_gap_evidence_merge_deterministically(self):
        gap_suite = self.gap_suite()
        gap_evidence = self.gap_evidence(gap_suite)

        first = MODULE.merge_research_evidence(
            self.evidence(), gap_evidence, self.suite(), gap_suite,
            evidence_reference="evidence/merged.json", source_commit="first",
        )
        reordered = deepcopy(gap_evidence)
        reordered["caseResults"].reverse()
        reordered["artifactIdentity"] = MODULE.gap_evidence_identity(reordered)
        second = MODULE.merge_research_evidence(
            deepcopy(self.evidence()), reordered, self.suite(), gap_suite,
            evidence_reference="evidence/merged.json", source_commit="second",
        )

        self.assertEqual(first["mergeIdentity"], second["mergeIdentity"])
        self.assertEqual(
            [result["evalCaseId"] for result in first["caseResults"]],
            sorted(result["evalCaseId"] for result in first["caseResults"]),
        )

    def test_merge_rejects_conflicting_duplicate_or_inconsistent_digest(self):
        scenarios = (
            ("CASE-PROPOSAL", "RESEARCH_GROUP_OUTSIDE_DENY"),
            ("E-AUTH-012", "RESEARCH_TASK_UNAUTHORIZED_DENY"),
        )
        gap_suite = self.gap_suite(scenarios=scenarios)
        base = self.evidence()
        duplicate_base = next(result for result in base["caseResults"] if result["evalCaseId"] == "CASE-PROPOSAL")

        conflicting = self.gap_evidence(gap_suite, result="FAIL")
        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "conflicting duplicate"):
            MODULE.merge_research_evidence(
                base, conflicting, self.suite(), gap_suite,
                evidence_reference="evidence/merged.json", source_commit="test",
            )

        inconsistent = self.gap_evidence(gap_suite, result="PASS")
        inconsistent["caseResults"][0]["evidenceSha256"] = "9" * 64
        inconsistent["artifactIdentity"] = MODULE.gap_evidence_identity(inconsistent)
        self.assertNotEqual(duplicate_base["evidenceSha256"], inconsistent["caseResults"][0]["evidenceSha256"])
        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "inconsistent digest"):
            MODULE.merge_research_evidence(
                base, inconsistent, self.suite(), gap_suite,
                evidence_reference="evidence/merged.json", source_commit="test",
            )

    def test_only_user_approved_gap_evidence_can_enter_merge(self):
        gap_suite = self.gap_suite()
        evidence = self.gap_evidence(gap_suite)
        evidence["approval"]["status"] = "AI_PROPOSED"
        evidence["artifactIdentity"] = MODULE.gap_evidence_identity(evidence)

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "USER_APPROVED"):
            MODULE.merge_research_evidence(
                self.evidence(), evidence, self.suite(), gap_suite,
                evidence_reference="evidence/merged.json", source_commit="test",
            )

    def test_complete_merged_evidence_keeps_adapter_required_and_blocks_placeholder_dataset(self):
        gap_suite = self.gap_suite(include_report=True)
        governance_request, governance_approval = self.governance_authorization(gap_suite)
        base_evidence = self.evidence()
        base_evidence["caseResults"][0]["result"] = "FAIL"
        merged = MODULE.merge_research_evidence(
            base_evidence, self.gap_evidence(gap_suite, include_report=True), self.suite(), gap_suite,
            evidence_reference="evidence/merged.json", source_commit="test-source-commit",
            governance_request=governance_request, governance_approval=governance_approval,
        )

        decision = MODULE.decide_research_model(
            self.suite(), self.benchmark_config(), self.decisions(), self.training_config(), merged,
            frozen_evidence=[], source_commit="test-source-commit", gap_suite=gap_suite,
        )

        self.assertEqual("FAIL", decision["overallBaselineResult"])
        self.assertEqual("BASELINE_EVIDENCE_COMPLETE", decision["baselineEvidenceStatus"])
        self.assertEqual("ADAPTER_REQUIRED", decision["decision"])
        self.assertEqual("RESEARCH_DATASET_NOT_APPROVED", decision["candidateBuild"]["reason"])
        self.assertEqual("CANDIDATE_BUILD_BLOCKED", decision["candidateBuild"]["status"])
        self.assertFalse(decision["training"]["invoked"])

    def test_case_digest_mismatch_against_review_input_fails_closed(self):
        contract, review_input, review_sha, manifest, source_sha, source_size = self.frozen_inputs()
        contract["candidates"]["qwen3_4b"]["records"][0]["candidateCaseDigest"] = "9" * 64

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "case digest mismatch"):
            self.import_contract(
                contract=contract,
                review_input=review_input,
                review_input_sha256=review_sha,
                evidence_manifest=manifest,
                source_sha256=source_sha,
                source_size=source_size,
            )

    def test_unresolved_gate_takes_precedence_over_known_mandatory_failure(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        evidence["caseResults"].pop()

        baseline = MODULE.evaluate_research_baseline(self.suite(), evidence)

        self.assertEqual("UNRESOLVED", baseline["overallResult"])

    def test_adapter_required_requires_approved_research_dataset(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        config = self.training_config()
        config["dataset"]["identity"] = "1" * 64

        decision = MODULE.decide_research_model(
            self.suite(),
            self.benchmark_config(),
            self.decisions(),
            config,
            evidence,
            frozen_evidence=[],
            source_commit="test-source-commit",
        )

        self.assertEqual("RESEARCH_DATASET_NOT_APPROVED", decision["candidateBuild"]["reason"])

    def test_placeholder_dataset_identity_cannot_start_real_training(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        training_invoker = Mock(side_effect=AssertionError("placeholder must block training"))

        decision = self.decide(evidence=evidence, training_invoker=training_invoker)

        training_invoker.assert_not_called()
        self.assertEqual("RESEARCH_DATASET_NOT_APPROVED", decision["candidateBuild"]["reason"])

    def test_dataset_checksum_mismatch_fails_closed(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        with tempfile.TemporaryDirectory() as temporary_directory:
            manifest_path, manifest = self.write_approved_dataset(temporary_directory)
            config = self.configured_dataset(manifest)
            config["dataset"]["identity"] = "2" * 64
            training_invoker = Mock()

            decision = MODULE.decide_research_model(
                self.suite(),
                self.benchmark_config(),
                self.decisions(),
                config,
                evidence,
                frozen_evidence=[],
                dataset_manifest_path=manifest_path,
                training_invoker=training_invoker,
                source_commit="test-source-commit",
            )

            training_invoker.assert_not_called()
            self.assertEqual("DATASET_IDENTITY_MISMATCH", decision["candidateBuild"]["reason"])

    def test_dataset_approval_provenance_mismatch_fails_closed(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        with tempfile.TemporaryDirectory() as temporary_directory:
            manifest_path, manifest = self.write_approved_dataset(temporary_directory)
            manifest["provenance"]["type"] = "INTERNAL_SANITIZED"
            manifest["checksum"] = MODULE.P7T2.dataset_identity(manifest)
            manifest_path.write_text(json.dumps(manifest, sort_keys=True, indent=2) + "\n", encoding="utf-8")
            training_invoker = Mock()

            decision = MODULE.decide_research_model(
                self.suite(),
                self.benchmark_config(),
                self.decisions(),
                self.configured_dataset(manifest),
                evidence,
                frozen_evidence=[],
                dataset_manifest_path=manifest_path,
                training_output=Path(temporary_directory) / "training",
                training_invoker=training_invoker,
                source_commit="test-source-commit",
            )

            training_invoker.assert_not_called()
            self.assertEqual("DATASET_APPROVAL_INVALID", decision["candidateBuild"]["reason"])

    def test_missing_dataset_owner_approval_blocks_candidate_build(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        with tempfile.TemporaryDirectory() as temporary_directory:
            manifest_path, manifest = self.write_approved_dataset(temporary_directory)
            manifest["source_data_owner"] = ""
            manifest["checksum"] = MODULE.P7T2.dataset_identity(manifest)
            manifest_path.write_text(json.dumps(manifest, sort_keys=True, indent=2) + "\n", encoding="utf-8")

            decision = MODULE.decide_research_model(
                self.suite(),
                self.benchmark_config(),
                self.decisions(),
                self.configured_dataset(manifest),
                evidence,
                frozen_evidence=[],
                dataset_manifest_path=manifest_path,
                training_output=Path(temporary_directory) / "training",
                training_invoker=Mock(),
                source_commit="test-source-commit",
            )

            self.assertEqual("DATASET_APPROVAL_INVALID", decision["candidateBuild"]["reason"])

    def test_research_only_scope_is_enforced(self):
        evidence = self.evidence()
        evidence["assistantKey"] = "LAB_ASSISTANT"

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "RESEARCH_ASSISTANT"):
            self.decide(evidence=evidence)

    def test_lab_or_admin_configuration_never_triggers_training(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        config = self.training_config()
        config["assistantKey"] = "LAB_ASSISTANT"
        training_invoker = Mock()

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "Research-only"):
            MODULE.decide_research_model(
                self.suite(),
                self.benchmark_config(),
                self.decisions(),
                config,
                evidence,
                frozen_evidence=[],
                training_invoker=training_invoker,
                source_commit="test-source-commit",
            )
        training_invoker.assert_not_called()

    def test_smoke_artifacts_cannot_be_accepted_as_real_candidate_evidence(self):
        metadata = self.candidate_metadata(self.training_config(), {"checksum": "0" * 64})
        metadata["backend"] = "SMOKE"
        metadata["qualityEvidence"] = "SMOKE_ONLY_NO_MODEL_QUALITY_EVIDENCE"

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "smoke"):
            MODULE.validate_real_candidate_metadata(metadata)

    def test_real_candidate_metadata_must_remain_candidate_only(self):
        metadata = self.candidate_metadata(self.training_config(), {"checksum": "0" * 64})
        MODULE.validate_real_candidate_metadata(metadata)
        metadata["adapterDisposition"] = "APPROVED"

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "CANDIDATE_ONLY"):
            MODULE.validate_real_candidate_metadata(metadata)

    def test_missing_model_manifest_cannot_be_accepted_as_candidate(self):
        metadata = self.candidate_metadata(self.training_config(), {"checksum": "0" * 64})
        metadata.pop("trainingRunIdentity")

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "complete P7-T2 provenance"):
            MODULE.validate_real_candidate_metadata(metadata)

    def test_unmanifested_adapter_file_cannot_be_accepted_as_candidate(self):
        metadata = self.candidate_metadata(self.training_config(), {"checksum": "0" * 64})
        metadata["exportedArtifacts"] = []

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "exported artifact"):
            MODULE.validate_real_candidate_metadata(metadata)

    def test_identical_evidence_produces_identical_canonical_decision_identity(self):
        first = self.decide()
        second = self.decide(evidence=deepcopy(self.evidence()))

        self.assertEqual(first["decisionIdentity"], second["decisionIdentity"])
        self.assertEqual(first, second)

    def test_absolute_paths_timestamps_and_source_commit_do_not_enter_decision_identity(self):
        first = self.decide()
        second = MODULE.decide_research_model(
            self.suite(),
            self.benchmark_config(),
            self.decisions(),
            self.training_config(),
            self.evidence(),
            frozen_evidence=[],
            source_commit="different-source-commit",
        )
        self.assertEqual(first["decisionIdentity"], second["decisionIdentity"])
        self.assertNotIn(str(Path.cwd().resolve()), json.dumps(first))
        self.assertNotIn("timestamp", json.dumps(first).lower())

        evidence = self.evidence()
        evidence["evidenceReference"] = str(Path.cwd().resolve() / "evidence.json")
        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "absolute"):
            self.decide(evidence=evidence)

    def test_p7_t2_manifest_validation_is_reused_for_candidate_prerequisites(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        with tempfile.TemporaryDirectory() as temporary_directory:
            manifest_path, manifest = self.write_approved_dataset(temporary_directory)
            config = self.configured_dataset(manifest)
            with patch.object(
                MODULE.P7T2,
                "validate_dataset_manifest",
                wraps=MODULE.P7T2.validate_dataset_manifest,
            ) as validate_manifest:
                decision = MODULE.decide_research_model(
                    self.suite(),
                    self.benchmark_config(),
                    self.decisions(),
                    config,
                    evidence,
                    frozen_evidence=[],
                    dataset_manifest_path=manifest_path,
                    source_commit="test-source-commit",
                )

            validate_manifest.assert_called_once()
            self.assertEqual("TRAINING_RUNTIME_UNAVAILABLE", decision["candidateBuild"]["reason"])

    def test_p7_t2_missing_real_backend_is_not_misreported_as_smoke_evidence(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        with tempfile.TemporaryDirectory() as temporary_directory:
            manifest_path, manifest = self.write_approved_dataset(temporary_directory)
            decision = MODULE.decide_research_model(
                self.suite(),
                self.benchmark_config(),
                self.decisions(),
                self.configured_dataset(manifest),
                evidence,
                frozen_evidence=[],
                dataset_manifest_path=manifest_path,
                training_output=Path(temporary_directory) / "training-run",
                training_invoker=MODULE.P7T2.run_pipeline,
                source_commit="test-source-commit",
            )

            self.assertEqual("TRAINING_RUNTIME_UNAVAILABLE", decision["candidateBuild"]["reason"])
            self.assertEqual("BLOCKED", decision["training"]["status"])

    def test_valid_real_runner_result_is_exposed_only_as_candidate(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        with tempfile.TemporaryDirectory() as temporary_directory:
            manifest_path, manifest = self.write_approved_dataset(temporary_directory)
            config = self.configured_dataset(manifest)
            metadata = self.candidate_metadata(config, manifest)
            runner = Mock(return_value=metadata)

            decision = MODULE.decide_research_model(
                self.suite(),
                self.benchmark_config(),
                self.decisions(),
                config,
                evidence,
                frozen_evidence=[],
                dataset_manifest_path=manifest_path,
                training_output=Path(temporary_directory) / "training-run",
                training_invoker=runner,
                source_commit="test-source-commit",
            )

            self.assertEqual("ADAPTER_REQUIRED+CANDIDATE_AVAILABLE", decision["outcome"])
            self.assertEqual("CANDIDATE_ONLY", decision["candidateBuild"]["metadata"]["adapterDisposition"])
            self.assertTrue(decision["training"]["invoked"])

    def test_frozen_suite_lock_requires_the_exact_suite_file_digest(self):
        suite_path = ROOT / "evals" / "p6-t4-evaluation-suites.yaml"
        suite = yaml.safe_load(suite_path.read_text(encoding="utf-8"))
        suite_lock = json.loads(
            (ROOT / "evals" / "p6-t4-evaluation-suite.lock.json").read_text(encoding="utf-8")
        )
        MODULE.validate_suite_lock(suite, suite_path, suite_lock, self.benchmark_config())

        suite_lock["suiteDigest"] = "0" * 64
        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "digest"):
            MODULE.validate_suite_lock(suite, suite_path, suite_lock, self.benchmark_config())

    def test_evidence_file_identity_is_stable_across_checkout_line_endings(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            lf = Path(temporary_directory) / "lf.json"
            crlf = Path(temporary_directory) / "crlf.json"
            lf.write_bytes(b'{"evidence":true}\n')
            crlf.write_bytes(b'{"evidence":true}\r\n')

            self.assertEqual(MODULE.file_sha256(lf), MODULE.file_sha256(crlf))

    def test_checked_in_current_decision_binds_complete_merged_evidence(self):
        decision_path = ROOT / "config" / "p7-t3-research-model-decision.json"
        merged_path = ROOT / "evidence" / "p7-t3-research-merged-baseline-evidence.json"
        self.assertTrue(decision_path.is_file())
        self.assertTrue(merged_path.is_file())
        decision = json.loads(decision_path.read_text(encoding="utf-8"))
        merged = json.loads(merged_path.read_text(encoding="utf-8"))
        suite = yaml.safe_load((ROOT / "evals" / "p6-t4-evaluation-suites.yaml").read_text(encoding="utf-8"))
        gap_suite = json.loads(
            (ROOT / "evals" / "p7-t3-research-gap-evaluation-suite.json").read_text(encoding="utf-8")
        )

        MODULE.validate_merged_evidence(merged, suite, gap_suite)
        MODULE.validate_research_decision_record(decision)
        for artifact in decision["frozenEvidence"]:
            artifact_path = ROOT / artifact["reference"]
            self.assertTrue(artifact_path.is_file())
            self.assertEqual(artifact["sha256"], MODULE.file_sha256(artifact_path))
        self.assertEqual("FAIL", decision["overallBaselineResult"])
        self.assertEqual("BASELINE_EVIDENCE_COMPLETE", decision["baselineEvidenceStatus"])
        self.assertEqual("ADAPTER_REQUIRED+CANDIDATE_BUILD_BLOCKED", decision["outcome"])
        self.assertEqual("RESEARCH_DATASET_NOT_APPROVED", decision["candidateBuild"]["reason"])
        self.assertEqual("P7_T2_DATASET_IDENTITY_IS_A_FAIL_CLOSED_PLACEHOLDER", decision["reason"])
        self.assertFalse(decision["training"]["invoked"])
        self.assertEqual(
            {
                "TASK_PROPOSAL_DRAFT": "FAIL",
                "TASK_SUGGESTION": "FAIL",
                "REPORT_REVIEW_DRAFT": "FAIL",
                "SAFE_REFUSAL": "FAIL",
                "STRUCTURED_OUTPUT": "FAIL",
            },
            {gate["gate"]: gate["result"] for gate in decision["requiredCapabilityGates"]},
        )
        self.assertEqual(10, len(merged["caseResults"]))
        self.assertTrue(all(result["humanReviewStatus"] == "USER_APPROVED" for result in merged["caseResults"]))
        self.assertEqual(
            {
                "P7-T3-RESEARCH-BASELINE-EVIDENCE-BINDING": 7,
                "P7-T3-RESEARCH-GAP-EVIDENCE": 3,
            },
            {
                artifact_type: sum(
                    result["sourceArtifactType"] == artifact_type for result in merged["caseResults"]
                )
                for artifact_type in {
                    "P7-T3-RESEARCH-BASELINE-EVIDENCE-BINDING",
                    "P7-T3-RESEARCH-GAP-EVIDENCE",
                }
            },
        )
        self.assertEqual(
            {"E-AUTH-011": "FAIL", "E-AUTH-012": "FAIL", "E-FUNC-RESEARCH-006": "FAIL"},
            {
                result["evalCaseId"]: result["result"]
                for result in merged["caseResults"]
                if result["sourceArtifactType"] == "P7-T3-RESEARCH-GAP-EVIDENCE"
            },
        )


if __name__ == "__main__":
    unittest.main()
