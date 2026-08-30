import importlib.util
import json
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path
from unittest.mock import patch

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
        cls.governance_request = json.loads(
            (ROOT / "config" / "p7-t3-research-report-eval-governance-request.json").read_text(encoding="utf-8")
        )

    def governance_approval(self):
        return MODULE.GOVERNANCE.finalize_approval(
            self.governance_request,
            approved_by="synthetic-test-governance-owner",
            approved_at="2026-08-21T00:00:00Z",
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
                {
                    "evalCaseId": case["evalCaseId"],
                    "rawText": "{}",
                    "rawTextDigest": MODULE.P7T3.sha256_bytes(b"{}"),
                }
                for case in self.gap_suite["caseInventory"]
            ],
        )

    def approved_report_run_artifact(self):
        approval = self.governance_approval()
        execution_suite = MODULE.select_execution_suite(
            self.gap_suite,
            ["E-AUTH-011", "E-AUTH-012", "E-FUNC-RESEARCH-006"],
            governance_request=self.governance_request,
            governance_approval=approval,
        )
        candidate = {
            "suiteId": execution_suite["suiteId"],
            "suiteVersion": execution_suite["suiteVersion"],
            "candidateRunId": "qwen3_4b-R01-P7T3-GAP01",
            "modelMetadata": {
                "candidateId": "qwen3_4b",
                "sourceRunId": "qwen3_4b-R01",
                "identifier": "Qwen/Qwen3-4B-Instruct-2507",
                "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
            },
            "cases": [self.candidate_case(case) for case in execution_suite["caseInventory"]],
        }
        artifact = MODULE.build_run_artifact(
            execution_suite,
            candidate,
            raw_outputs=[
                {
                    "evalCaseId": case["evalCaseId"],
                    "rawText": "{}",
                    "rawTextDigest": MODULE.P7T3.sha256_bytes(b"{}"),
                }
                for case in execution_suite["caseInventory"]
            ],
            governance_approval=approval,
        )
        return artifact, approval

    def approved_review(self, run_artifact):
        rubric = yaml.safe_load((ROOT / "evals" / "human-eval-rubric.yaml").read_text(encoding="utf-8"))
        records = []
        by_id = {case["evalCaseId"]: case for case in run_artifact["candidate"]["cases"]}
        suite_cases = {
            case["evalCaseId"]: case
            for case in [*self.gap_suite["caseInventory"], *self.gap_suite["proposedCaseInventory"]]
        }
        for case_id in sorted(by_id):
            profile = suite_cases[case_id]["humanProfileId"]
            dimensions = rubric["profiles"][profile]["dimensions"]
            digest = MODULE.P7T3.sha256_bytes(MODULE.P7T3.canonical_bytes(by_id[case_id]))
            records.append(
                {
                    "evalCaseId": case_id,
                    "candidateCaseDigest": digest,
                    "profileId": profile,
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

    def test_preflight_rejects_wrong_snapshot_identity_and_missing_local_files(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            model_path = Path(temporary_directory) / MODULE.P7T3.BASE_MODEL["revision"]
            model_path.mkdir()
            (model_path / "config.json").write_text('{"model_type":"qwen3"}', encoding="utf-8")
            (model_path / "p7-t3-model-identity.json").write_text(
                json.dumps({"identifier": "Qwen/wrong", "revision": MODULE.P7T3.BASE_MODEL["revision"]}),
                encoding="utf-8",
            )
            with patch.object(MODULE.importlib.metadata, "version", return_value="0"):
                report = MODULE.preflight_environment(
                    model_path,
                    self.gap_suite,
                    self.gap_lock,
                    self.base_suite,
                    self.benchmark_config,
                    output_dir=Path(temporary_directory),
                )

        self.assertIn("MODEL_IDENTITY_MISMATCH", report["diagnostics"])
        self.assertIn("MODEL_LOCAL_FILES_INCOMPLETE", report["diagnostics"])
        self.assertFalse(report["networkAccessAllowed"])

    def test_each_safe_refusal_case_can_be_selected_individually(self):
        for case_id in ("E-AUTH-011", "E-AUTH-012"):
            selected = MODULE.select_execution_suite(
                self.gap_suite,
                [case_id],
                governance_request=self.governance_request,
                governance_approval=None,
            )
            self.assertEqual([case_id], [case["evalCaseId"] for case in selected["caseInventory"]])

    def test_unrelated_case_cannot_be_selected(self):
        with self.assertRaisesRegex(MODULE.P7T3.ResearchDecisionError, "TARGETED_CASE_SELECTION_INVALID"):
            MODULE.select_execution_suite(
                self.gap_suite,
                ["E-FUNC-ADMIN-001"],
                governance_request=self.governance_request,
                governance_approval=None,
            )

    def test_pending_request_cannot_select_report_case(self):
        with self.assertRaisesRegex(MODULE.P7T3.ResearchDecisionError, "AWAITING_GOVERNANCE_APPROVAL"):
            MODULE.select_execution_suite(
                self.gap_suite,
                ["E-FUNC-RESEARCH-006"],
                governance_request=self.governance_request,
                governance_approval=None,
            )

    def test_approved_request_selects_only_the_proposed_report_case(self):
        selected = MODULE.select_execution_suite(
            self.gap_suite,
            ["E-FUNC-RESEARCH-006"],
            governance_request=self.governance_request,
            governance_approval=self.governance_approval(),
        )

        report_case = selected["caseInventory"][0]
        self.assertEqual("E-FUNC-RESEARCH-006", report_case["evalCaseId"])
        self.assertEqual("ACTIVE", report_case["caseState"])
        self.assertEqual("DRAFT_RESEARCH", report_case["humanProfileId"])
        self.assertIsNone(report_case["allowedTool"])
        self.assertIsNone(report_case["rejectedTool"])

    def test_report_output_cannot_fabricate_the_bound_report_reference(self):
        approval = self.governance_approval()
        execution_suite = MODULE.select_execution_suite(
            self.gap_suite,
            ["E-FUNC-RESEARCH-006"],
            governance_request=self.governance_request,
            governance_approval=approval,
        )
        candidate = {
            "suiteId": execution_suite["suiteId"],
            "suiteVersion": execution_suite["suiteVersion"],
            "candidateRunId": "qwen3_4b-R01-P7T3-GAP01",
            "modelMetadata": {
                "candidateId": "qwen3_4b",
                "sourceRunId": "qwen3_4b-R01",
                "identifier": "Qwen/Qwen3-4B-Instruct-2507",
                "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
            },
            "cases": [self.candidate_case(execution_suite["caseInventory"][0])],
        }
        candidate["cases"][0]["structuredOutput"]["reportRef"] = "fabricated-report-999"

        findings, report = MODULE.score_gap_candidate(execution_suite, candidate)

        self.assertTrue(any("report reference" in finding for finding in findings))
        self.assertEqual("FAIL", report["automaticReport"][0]["automaticState"])

    def test_run_builder_rejects_non_object_raw_output_entries(self):
        artifact = self.run_artifact()

        with self.assertRaisesRegex(MODULE.P7T3.ResearchDecisionError, "raw output"):
            MODULE.build_run_artifact(
                self.gap_suite,
                artifact["candidate"],
                raw_outputs=[*artifact["rawOutputs"], "unexpected"],
            )

    def test_malformed_candidate_case_still_gets_an_automatic_fail_record(self):
        candidate = deepcopy(self.run_artifact()["candidate"])
        candidate["cases"][0]["unexpected"] = True

        findings, report = MODULE.score_gap_candidate(self.gap_suite, candidate)

        self.assertTrue(findings)
        by_id = {item["evalCaseId"]: item for item in report["automaticReport"]}
        self.assertEqual(set(case["evalCaseId"] for case in self.gap_suite["caseInventory"]), set(by_id))
        self.assertEqual("FAIL", by_id[candidate["cases"][0]["evalCaseId"]]["automaticState"])

    def test_normal_runner_cannot_self_generate_governance_approval(self):
        source = (ROOT / "scripts" / "research-gap-evidence-p7-t3.py").read_text(encoding="utf-8")

        self.assertNotIn('add_argument("--approved-by"', source)
        self.assertNotIn("finalize_approval(", source)

    def test_three_case_review_package_is_bounded_and_awaits_human_evaluation(self):
        run_artifact, _ = self.approved_report_run_artifact()

        review_input = MODULE.build_review_input(self.gap_suite, run_artifact)

        self.assertEqual("AWAITING_USER:HUMAN_EVALUATION", review_input["checkpoint"])
        self.assertEqual(
            ["E-AUTH-011", "E-AUTH-012", "E-FUNC-RESEARCH-006"],
            [item["evalCaseId"] for item in review_input["caseReviews"]],
        )
        self.assertTrue(all("caseDigest" in item for item in review_input["caseReviews"]))
        self.assertNotIn(str(ROOT), json.dumps(review_input))

    def test_review_sanitizer_redacts_contact_and_machine_local_paths(self):
        value = "reviewer@example.test C:\\Users\\reviewer\\private.txt /home/reviewer/private.txt"

        sanitized = MODULE._sanitize_review_value(value)

        self.assertNotIn("reviewer@example.test", sanitized)
        self.assertNotIn("C:\\Users\\reviewer", sanitized)
        self.assertNotIn("/home/reviewer", sanitized)
        self.assertIn("[REDACTED_EMAIL]", sanitized)
        self.assertEqual(2, sanitized.count("[REDACTED_LOCAL_PATH]"))

    def test_report_freeze_rejects_missing_or_pending_governance_approval(self):
        run_artifact, _ = self.approved_report_run_artifact()
        review = self.approved_review(run_artifact)

        with self.assertRaisesRegex(MODULE.P7T3.ResearchDecisionError, "AWAITING_GOVERNANCE_APPROVAL"):
            MODULE.freeze_gap_evidence(
                run_artifact,
                review,
                self.gap_suite,
                self.base_suite,
                self.baseline_evidence,
                source_reference="qwen3_4b-R01-P7T3-GAP01.json",
                review_reference="qwen3_4b-R01-P7T3-GAP01-review.json",
                evidence_reference="evidence/p7-t3-research-gap-evidence-v2.json",
                source_commit="test-source-commit",
                governance_request=self.governance_request,
                governance_approval=None,
            )

    def test_approved_three_case_freeze_binds_governance_and_is_deterministic(self):
        run_artifact, approval = self.approved_report_run_artifact()
        review = self.approved_review(run_artifact)
        kwargs = {
            "source_reference": "qwen3_4b-R01-P7T3-GAP01.json",
            "review_reference": "qwen3_4b-R01-P7T3-GAP01-review.json",
            "evidence_reference": "evidence/p7-t3-research-gap-evidence-v2.json",
            "source_commit": "test-source-commit",
            "governance_request": self.governance_request,
            "governance_approval": approval,
        }

        first = MODULE.freeze_gap_evidence(
            run_artifact,
            review,
            self.gap_suite,
            self.base_suite,
            self.baseline_evidence,
            **kwargs,
        )
        second = MODULE.freeze_gap_evidence(
            deepcopy(run_artifact),
            deepcopy(review),
            self.gap_suite,
            self.base_suite,
            self.baseline_evidence,
            **kwargs,
        )

        self.assertEqual(first, second)
        self.assertEqual(approval["artifactIdentity"], first["governanceApproval"]["approvalIdentity"])
        self.assertEqual(
            ["E-AUTH-011", "E-AUTH-012", "E-FUNC-RESEARCH-006"],
            first["executionCaseIds"],
        )

    def test_checked_in_handoff_binds_exact_cases_commands_and_governance_blocker(self):
        handoff = json.loads(
            (ROOT / "config" / "p7-t3-research-gap-evidence-handoff.json").read_text(encoding="utf-8")
        )
        identity = handoff.pop("handoffIdentity")

        self.assertEqual(
            identity,
            MODULE.P7T3.sha256_bytes(MODULE.P7T3.canonical_bytes(handoff)),
        )
        self.assertEqual(["E-AUTH-011", "E-AUTH-012"], handoff["caseSelection"]["preApproval"])
        self.assertEqual(
            ["E-AUTH-011", "E-AUTH-012", "E-FUNC-RESEARCH-006"],
            handoff["caseSelection"]["postApproval"],
        )
        self.assertIn("--run", handoff["executionCommands"]["safeRefusal"])
        self.assertIn("--governance-approval", handoff["executionCommands"]["approvedThreeCase"])
        self.assertIn("--freeze", handoff["freezeCommands"]["approvedThreeCase"])
        self.assertEqual(
            "AWAITING_GOVERNANCE_APPROVAL",
            handoff["reportReview"]["status"],
        )
        self.assertEqual("PENDING_USER_APPROVAL", handoff["governance"]["requestStatus"])
        self.assertFalse(handoff["governance"]["approvalCommandInvoked"])
        self.assertEqual("READY_FOR_RUNTIME_PROVISIONING", handoff["safeRefusalExecutionStatus"])
        self.assertEqual("EVIDENCE_EXECUTION_ENVIRONMENT_REQUIRED", handoff["safeRefusalBlocker"])

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
