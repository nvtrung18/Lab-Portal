import importlib.util
import json
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "p7t4",
    ROOT / "scripts" / "research-independent-evaluation-p7-t4.py",
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)
V6_SPEC = importlib.util.spec_from_file_location(
    "p7t4_v6",
    ROOT / "scripts" / "research-independent-evaluation-p7-t4-v6.py",
)
V6_MODULE = importlib.util.module_from_spec(V6_SPEC)
assert V6_SPEC.loader is not None
V6_SPEC.loader.exec_module(V6_MODULE)
V7_SPEC = importlib.util.spec_from_file_location(
    "p7t4_v7",
    ROOT / "scripts" / "research-independent-evaluation-p7-t4-v7.py",
)
V7_MODULE = importlib.util.module_from_spec(V7_SPEC)
assert V7_SPEC.loader is not None
V7_SPEC.loader.exec_module(V7_MODULE)


class P7T4ResearchIndependentEvaluationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base_suite = yaml.safe_load(
            (ROOT / "evals" / "p6-t4-evaluation-suites.yaml").read_text(encoding="utf-8")
        )
        cls.gap_suite = json.loads(
            (ROOT / "evals" / "p7-t3-research-gap-evaluation-suite.json").read_text(
                encoding="utf-8"
            )
        )
        cls.governance_request = json.loads(
            (ROOT / "config" / "p7-t3-research-report-eval-governance-request.json").read_text(
                encoding="utf-8"
            )
        )
        cls.governance_approval = json.loads(
            (ROOT / "evidence" / "p7-t3-research-report-eval-governance-approval-v2.json").read_text(
                encoding="utf-8"
            )
        )

    def evaluation_suite(self):
        return MODULE.compose_research_evaluation_suite(
            deepcopy(self.base_suite),
            deepcopy(self.gap_suite),
            deepcopy(self.governance_request),
            deepcopy(self.governance_approval),
        )

    def run_artifact(self, suite, variant, repetition, *, failed=None, findings=None):
        failed = set(failed or [])
        automatic_report = [
            {
                "evalCaseId": case["evalCaseId"],
                "candidateCaseDigest": MODULE.sha256_bytes(case["evalCaseId"].encode()),
                "automaticState": "FAIL" if case["evalCaseId"] in failed else "PASS",
            }
            for case in suite["caseInventory"]
        ]
        value = {
            "artifactType": "P7-T4-RESEARCH-INDEPENDENT-EVALUATION-RUN",
            "schemaVersion": "1.0.0",
            "state": "COMPLETE",
            "modelVariant": variant,
            "repetition": repetition,
            "suite": MODULE.suite_binding(suite),
            "candidateRun": {
                "suiteId": suite["suiteId"],
                "suiteVersion": suite["suiteVersion"],
                "candidateRunId": f"{variant}-{repetition}",
                "modelMetadata": {"variant": variant},
                "cases": [
                    {
                        "evalCaseId": case["evalCaseId"],
                        "response": None,
                        "observedBehavior": "TEST",
                        "observedActionRisk": "TEST",
                        "toolRequest": None,
                        "structuredOutput": None,
                        "referencedContextIds": [],
                    }
                    for case in suite["caseInventory"]
                ],
            },
            "automatic": {
                "suiteId": suite["suiteId"],
                "suiteVersion": suite["suiteVersion"],
                "candidateRunId": f"{variant}-{repetition}",
                "automaticReport": automatic_report,
            },
            "promptManifest": [
                {
                    "evalCaseId": case["evalCaseId"],
                    "sourceAssistantKey": case["assistantKey"],
                    "executionAssistantKey": "RESEARCH_ASSISTANT",
                    "promptDigest": MODULE.sha256_bytes(case["evalCaseId"].encode()),
                }
                for case in suite["caseInventory"]
            ],
            "rawOutputs": [
                {
                    "evalCaseId": case["evalCaseId"],
                    "rawText": "{}",
                    "rawTextDigest": MODULE.sha256_bytes(b"{}"),
                }
                for case in suite["caseInventory"]
            ],
            "findings": sorted(findings or []),
            "metrics": {
                "generationLatencyNs": [100, 200],
                "peakVramBytes": 1_000,
                "peakRssBytes": 2_000,
            },
            "sourceCommit": "a" * 40,
        }
        value["artifactIdentity"] = MODULE.artifact_identity(value)
        return value

    def test_composed_suite_uses_research_profile_and_governed_frozen_cases(self):
        suite = self.evaluation_suite()

        self.assertEqual("P7-T4-RESEARCH-INDEPENDENT-EVALUATION", suite["suiteId"])
        self.assertEqual("1.0.0", suite["suiteVersion"])
        self.assertTrue(suite["EVALUATION_ONLY"])
        self.assertTrue(suite["TRAINING_PROHIBITED"])
        self.assertEqual(18, len(suite["caseInventory"]))
        self.assertEqual("RESEARCH_ASSISTANT", suite["executionProfile"]["assistantKey"])
        self.assertEqual(
            ["E-INJECT-001", "E-INJECT-002", "E-INJECT-003"],
            suite["executionProfile"]["frozenProfileOverrideCaseIds"],
        )
        report = next(
            case for case in suite["caseInventory"] if case["evalCaseId"] == "E-FUNC-RESEARCH-006"
        )
        self.assertEqual("ACTIVE", report["caseState"])
        self.assertEqual(
            self.governance_approval["artifactIdentity"],
            suite["governance"]["reportEvaluationApprovalIdentity"],
        )

    def test_composed_suite_rejects_unapproved_report_case(self):
        approval = deepcopy(self.governance_approval)
        approval["status"] = "PENDING"

        with self.assertRaisesRegex(MODULE.P7T4Error, "governance"):
            MODULE.compose_research_evaluation_suite(
                deepcopy(self.base_suite),
                deepcopy(self.gap_suite),
                deepcopy(self.governance_request),
                approval,
            )

    def test_comparison_fails_closed_for_adapter_regressions_and_failed_cases(self):
        suite = self.evaluation_suite()
        prompt_case = next(
            case["evalCaseId"]
            for case in suite["caseInventory"]
            if "PROMPT_INJECTION" in case["suiteTags"]
        )
        reference_case = suite["caseInventory"][0]["evalCaseId"]
        base_runs = [self.run_artifact(suite, "SHARED_BASE", repetition) for repetition in MODULE.REPETITIONS]
        adapter_runs = [
            self.run_artifact(
                suite,
                "RESEARCH_ADAPTER",
                repetition,
                failed={prompt_case, reference_case},
                findings={f"EVAL-FORBIDDEN-REFERENCE ({reference_case})"},
            )
            for repetition in MODULE.REPETITIONS
        ]

        comparison = MODULE.compare_model_runs(suite, base_runs, adapter_runs)

        self.assertEqual("AUTOMATIC_FAIL", comparison["automaticDecision"])
        self.assertIn(prompt_case, comparison["regressions"]["promptInjection"])
        self.assertIn(reference_case, comparison["regressions"]["hallucinatedIds"])
        self.assertEqual(sorted([prompt_case, reference_case]), comparison["adapterFailedCaseIds"])
        self.assertEqual("AWAITING_INDEPENDENT_REVIEW", comparison["state"])

    def test_comparison_passes_automatic_gate_only_when_every_adapter_case_passes(self):
        suite = self.evaluation_suite()
        base_failed = {suite["caseInventory"][0]["evalCaseId"]}
        base_runs = [
            self.run_artifact(suite, "SHARED_BASE", repetition, failed=base_failed)
            for repetition in MODULE.REPETITIONS
        ]
        adapter_runs = [self.run_artifact(suite, "RESEARCH_ADAPTER", repetition) for repetition in MODULE.REPETITIONS]

        comparison = MODULE.compare_model_runs(suite, base_runs, adapter_runs)

        self.assertEqual("AUTOMATIC_PASS", comparison["automaticDecision"])
        self.assertEqual([], comparison["adapterFailedCaseIds"])
        self.assertEqual(sorted(base_failed), comparison["improvedCaseIds"])
        self.assertEqual("AWAITING_INDEPENDENT_REVIEW", comparison["state"])

    def test_comparison_rejects_incomplete_or_mismatched_repetitions(self):
        suite = self.evaluation_suite()
        base_runs = [self.run_artifact(suite, "SHARED_BASE", repetition) for repetition in MODULE.REPETITIONS]
        adapter_runs = [
            self.run_artifact(suite, "RESEARCH_ADAPTER", repetition) for repetition in MODULE.REPETITIONS[:-1]
        ]

        with self.assertRaisesRegex(MODULE.P7T4Error, "repetitions"):
            MODULE.compare_model_runs(suite, base_runs, adapter_runs)

    def adapter_fixture(self, directory):
        adapter_directory = Path(directory) / "adapter"
        adapter_directory.mkdir()
        (adapter_directory / "adapter_config.json").write_bytes(b'{"peft_type":"LORA"}\n')
        (adapter_directory / "adapter_model.safetensors").write_bytes(b"synthetic-adapter")
        artifacts = []
        for path in sorted(adapter_directory.iterdir()):
            artifacts.append(
                {
                    "filename": path.name,
                    "sha256": MODULE.sha256_bytes(path.read_bytes()),
                    "size": path.stat().st_size,
                }
            )
        training_run_identity = "1" * 64
        adapter_identity = MODULE.sha256_bytes(MODULE.canonical_bytes(artifacts))
        candidate_id = MODULE.sha256_bytes(
            MODULE.canonical_bytes(
                {
                    "trainingRunIdentity": training_run_identity,
                    "adapterIdentity": adapter_identity,
                }
            )
        )
        manifest = {
            "schemaVersion": "1.0.0",
            "pipelineVersion": "1.0.0",
            "backend": "REAL_QLORA",
            "realTraining": True,
            "adapterDisposition": "CANDIDATE_ONLY",
            "qualityEvidence": "REAL_TRAINING_EXECUTION",
            "assistantKey": "RESEARCH_ASSISTANT",
            "baseModel": {
                "identifier": "Qwen/Qwen3-4B-Instruct-2507",
                "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
            },
            "datasetIdentity": "2" * 64,
            "trainingConfigIdentity": "3" * 64,
            "trainingRunIdentity": training_run_identity,
            "candidateId": candidate_id,
            "adapterIdentity": adapter_identity,
            "seed": 20260821,
            "sourceCommit": "a" * 40,
            "artifacts": artifacts,
        }
        return adapter_directory, manifest

    def test_adapter_candidate_validates_exact_inventory_and_candidate_identity(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            adapter_directory, manifest = self.adapter_fixture(temporary_directory)

            result = MODULE.validate_adapter_candidate(adapter_directory, manifest)

        self.assertEqual(manifest["candidateId"], result["candidateId"])
        self.assertEqual(manifest["adapterIdentity"], result["adapterIdentity"])

    def test_adapter_candidate_rejects_payload_tampering(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            adapter_directory, manifest = self.adapter_fixture(temporary_directory)
            (adapter_directory / "adapter_model.safetensors").write_bytes(b"tampered")

            with self.assertRaisesRegex(MODULE.P7T4Error, "inventory"):
                MODULE.validate_adapter_candidate(adapter_directory, manifest)

    def test_checked_in_config_binds_the_real_candidate_and_review_policy(self):
        config = json.loads(
            (ROOT / "config" / "p7-t4-research-independent-evaluation.json").read_text(
                encoding="utf-8"
            )
        )
        manifest = json.loads(
            (ROOT / "evidence" / "p7-t2-real-training" / "adapter-manifest.json").read_text(
                encoding="utf-8"
            )
        )

        MODULE.validate_evaluation_config(config, manifest)

        self.assertEqual(list(MODULE.REPETITIONS), config["execution"]["repetitions"])
        self.assertTrue(config["review"]["humanEvaluationRequired"])
        self.assertTrue(config["review"]["independentReviewerRequired"])
        self.assertIsNone(config["comparisonPolicy"]["resourceIncreaseThreshold"])

    def test_v2_contract_loads_approved_suite_evaluator_and_execution_approval(self):
        config = json.loads(
            (
                ROOT
                / "config/p7-t4-research-independent-evaluation-remediation-v5.json"
            ).read_text(encoding="utf-8")
        )
        manifest = json.loads(
            (
                ROOT
                / "evidence/p7-t2-real-training/remediation-v5/adapter-manifest.json"
            ).read_text(encoding="utf-8")
        )

        MODULE.validate_evaluation_config(config, manifest)
        suite, evaluator, execution_approval = MODULE.load_v2_evaluation_contract(
            ROOT,
            config,
            json.loads(
                (ROOT / "evals/p6-t4-evaluation-suite.lock.json").read_text(
                    encoding="utf-8"
                )
            ),
            self.gap_suite,
        )

        self.assertEqual("2.0.0", suite["suiteVersion"])
        self.assertEqual("2.0.0", evaluator.EVALUATOR_VERSION)
        self.assertTrue(
            execution_approval["authorization"]["externalEvaluationExecutionAllowed"]
        )
        self.assertFalse(execution_approval["authorization"]["promotionAllowed"])

    def test_v6_contract_loads_approved_prompt_profile_for_evaluation_only(self):
        config = json.loads(
            (
                ROOT
                / "config/p7-t4-research-independent-evaluation-remediation-v6.json"
            ).read_text(encoding="utf-8")
        )
        manifest = json.loads(
            (
                ROOT
                / "evidence/p7-t2-real-training/remediation-v6/adapter-manifest.json"
            ).read_text(encoding="utf-8")
        )

        V6_MODULE.validate_evaluation_config(config, manifest)
        _, _, execution_approval = V6_MODULE.load_v2_evaluation_contract(
            ROOT,
            config,
            json.loads(
                (ROOT / "evals/p6-t4-evaluation-suite.lock.json").read_text(
                    encoding="utf-8"
                )
            ),
            self.gap_suite,
        )

        self.assertTrue(
            execution_approval["authorization"][
                "promptProfileV3EvaluationUseAllowed"
            ]
        )
        self.assertFalse(
            execution_approval["authorization"]["productionPromptingAllowed"]
        )

    def test_v7_contract_loads_exact_candidate_evaluation_approval(self):
        config = json.loads(
            (
                ROOT
                / "config/p7-t4-research-independent-evaluation-remediation-v7.json"
            ).read_text(encoding="utf-8")
        )
        manifest = json.loads(
            (
                ROOT
                / "evidence/p7-t2-real-training/remediation-v7/adapter-manifest.json"
            ).read_text(encoding="utf-8")
        )

        V7_MODULE.validate_evaluation_config(config, manifest)
        _, _, execution_approval = V7_MODULE.load_v2_evaluation_contract(
            ROOT,
            config,
            json.loads(
                (ROOT / "evals/p6-t4-evaluation-suite.lock.json").read_text(
                    encoding="utf-8"
                )
            ),
            self.gap_suite,
        )

        self.assertEqual(
            "b2e61ccba5e79dde268d5cb96e1426fbd0d42136a0b8cdfbd1f4a414e523a9e0",
            execution_approval["approvedCandidate"]["candidateId"],
        )
        self.assertTrue(
            execution_approval["authorization"]["externalEvaluationExecutionAllowed"]
        )
        self.assertFalse(execution_approval["authorization"]["promotionAllowed"])

    def test_v2_evaluator_accepts_the_approved_report_review_draft(self):
        suite = json.loads(
            (
                ROOT
                / "config/p7-t4-research-remediation-governance-v5/evaluation-suite-v2.approved.json"
            ).read_text(encoding="utf-8")
        )
        cases = []
        for case in suite["caseInventory"]:
            expected = suite["expectedObservations"][case["expectedObservationId"]]
            cases.append(
                {
                    "evalCaseId": case["evalCaseId"],
                    "response": {
                        "language": expected["responseContract"]["language"],
                        "markers": expected["responseContract"]["markers"],
                        "mode": expected["responseContract"]["mode"],
                        "text": "Synthetic governed response.",
                    },
                    "observedBehavior": expected["behavior"],
                    "observedActionRisk": expected["actionRisk"],
                    "toolRequest": expected["toolRequest"],
                    "structuredOutput": expected["structuredOutput"],
                    "referencedContextIds": expected["referencedContextIds"],
                }
            )
        candidate = {
            "suiteId": suite["suiteId"],
            "suiteVersion": suite["suiteVersion"],
            "candidateRunId": "v2-contract-test",
            "modelMetadata": {},
            "cases": cases,
        }

        evaluator = MODULE.evaluator_for_suite(ROOT, suite)
        findings, report = evaluator.score_candidate(suite, candidate)

        self.assertEqual([], findings)
        self.assertTrue(
            all(item["automaticState"] == "PASS" for item in report["automaticReport"])
        )

    def completed_human_report(self, suite, run, outcome="PASS"):
        rubric = yaml.safe_load(
            (ROOT / "evals" / "human-eval-rubric.yaml").read_text(encoding="utf-8")
        )
        applicable = {
            case_id: profile
            for profile, case_ids in suite["matrices"]["humanApplicabilityBinding"].items()
            if profile != "NONE"
            for case_id in case_ids
        }
        candidate_cases = {
            case["evalCaseId"]: case for case in run["candidateRun"]["cases"]
        }
        records = []
        for case_id in sorted(applicable):
            profile = applicable[case_id]
            dimensions = [
                {
                    "dimension": dimension,
                    "outcome": outcome,
                    "rationale": "Independent synthetic test review.",
                    "evidenceRefs": [f"evalCaseId:{case_id}"],
                }
                for dimension in rubric["profiles"][profile]["dimensions"]
            ]
            records.append(
                {
                    "evalCaseId": case_id,
                    "candidateCaseDigest": MODULE.sha256_bytes(
                        MODULE.canonical_bytes(candidate_cases[case_id])
                    ),
                    "profileId": profile,
                    "dimensions": dimensions,
                    "overall": outcome,
                    "reviewerRationale": "Independent synthetic test review.",
                    "evidenceRefs": [f"evalCaseId:{case_id}"],
                }
            )
        review = {
            "suiteId": suite["suiteId"],
            "suiteVersion": suite["suiteVersion"],
            "candidateRunId": run["candidateRun"]["candidateRunId"],
            "rubricVersion": rubric["rubricVersion"],
            "records": records,
        }
        sidecar = {
            "candidateOutputDigest": MODULE.sha256_bytes(
                MODULE.canonical_bytes(run["candidateRun"])
            ),
            "review": review,
        }
        return MODULE.validate_human_review(suite, run, sidecar, rubric)

    def test_human_packet_is_bound_to_r01_output_without_synthesized_outcomes(self):
        suite = self.evaluation_suite()
        run = self.run_artifact(suite, "RESEARCH_ADAPTER", "R01")

        packet = MODULE.build_human_review_packet(suite, run)

        self.assertEqual("PENDING_HUMAN_REVIEW", packet["humanReview"]["humanReviewState"])
        self.assertEqual([], packet["humanReview"]["records"])
        self.assertTrue(packet["sidecarTemplate"]["review"]["records"])
        self.assertTrue(
            all(
                record["overall"] is None
                and all(dimension["outcome"] is None for dimension in record["dimensions"])
                for record in packet["sidecarTemplate"]["review"]["records"]
            )
        )
        self.assertEqual(
            MODULE.sha256_bytes(MODULE.canonical_bytes(run["candidateRun"])),
            packet["candidateOutputDigest"],
        )

    def test_independent_review_template_is_bound_but_contains_no_decision(self):
        suite = self.evaluation_suite()
        base_runs = [
            self.run_artifact(suite, "SHARED_BASE", repetition)
            for repetition in MODULE.REPETITIONS
        ]
        adapter_runs = [
            self.run_artifact(suite, "RESEARCH_ADAPTER", repetition)
            for repetition in MODULE.REPETITIONS
        ]
        comparison = MODULE.compare_model_runs(suite, base_runs, adapter_runs)

        template = MODULE.build_independent_review_template(comparison)

        self.assertEqual(comparison["artifactIdentity"], template["comparisonIdentity"])
        self.assertIsNone(template["decision"])
        self.assertIsNone(template["resourceUseAccepted"])

    def test_finalization_allows_pass_only_after_human_and_independent_review(self):
        suite = self.evaluation_suite()
        base_runs = [
            self.run_artifact(suite, "SHARED_BASE", repetition)
            for repetition in MODULE.REPETITIONS
        ]
        adapter_runs = [
            self.run_artifact(suite, "RESEARCH_ADAPTER", repetition)
            for repetition in MODULE.REPETITIONS
        ]
        comparison = MODULE.compare_model_runs(suite, base_runs, adapter_runs)
        rubric = yaml.safe_load(
            (ROOT / "evals" / "human-eval-rubric.yaml").read_text(encoding="utf-8")
        )
        base_human = self.completed_human_report(suite, base_runs[0])
        adapter_human = self.completed_human_report(suite, adapter_runs[0])
        reviewer = {
            "artifactType": "P7-T4-INDEPENDENT-REVIEW",
            "schemaVersion": "1.0.0",
            "comparisonIdentity": comparison["artifactIdentity"],
            "reviewerId": "independent-reviewer-test",
            "independentFromTraining": True,
            "reviewedAt": "2026-08-24T00:00:00Z",
            "decision": "PASS",
            "resourceUseAccepted": True,
            "rationale": "Automatic and human evidence reviewed for the synthetic test.",
        }

        finalized = MODULE.finalize_comparison(
            comparison, base_human, adapter_human, reviewer, rubric
        )

        self.assertEqual("PASS", finalized["evaluationDecision"])
        self.assertTrue(finalized["promotionAllowed"])

    def test_finalization_stays_open_when_human_review_needs_resolution(self):
        suite = self.evaluation_suite()
        base_runs = [
            self.run_artifact(suite, "SHARED_BASE", repetition)
            for repetition in MODULE.REPETITIONS
        ]
        adapter_runs = [
            self.run_artifact(suite, "RESEARCH_ADAPTER", repetition)
            for repetition in MODULE.REPETITIONS
        ]
        comparison = MODULE.compare_model_runs(suite, base_runs, adapter_runs)
        rubric = yaml.safe_load(
            (ROOT / "evals" / "human-eval-rubric.yaml").read_text(encoding="utf-8")
        )
        base_human = self.completed_human_report(suite, base_runs[0])
        adapter_human = self.completed_human_report(
            suite, adapter_runs[0], outcome="NEEDS_REVIEW"
        )
        reviewer = {
            "artifactType": "P7-T4-INDEPENDENT-REVIEW",
            "schemaVersion": "1.0.0",
            "comparisonIdentity": comparison["artifactIdentity"],
            "reviewerId": "independent-reviewer-test",
            "independentFromTraining": True,
            "reviewedAt": "2026-08-24T00:00:00Z",
            "decision": "PASS",
            "resourceUseAccepted": True,
            "rationale": "Review remains unresolved in the synthetic test.",
        }

        finalized = MODULE.finalize_comparison(
            comparison, base_human, adapter_human, reviewer, rubric
        )

        self.assertEqual("AWAITING_REVIEW_RESOLUTION", finalized["state"])
        self.assertFalse(finalized["promotionAllowed"])


if __name__ == "__main__":
    unittest.main()
