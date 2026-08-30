import importlib.util
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "p7t1b_research_governance_packet",
    ROOT / "scripts" / "build-p7-t1b-research-governance-packet.py",
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class P7T1BResearchGovernancePacketTests(unittest.TestCase):
    def test_checked_in_packet_reproduces_byte_for_byte(self):
        artifacts = MODULE.build_artifacts()

        self.assertEqual(
            {
                "training-approval-request.json",
                "training-dataset-card.pending.json",
                "frozen-evaluation-approval-request.json",
                "frozen-evaluation-manifest.pending.json",
            },
            set(artifacts),
        )
        for filename, expected in artifacts.items():
            self.assertEqual(
                expected,
                (MODULE.CANONICAL_OUTPUT_DIRECTORY / filename).read_bytes(),
            )

    def test_training_request_binds_the_authoritative_source_and_remains_pending(self):
        documents = MODULE.build_documents()
        request = documents["training-approval-request.json"]

        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertIsNone(request["approval"])
        self.assertIsNone(request["approvedBy"])
        self.assertIsNone(request["approvedAt"])
        self.assertEqual(MODULE.SOURCE_ID, request["source"]["sourceId"])
        self.assertEqual(MODULE.SOURCE_VERSION, request["source"]["sourceVersion"])
        self.assertEqual(MODULE.SOURCE_SHA256, request["source"]["sourceSha256"])
        self.assertEqual(MODULE.CONTENT_IDENTITY, request["source"]["contentIdentity"])
        self.assertEqual(MODULE.PROVENANCE_IDENTITY, request["source"]["provenanceIdentity"])
        self.assertEqual(["TRAINING"], request["requestedScope"]["permittedPurposes"])
        self.assertEqual(MODULE.APPROVAL_AUTHORITY, request["approvalAuthority"])
        self.assertFalse(request["requestedScope"]["externalSharingAllowed"])
        self.assertFalse(request["currentState"]["trainingAuthorized"])
        self.assertEqual(MODULE.request_identity(request), request["requestIdentity"])

        export = MODULE.load_document(MODULE.SOURCE_EXPORT_PATH)
        provenance = MODULE.load_document(MODULE.PROVENANCE_PATH)
        self.assertEqual(MODULE.SOURCE_SHA256, MODULE.sha256_bytes(MODULE.SOURCE_EXPORT_PATH.read_bytes()))
        self.assertEqual(MODULE.CONTENT_IDENTITY, provenance["contentIdentity"])
        self.assertEqual(MODULE.PROVENANCE_IDENTITY, provenance["provenanceIdentity"])
        self.assertIsNone(export["source"]["sourcePermissionReference"])
        self.assertIsNone(export["source"]["approvalReference"])

    def test_pending_card_uses_native_fields_without_approval_evidence(self):
        documents = MODULE.build_documents()
        card = documents["training-dataset-card.pending.json"]
        governance = MODULE.load_document(MODULE.GOVERNANCE_PATH)["contract"]
        expected_fields = set(governance["dataset_card_contract"]["required_fields"])
        expected_fields.add("evaluation_freeze_prerequisite")

        self.assertEqual(expected_fields, set(card))
        self.assertEqual("PENDING", card["approval_status"])
        self.assertEqual("PENDING_APPROVAL", card["lifecycle_status"])
        self.assertEqual("NOT_ASSESSED", card["source_permission_status"])
        self.assertEqual([], card["approval_references"])
        self.assertEqual([], card["source_permission_references"])
        self.assertEqual([], card["approved_purposes"])
        self.assertEqual(["DEVELOPMENT_TEST"], card["permitted_purposes"])
        self.assertIsNone(card["evaluation_freeze_prerequisite"]["evaluation_approval_reference"])

        with self.assertRaisesRegex(MODULE.P7T1.DatasetPipelineError, "APPROVED required"):
            MODULE.P7T1.validate_card(
                card,
                documents["frozen-evaluation-manifest.pending.json"],
            )

    def test_frozen_evaluation_request_is_separate_evaluation_only_and_pending(self):
        documents = MODULE.build_documents()
        training_request = documents["training-approval-request.json"]
        evaluation_request = documents["frozen-evaluation-approval-request.json"]
        manifest = documents["frozen-evaluation-manifest.pending.json"]

        self.assertNotEqual(training_request["requestIdentity"], evaluation_request["requestIdentity"])
        self.assertEqual(["EVALUATION"], evaluation_request["requestedScope"]["permittedPurposes"])
        self.assertEqual(["TRAINING"], evaluation_request["requestedScope"]["forbiddenPurposes"])
        self.assertFalse(evaluation_request["requestedScope"]["trainingAllowed"])
        self.assertEqual("PENDING_USER_APPROVAL", evaluation_request["status"])
        self.assertIsNone(evaluation_request["approval"])
        self.assertEqual(MODULE.request_identity(evaluation_request), evaluation_request["requestIdentity"])
        self.assertEqual(MODULE.EVALUATION_SUITE_ID, manifest["dataset_id"])
        self.assertEqual(MODULE.EVALUATION_SUITE_VERSION, manifest["dataset_version"])
        self.assertEqual("EVALUATION", manifest["model_development_purpose"])
        self.assertEqual("FROZEN", manifest["lifecycle_status"])
        self.assertEqual("FROZEN", manifest["freeze_status"])
        self.assertEqual("FROZEN_EVALUATION", manifest["retention"]["retention_class"])
        self.assertEqual("PENDING", manifest["approval_status"])
        self.assertEqual([], manifest["approval_references"])
        self.assertEqual(MODULE.EVALUATION_SOURCE_COMMIT, evaluation_request["sourceCommit"])

    def test_recomputed_identity_cannot_hide_source_or_purpose_tampering(self):
        documents = MODULE.build_documents()
        training_tamper = deepcopy(documents)
        training_request = training_tamper["training-approval-request.json"]
        training_request["source"]["sourceSha256"] = "0" * 64
        training_request["requestIdentity"] = MODULE.request_identity(training_request)
        with self.assertRaisesRegex(MODULE.PacketBuildError, "source-bound"):
            MODULE.validate_documents(training_tamper)

        evaluation_tamper = deepcopy(documents)
        evaluation_request = evaluation_tamper["frozen-evaluation-approval-request.json"]
        evaluation_request["requestedScope"]["permittedPurposes"] = ["TRAINING"]
        evaluation_request["requestIdentity"] = MODULE.request_identity(evaluation_request)
        with self.assertRaisesRegex(MODULE.PacketBuildError, "frozen-suite"):
            MODULE.validate_documents(evaluation_tamper)

    def test_future_approved_bindings_are_native_p7_t1_compatible_without_writing_approval(self):
        documents = MODULE.build_documents()
        card = deepcopy(documents["training-dataset-card.pending.json"])
        evaluation = deepcopy(documents["frozen-evaluation-manifest.pending.json"])
        export = deepcopy(MODULE.load_document(MODULE.SOURCE_EXPORT_PATH))
        evaluation_reference = "PROBE_ONLY_EVALUATION_APPROVAL_REFERENCE"
        training_reference = "PROBE_ONLY_TRAINING_APPROVAL_REFERENCE"
        permission_reference = "PROBE_ONLY_SOURCE_PERMISSION_REFERENCE"

        evaluation["approval_status"] = "APPROVED"
        evaluation["approval_references"] = [evaluation_reference]
        card["source_permission_status"] = "VERIFIED"
        card["source_permission_references"] = [permission_reference]
        card["approval_status"] = "APPROVED"
        card["approval_references"] = [training_reference]
        card["lifecycle_status"] = "APPROVED"
        card["approved_purposes"] = ["TRAINING"]
        card["permitted_purposes"] = ["DEVELOPMENT_TEST", "TRAINING"]
        card["evaluation_freeze_prerequisite"]["evaluation_approval_reference"] = evaluation_reference
        export["source"]["sourcePermissionReference"] = permission_reference
        export["source"]["approvalReference"] = training_reference

        MODULE.P7T1.validate_card(card, evaluation)
        self.assertEqual(45, len(MODULE.P7T1.validate_controlled_export(export, card)))

    def test_current_export_stays_fail_closed_and_p7_t3_approval_is_not_reused(self):
        documents = MODULE.build_documents()
        export = MODULE.load_document(MODULE.SOURCE_EXPORT_PATH)
        card = documents["training-dataset-card.pending.json"]
        rendered = b"".join(MODULE.json_bytes(value) for value in documents.values())

        with self.assertRaisesRegex(MODULE.P7T1.DatasetPipelineError, "sourcePermissionReference|approvalReference"):
            MODULE.P7T1.validate_controlled_export(export, card)
        self.assertNotIn(b"p7-t3-research-report-eval-governance-approval", rendered.lower())
        self.assertNotIn(b"961957f646e9a8ae0d5ee9a8846dfdb85c554101a072eef3e833600121449114", rendered)

    def test_writes_are_deterministic_and_append_safe(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            first = root / "first"
            second = root / "second"
            MODULE.write_packet(first)
            MODULE.write_packet(second)

            self.assertEqual(
                {path.name: path.read_bytes() for path in first.iterdir()},
                {path.name: path.read_bytes() for path in second.iterdir()},
            )
            with self.assertRaisesRegex(MODULE.PacketBuildError, "must not already exist"):
                MODULE.write_packet(first)

    def test_historical_packet_reproduction_is_independent_from_the_live_approved_binding(self):
        original_binding_path = MODULE.EVALUATION_BINDING_PATH
        with tempfile.TemporaryDirectory() as temporary_directory:
            approved_binding = MODULE.load_document(original_binding_path)
            approved_binding["governanceState"] = "GOVERNED_EVIDENCE_APPROVED"
            approved_binding["approvalReference"] = (
                "evidence/p7-t1c-frozen-evaluation-governance-approval.json"
            )
            replacement = Path(temporary_directory) / "binding.yaml"
            replacement.write_text(
                MODULE.yaml.safe_dump(approved_binding, sort_keys=False),
                encoding="utf-8",
            )
            MODULE.EVALUATION_BINDING_PATH = replacement
            try:
                artifacts = MODULE.build_artifacts()
            finally:
                MODULE.EVALUATION_BINDING_PATH = original_binding_path

        for filename, expected in artifacts.items():
            self.assertEqual(expected, (MODULE.CANONICAL_OUTPUT_DIRECTORY / filename).read_bytes())


if __name__ == "__main__":
    unittest.main()
