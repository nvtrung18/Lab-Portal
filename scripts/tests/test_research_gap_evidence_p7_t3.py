import importlib.util
import json
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "p7t3_gap_runner", ROOT / "scripts" / "research-gap-evidence-p7-t3.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class P7T3ResearchGapEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base_suite = yaml.safe_load(
            (ROOT / "evals" / "p6-t4-evaluation-suites.yaml").read_text(encoding="utf-8")
        )
        cls.gap_suite = json.loads(
            (ROOT / "evals" / "p7-t3-research-gap-evaluation-suite.json").read_text(encoding="utf-8")
        )
        cls.gap_lock = json.loads(
            (ROOT / "evals" / "p7-t3-research-gap-evaluation-suite.lock.json").read_text(encoding="utf-8")
        )
        cls.benchmark_config = yaml.safe_load(
            (ROOT / "config" / "p6-t5-benchmark.yaml").read_text(encoding="utf-8")
        )
        cls.baseline_evidence = json.loads(
            (ROOT / "evidence" / "p7-t3-research-baseline-evidence.json").read_text(encoding="utf-8")
        )

    def candidate_case(self, case):
        observation = self.gap_suite["expectedObservations"][case["expectedObservationId"]]
        return {
            "evalCaseId": case["evalCaseId"],
            "response": {
                **deepcopy(observation["responseContract"]),
                "text": "Từ chối an toàn vì không có ngữ cảnh được cấp quyền.",
            },
            "observedBehavior": observation["behavior"],
            "observedActionRisk": observation["actionRisk"],
            "toolRequest": deepcopy(observation["toolRequest"]),
            "structuredOutput": deepcopy(observation["structuredOutput"]),
            "referencedContextIds": deepcopy(observation["referencedContextIds"]),
        }

    def run_artifact(self):
        candidate = {
            "suiteId": self.gap_suite["suiteId"],
            "suiteVersion": self.gap_suite["suiteVersion"],
            "candidateRunId": "qwen3_4b-R01-P7T3-GAP01",
            "modelMetadata": {
                "candidateId": "qwen3_4b",
                "sourceRunId": "qwen3_4b-R01",
                "identifier": "Qwen/Qwen3-4B-Instruct-2507",
                "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
            },
            "cases": [self.candidate_case(case) for case in self.gap_suite["caseInventory"]],
        }
        return MODULE.build_run_artifact(
            self.gap_suite,
            candidate,
            raw_outputs=[
                {"evalCaseId": case["evalCaseId"], "rawText": "{}", "rawTextDigest": "1" * 64}
                for case in self.gap_suite["caseInventory"]
            ],
        )

    def approved_review(self, run_artifact):
        rubric = yaml.safe_load((ROOT / "evals" / "human-eval-rubric.yaml").read_text(encoding="utf-8"))
        dimensions = rubric["profiles"]["REFUSAL"]["dimensions"]
        records = []
        by_id = {case["evalCaseId"]: case for case in run_artifact["candidate"]["cases"]}
        for case_id in sorted(by_id):
            digest = MODULE.P7T3.sha256_bytes(MODULE.P7T3.canonical_bytes(by_id[case_id]))
            records.append(
                {
                    "evalCaseId": case_id,
                    "candidateCaseDigest": digest,
                    "profileId": "REFUSAL",
                    "dimensions": [
                        {
                            "dimension": dimension,
                            "outcome": "PASS",
                            "rationale": "Human-reviewed bounded refusal.",
                            "evidenceRefs": [f"evalCaseId:{case_id}"],
                        }
                        for dimension in dimensions
                    ],
                    "overall": "PASS",
                    "reviewerRationale": "Human-approved refusal evidence.",
                    "evidenceRefs": [f"evalCaseId:{case_id}"],
                }
            )
        return {
            "candidateOutputDigest": run_artifact["candidateOutputDigest"],
            "approval": {
                "decision": "USER_APPROVED",
                "source": "user-reviewed-sidecar",
                "approvedAt": "2026-08-21T00:00:00Z",
            },
            "review": {
                "suiteId": self.gap_suite["suiteId"],
                "suiteVersion": self.gap_suite["suiteVersion"],
                "candidateRunId": "qwen3_4b-R01-P7T3-GAP01",
                "rubricVersion": rubric["rubricVersion"],
                "records": records,
            },
        }

    def test_preflight_reports_missing_model_and_runtime_without_download(self):
        report = MODULE.preflight_environment(
            None,
            self.gap_suite,
            self.gap_lock,
            self.base_suite,
            self.benchmark_config,
        )

        self.assertEqual("EVIDENCE_EXECUTION_ENVIRONMENT_REQUIRED", report["status"])
        self.assertIn("MODEL_SNAPSHOT_REQUIRED", report["diagnostics"])
        self.assertFalse(report["networkAccessAllowed"])

    def test_checked_in_handoff_binds_exact_cases_commands_and_governance_blocker(self):
        handoff = json.loads(
            (ROOT / "config" / "p7-t3-research-gap-evidence-handoff.json").read_text(encoding="utf-8")
        )
        identity = handoff.pop("handoffIdentity")

        self.assertEqual(
            identity,
            MODULE.P7T3.sha256_bytes(MODULE.P7T3.canonical_bytes(handoff)),
        )
        self.assertEqual(["E-AUTH-011", "E-AUTH-012"], handoff["caseIds"])
        self.assertIn("--run", handoff["executionCommand"])
        self.assertIn("--freeze", handoff["freezeCommand"])
        self.assertEqual(
            "REPORT_REVIEW_EVALUATION_GOVERNANCE_BLOCKED",
            handoff["reportReview"]["status"],
        )

    def test_run_artifact_is_scored_but_stops_at_human_review(self):
        artifact = self.run_artifact()

        self.assertEqual("AWAITING_USER:HUMAN_EVALUATION", artifact["checkpoint"])
        self.assertEqual(
            ["E-AUTH-011", "E-AUTH-012"],
            [result["evalCaseId"] for result in artifact["automatic"]["automaticReport"]],
        )
        self.assertTrue(all(result["automaticState"] == "PASS" for result in artifact["automatic"]["automaticReport"]))

    def test_evidence_writer_is_append_only(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "artifact.json"
            path.write_text('{"original":true}\n', encoding="utf-8")

            with self.assertRaisesRegex(MODULE.P7T3.ResearchDecisionError, "append-only"):
                MODULE._write_append_only(path, {"replacement": True})

            self.assertEqual('{"original":true}\n', path.read_text(encoding="utf-8"))

    def test_freeze_rejects_ai_or_incomplete_approval(self):
        run_artifact = self.run_artifact()
        review = self.approved_review(run_artifact)
        review["approval"]["decision"] = "AI_PROPOSED"

        with self.assertRaisesRegex(MODULE.P7T3.ResearchDecisionError, "USER_APPROVED"):
            MODULE.freeze_gap_evidence(
                run_artifact,
                review,
                self.gap_suite,
                self.base_suite,
                self.baseline_evidence,
                source_reference="qwen3_4b-R01-P7T3-GAP01.json",
                review_reference="qwen3_4b-R01-P7T3-GAP01-review.json",
                evidence_reference="evidence/p7-t3-research-gap-evidence-v1.json",
                source_commit="test-source-commit",
            )

    def test_freeze_rejects_unavailable_source_commit(self):
        run_artifact = self.run_artifact()
        review = self.approved_review(run_artifact)

        with self.assertRaisesRegex(MODULE.P7T3.ResearchDecisionError, "source commit"):
            MODULE.freeze_gap_evidence(
                run_artifact,
                review,
                self.gap_suite,
                self.base_suite,
                self.baseline_evidence,
                source_reference="qwen3_4b-R01-P7T3-GAP01.json",
                review_reference="qwen3_4b-R01-P7T3-GAP01-review.json",
                evidence_reference="evidence/p7-t3-research-gap-evidence-v1.json",
                source_commit="UNAVAILABLE",
            )

    def test_freeze_derives_results_from_user_review_and_is_deterministic(self):
        run_artifact = self.run_artifact()
        review = self.approved_review(run_artifact)

        first = MODULE.freeze_gap_evidence(
            run_artifact,
            review,
            self.gap_suite,
            self.base_suite,
            self.baseline_evidence,
            source_reference="qwen3_4b-R01-P7T3-GAP01.json",
            review_reference="qwen3_4b-R01-P7T3-GAP01-review.json",
            evidence_reference="evidence/p7-t3-research-gap-evidence-v1.json",
            source_commit="test-source-commit",
        )
        second = MODULE.freeze_gap_evidence(
            deepcopy(run_artifact),
            deepcopy(review),
            self.gap_suite,
            self.base_suite,
            self.baseline_evidence,
            source_reference="qwen3_4b-R01-P7T3-GAP01.json",
            review_reference="qwen3_4b-R01-P7T3-GAP01-review.json",
            evidence_reference="evidence/p7-t3-research-gap-evidence-v1.json",
            source_commit="test-source-commit",
        )

        self.assertEqual(first, second)
        self.assertTrue(all(result["result"] == "PASS" for result in first["caseResults"]))
        self.assertNotIn("rawText", json.dumps(first))


if __name__ == "__main__":
    unittest.main()
