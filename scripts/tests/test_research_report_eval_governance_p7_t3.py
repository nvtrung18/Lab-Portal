import importlib.util
import json
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "p7t3_report_governance",
    ROOT / "scripts" / "research-report-eval-governance-p7-t3.py",
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class P7T3ResearchReportGovernanceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.request = MODULE.load_document(MODULE.REQUEST_PATH)
        cls.governance = MODULE.load_document(MODULE.GOVERNANCE_PATH)
        cls.fixture_document = MODULE.load_document(MODULE.FIXTURE_PATH)
        cls.schema = MODULE.load_document(MODULE.SCHEMA_PATH)
        cls.gap_suite = MODULE.load_document(MODULE.GAP_SUITE_PATH)

    def approval(self):
        return MODULE.finalize_approval(
            self.request,
            approved_by="synthetic-test-governance-owner",
            approved_at="2026-08-21T00:00:00Z",
        )

    def test_checked_in_request_is_valid_and_pending(self):
        MODULE.validate_request(
            self.request,
            self.governance,
            self.fixture_document,
            self.schema,
            self.gap_suite,
        )

        self.assertEqual("PENDING_USER_APPROVAL", self.request["status"])
        self.assertIsNone(self.request["approval"])
        self.assertIsNone(self.request["approvedBy"])
        self.assertIsNone(self.request["approvedAt"])

    def test_authoritative_category_remains_deferred_and_cannot_execute(self):
        category = MODULE.category_record(self.governance)

        self.assertEqual("DEFERRED", category["use_decision"])
        self.assertEqual("DEFERRED_NO_EXPORT", category["sanitization_disposition"])
        with self.assertRaisesRegex(MODULE.GovernanceError, "AWAITING_GOVERNANCE_APPROVAL"):
            MODULE.validate_execution_authorization(self.request, None, purpose="EVALUATION")

    def test_pending_request_cannot_authorize_execution(self):
        with self.assertRaisesRegex(MODULE.GovernanceError, "AWAITING_GOVERNANCE_APPROVAL"):
            MODULE.validate_execution_authorization(self.request, self.request, purpose="EVALUATION")

    def test_missing_approval_cannot_authorize_execution(self):
        with self.assertRaisesRegex(MODULE.GovernanceError, "AWAITING_GOVERNANCE_APPROVAL"):
            MODULE.validate_execution_authorization(self.request, None, purpose="HUMAN_EVALUATION")

    def test_wrong_category_approval_is_rejected(self):
        approval = self.approval()
        approval["categoryId"] = "CAT_RESEARCH_DRAFT_CONTEXT"
        approval["artifactIdentity"] = MODULE.approval_identity(approval)

        with self.assertRaisesRegex(MODULE.GovernanceError, "category"):
            MODULE.validate_execution_authorization(self.request, approval, purpose="EVALUATION")

    def test_wrong_fixture_digest_is_rejected(self):
        approval = self.approval()
        approval["fixture"]["sha256"] = "0" * 64
        approval["artifactIdentity"] = MODULE.approval_identity(approval)

        with self.assertRaisesRegex(MODULE.GovernanceError, "fixture"):
            MODULE.validate_execution_authorization(self.request, approval, purpose="EVALUATION")

    def test_wrong_schema_digest_is_rejected(self):
        approval = self.approval()
        approval["schema"]["sha256"] = "0" * 64
        approval["artifactIdentity"] = MODULE.approval_identity(approval)

        with self.assertRaisesRegex(MODULE.GovernanceError, "schema"):
            MODULE.validate_execution_authorization(self.request, approval, purpose="EVALUATION")

    def test_wrong_case_id_is_rejected(self):
        approval = self.approval()
        approval["evaluationCase"]["evalCaseId"] = "E-FUNC-RESEARCH-999"
        approval["artifactIdentity"] = MODULE.approval_identity(approval)

        with self.assertRaisesRegex(MODULE.GovernanceError, "evaluation case"):
            MODULE.validate_execution_authorization(self.request, approval, purpose="EVALUATION")

    def test_wrong_source_identity_is_rejected(self):
        approval = self.approval()
        approval["sourceCommit"] = "0" * 40
        approval["artifactIdentity"] = MODULE.approval_identity(approval)

        with self.assertRaisesRegex(MODULE.GovernanceError, "source identity"):
            MODULE.validate_execution_authorization(self.request, approval, purpose="EVALUATION")

    def test_wrong_purpose_is_rejected(self):
        approval = self.approval()
        approval["permittedPurposes"] = ["HUMAN_EVALUATION"]
        approval["artifactIdentity"] = MODULE.approval_identity(approval)

        with self.assertRaisesRegex(MODULE.GovernanceError, "purposes"):
            MODULE.validate_execution_authorization(self.request, approval, purpose="EVALUATION")

    def test_evaluation_only_approval_cannot_authorize_training(self):
        with self.assertRaisesRegex(MODULE.GovernanceError, "purpose TRAINING"):
            MODULE.validate_execution_authorization(self.request, self.approval(), purpose="TRAINING")

    def test_evaluation_only_approval_cannot_authorize_rag(self):
        with self.assertRaisesRegex(MODULE.GovernanceError, "purpose RAG_INGESTION"):
            MODULE.validate_execution_authorization(self.request, self.approval(), purpose="RAG_INGESTION")

    def test_evaluation_only_approval_cannot_authorize_general_export(self):
        with self.assertRaisesRegex(MODULE.GovernanceError, "purpose GENERAL_DEVELOPMENT_EXPORT"):
            MODULE.validate_execution_authorization(
                self.request,
                self.approval(),
                purpose="GENERAL_DEVELOPMENT_EXPORT",
            )

    def test_explicit_approval_authorizes_only_targeted_evaluation(self):
        approval = self.approval()

        MODULE.validate_execution_authorization(self.request, approval, purpose="EVALUATION")
        MODULE.validate_execution_authorization(self.request, approval, purpose="HUMAN_EVALUATION")
        self.assertEqual(
            "E-FUNC-RESEARCH-006",
            approval["evaluationCase"]["evalCaseId"],
        )

    def test_approved_artifact_cannot_retain_deferred_disposition(self):
        approval = self.approval()
        approval["scope"]["sanitizationDisposition"] = "DEFERRED_NO_EXPORT"
        approval["artifactIdentity"] = MODULE.approval_identity(approval)

        with self.assertRaisesRegex(MODULE.GovernanceError, "non-deferred"):
            MODULE.validate_execution_authorization(self.request, approval, purpose="EVALUATION")

    def test_finalize_requires_explicit_approved_by(self):
        with self.assertRaisesRegex(MODULE.GovernanceError, "approved-by"):
            MODULE.finalize_approval(
                self.request,
                approved_by="",
                approved_at="2026-08-21T00:00:00Z",
            )

    def test_finalize_requires_explicit_approved_at(self):
        with self.assertRaisesRegex(MODULE.GovernanceError, "approved-at"):
            MODULE.finalize_approval(
                self.request,
                approved_by="synthetic-test-governance-owner",
                approved_at="",
            )

    def test_finalize_rejects_calendar_invalid_approved_at(self):
        with self.assertRaisesRegex(MODULE.GovernanceError, "approved-at"):
            MODULE.finalize_approval(
                self.request,
                approved_by="synthetic-test-governance-owner",
                approved_at="2026-02-31T00:00:00Z",
            )

    def test_approval_is_bound_and_deterministic(self):
        first = self.approval()
        second = MODULE.finalize_approval(
            deepcopy(self.request),
            approved_by="synthetic-test-governance-owner",
            approved_at="2026-08-21T00:00:00Z",
        )

        self.assertEqual(first, second)
        self.assertEqual(first["artifactIdentity"], MODULE.approval_identity(first))

    def test_approval_writer_is_append_only(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "approval.json"
            path.write_text('{"original":true}\n', encoding="utf-8")

            with self.assertRaisesRegex(MODULE.GovernanceError, "append-only"):
                MODULE.write_append_only(path, self.approval())

            self.assertEqual('{"original":true}\n', path.read_text(encoding="utf-8"))

    def test_fixture_is_synthetic_closed_and_contains_no_private_identity_fields(self):
        fixture = MODULE.fixture_record(self.fixture_document)
        serialized = json.dumps(fixture, sort_keys=True)

        self.assertTrue(fixture["metadata"]["synthetic"])
        self.assertNotIn("email", serialized.lower())
        self.assertNotIn("student", serialized.lower())
        self.assertEqual(
            {"reportRef", "projectRef", "groupRef", "reportMetadata"},
            set(fixture["input"]),
        )

    def test_fixture_and_case_digests_match_the_request(self):
        fixture = MODULE.fixture_record(self.fixture_document)
        report_case = MODULE.report_case(self.gap_suite)

        self.assertEqual(MODULE.canonical_sha256(fixture), self.request["fixture"]["sha256"])
        self.assertEqual(MODULE.canonical_sha256(report_case), self.request["evaluationCase"]["sha256"])
        self.assertEqual(MODULE.file_sha256(MODULE.SCHEMA_PATH), self.request["schema"]["sha256"])

    def test_historical_deferred_case_remains_unchanged(self):
        suite = yaml.safe_load((ROOT / "evals" / "p6-t4-evaluation-suites.yaml").read_text(encoding="utf-8"))
        historical = next(
            case for case in suite["caseInventory"] if case["evalCaseId"] == "E-DEFERRED-RESEARCH-006"
        )

        self.assertEqual("DEFERRED_ASSERTION_ONLY", historical["caseState"])
        self.assertEqual([], historical["suiteTags"])
        self.assertIsNone(historical["assistantKey"])
        self.assertIsNone(historical["input"])

    def test_report_case_forbids_tools_approval_and_status_mutation(self):
        case = MODULE.report_case(self.gap_suite)
        observation = self.gap_suite["expectedObservations"][case["expectedObservationId"]]
        output = observation["structuredOutput"]

        self.assertEqual("RESEARCH_ASSISTANT", case["assistantKey"])
        self.assertEqual("GOVERNANCE_PENDING", case["caseState"])
        self.assertEqual("DRAFT_ONLY", observation["actionRisk"])
        self.assertEqual({"kind": "NONE"}, observation["toolRequest"])
        self.assertIsNone(case["allowedTool"])
        self.assertIsNone(case["rejectedTool"])
        self.assertTrue(output["advisoryOnly"])
        self.assertFalse({"approval", "rejection", "statusMutation", "permissionGrant"} & set(output))


if __name__ == "__main__":
    unittest.main()
