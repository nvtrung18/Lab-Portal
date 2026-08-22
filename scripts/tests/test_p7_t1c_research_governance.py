import importlib.util
import hashlib
import json
import shutil
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "p7t1c_research_governance",
    ROOT / "scripts" / "finalize-p7-t1c-research-governance.py",
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class P7T1CResearchGovernanceTests(unittest.TestCase):
    APPROVED_AT = "2026-08-22T00:00:00Z"

    def documents(self):
        approval_path = ROOT / MODULE.TRAINING_APPROVAL_REFERENCE
        approved_at = self.APPROVED_AT
        if approval_path.is_file():
            approved_at = MODULE.load_document(approval_path)["approval"]["approvedAt"]
        return MODULE.build_documents(
            approved_by=MODULE.APPROVAL_AUTHORITY,
            approved_at=approved_at,
        )

    def test_two_approvals_bind_exact_requests_and_keep_scopes_separate(self):
        documents = self.documents()
        training = documents[MODULE.TRAINING_APPROVAL_REFERENCE]
        evaluation = documents[MODULE.EVALUATION_APPROVAL_REFERENCE]

        self.assertEqual(MODULE.TRAINING_REQUEST_IDENTITY, training["requestIdentity"])
        self.assertEqual(["TRAINING"], training["scope"]["permittedPurposes"])
        self.assertEqual("VERIFIED", training["sourcePermission"]["status"])
        self.assertEqual(MODULE.SOURCE_SHA256, training["source"]["sourceSha256"])
        self.assertEqual(MODULE.artifact_identity(training), training["artifactIdentity"])

        self.assertEqual(MODULE.EVALUATION_REQUEST_IDENTITY, evaluation["requestIdentity"])
        self.assertEqual(["EVALUATION"], evaluation["scope"]["permittedPurposes"])
        self.assertEqual(["TRAINING"], evaluation["scope"]["forbiddenPurposes"])
        self.assertFalse(evaluation["scope"]["trainingAllowed"])
        self.assertEqual(MODULE.artifact_identity(evaluation), evaluation["artifactIdentity"])
        self.assertNotEqual(training["artifactIdentity"], evaluation["artifactIdentity"])

    def test_approved_card_and_manifest_are_native_p7_t1_inputs(self):
        documents = self.documents()
        card = documents[MODULE.APPROVED_CARD_REFERENCE]
        manifest = documents[MODULE.APPROVED_EVALUATION_MANIFEST_REFERENCE]
        training = documents[MODULE.TRAINING_APPROVAL_REFERENCE]
        export = MODULE.load_document(MODULE.SOURCE_EXPORT_PATH)

        MODULE.P7T1.validate_card(card, manifest)
        records = MODULE.P7T1.validate_controlled_export(
            export,
            card,
            source_approval=training,
            source_approval_reference=MODULE.TRAINING_APPROVAL_REFERENCE,
            source_export_sha256=MODULE.SOURCE_SHA256,
            require_durable_source_approval=False,
        )
        self.assertEqual(45, len(records))
        self.assertEqual(["TRAINING"], card["approved_purposes"])
        self.assertEqual([MODULE.EVALUATION_APPROVAL_REFERENCE], manifest["approval_references"])

    def test_recomputed_identity_cannot_hide_approval_scope_tampering(self):
        documents = self.documents()
        tampered = deepcopy(documents)
        approval = tampered[MODULE.EVALUATION_APPROVAL_REFERENCE]
        approval["scope"]["permittedPurposes"] = ["TRAINING"]
        approval["artifactIdentity"] = MODULE.artifact_identity(approval)

        with self.assertRaisesRegex(MODULE.FinalizationError, "EVALUATION approval"):
            MODULE.validate_documents(tampered)

    def test_immutable_source_requires_the_exact_training_approval_sidecar(self):
        documents = self.documents()
        export = MODULE.load_document(MODULE.SOURCE_EXPORT_PATH)
        card = documents[MODULE.APPROVED_CARD_REFERENCE]

        with self.assertRaisesRegex(MODULE.P7T1.DatasetPipelineError, "source approval"):
            MODULE.P7T1.validate_controlled_export(export, card)

        tampered = deepcopy(documents[MODULE.TRAINING_APPROVAL_REFERENCE])
        tampered["source"]["sourceSha256"] = "0" * 64
        tampered["artifactIdentity"] = MODULE.artifact_identity(tampered)
        with self.assertRaisesRegex(MODULE.P7T1.DatasetPipelineError, "source SHA-256"):
            MODULE.P7T1.validate_controlled_export(
                export,
                card,
                source_approval=tampered,
                source_approval_reference=MODULE.TRAINING_APPROVAL_REFERENCE,
                source_export_sha256=MODULE.SOURCE_SHA256,
            )

        missing_reference = "evidence/p7-t1c-missing-training-approval.json"
        missing = deepcopy(documents[MODULE.TRAINING_APPROVAL_REFERENCE])
        missing["sourcePermission"]["evidenceReference"] = missing_reference
        missing["artifactIdentity"] = MODULE.artifact_identity(missing)
        missing_card = deepcopy(card)
        missing_card["source_permission_references"] = [missing_reference]
        missing_card["approval_references"] = [missing_reference]
        with self.assertRaisesRegex(MODULE.P7T1.DatasetPipelineError, "durable repository evidence"):
            MODULE.P7T1.validate_controlled_export(
                export,
                missing_card,
                source_approval=missing,
                source_approval_reference=missing_reference,
                source_export_sha256=MODULE.SOURCE_SHA256,
            )

    def test_transition_approves_binding_updates_its_lock_digest_and_finalizes_config(self):
        documents = self.documents()
        transitions = MODULE.build_transition_documents(documents)
        binding = transitions[MODULE.EVALUATION_BINDING_REFERENCE]
        lock = transitions[MODULE.EVALUATION_LOCK_REFERENCE]
        config = transitions[MODULE.P7T1_CONFIG_REFERENCE]

        self.assertEqual("GOVERNED_EVIDENCE_APPROVED", binding["governanceState"])
        self.assertEqual(MODULE.EVALUATION_APPROVAL_REFERENCE, binding["approvalReference"])
        self.assertEqual(
            hashlib.sha256(MODULE.yaml_bytes(binding).replace(b"\r\n", b"\n")).hexdigest(),
            lock["files"][MODULE.EVALUATION_BINDING_REFERENCE],
        )
        self.assertEqual(MODULE.APPROVED_CARD_REFERENCE, config["cardReference"])

    def test_final_documents_and_transitions_reproduce_deterministically(self):
        first = self.documents()
        second = self.documents()

        self.assertEqual(
            {path: MODULE.json_bytes(value) for path, value in first.items()},
            {path: MODULE.json_bytes(value) for path, value in second.items()},
        )
        self.assertEqual(MODULE.build_transition_documents(first), MODULE.build_transition_documents(second))

    def test_approved_transition_passes_native_governed_release_validation(self):
        transitions = MODULE.build_transition_documents(self.documents())
        with tempfile.TemporaryDirectory() as temporary_directory:
            artifact_root = Path(temporary_directory)
            shutil.copytree(ROOT / "evals", artifact_root / "evals")
            (artifact_root / MODULE.EVALUATION_BINDING_REFERENCE).write_bytes(
                MODULE.yaml_bytes(transitions[MODULE.EVALUATION_BINDING_REFERENCE])
            )
            (artifact_root / MODULE.EVALUATION_LOCK_REFERENCE).write_bytes(
                MODULE.json_bytes(transitions[MODULE.EVALUATION_LOCK_REFERENCE])
            )
            suite = MODULE.EVALUATION.load(artifact_root / "evals/p6-t4-evaluation-suites.yaml")
            schema = json.loads(
                (artifact_root / "evals/evaluation-suite.schema.json").read_text(encoding="utf-8")
            )
            rubric = MODULE.EVALUATION.load(artifact_root / "evals/human-eval-rubric.yaml")
            errors = MODULE.EVALUATION.validate_suite(
                suite,
                schema,
                rubric,
                transitions[MODULE.EVALUATION_LOCK_REFERENCE],
                transitions[MODULE.EVALUATION_BINDING_REFERENCE],
                MODULE.EVALUATION.DATASET_MODEL_WORK_RELEASE,
                artifact_root,
            )

        self.assertEqual([], errors)

    def test_repository_outputs_are_deterministic_and_approval_artifacts_are_append_only(self):
        documents = self.documents()
        transitions = MODULE.build_transition_documents(documents)
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            first = root / "first"
            second = root / "second"
            MODULE.write_document_set(first, documents, transitions)
            MODULE.write_document_set(second, documents, transitions)

            first_bytes = {
                path.relative_to(first).as_posix(): path.read_bytes()
                for path in first.rglob("*")
                if path.is_file()
            }
            second_bytes = {
                path.relative_to(second).as_posix(): path.read_bytes()
                for path in second.rglob("*")
                if path.is_file()
            }
            self.assertEqual(first_bytes, second_bytes)
            with self.assertRaisesRegex(MODULE.FinalizationError, "append-only"):
                MODULE.write_document_set(first, documents, transitions)

    def test_checked_in_finalization_reproduces_byte_for_byte(self):
        stored_training = MODULE.load_document(ROOT / MODULE.TRAINING_APPROVAL_REFERENCE)
        documents = MODULE.build_documents(
            approved_by=stored_training["approval"]["approvedBy"],
            approved_at=stored_training["approval"]["approvedAt"],
        )
        transitions = MODULE.build_transition_documents(documents)

        for reference, value in documents.items():
            self.assertEqual(MODULE.json_bytes(value), (ROOT / reference).read_bytes())
        for reference, value in transitions.items():
            expected = MODULE._serialized_document(reference, value)
            self.assertEqual(expected, (ROOT / reference).read_bytes())

    def test_checked_in_materialized_dataset_reproduces_byte_for_byte(self):
        export = MODULE.load_document(MODULE.SOURCE_EXPORT_PATH)
        approval = MODULE.load_document(ROOT / MODULE.TRAINING_APPROVAL_REFERENCE)
        card = MODULE.load_document(ROOT / MODULE.APPROVED_CARD_REFERENCE)
        evaluation = MODULE.load_document(ROOT / MODULE.APPROVED_EVALUATION_MANIFEST_REFERENCE)
        config = MODULE.load_document(MODULE.P7T1_CONFIG_PATH)
        canonical_output = ROOT / MODULE.MATERIALIZED_DATASET_REFERENCE

        with tempfile.TemporaryDirectory() as temporary_directory:
            first = Path(temporary_directory) / "first"
            second = Path(temporary_directory) / "second"
            first_manifest = MODULE.P7T1.build_dataset(
                export,
                card,
                config,
                first,
                evaluation,
                source_approval=approval,
                source_approval_reference=MODULE.TRAINING_APPROVAL_REFERENCE,
                source_export_sha256=MODULE.SOURCE_SHA256,
            )
            second_manifest = MODULE.P7T1.build_dataset(
                export,
                card,
                config,
                second,
                evaluation,
                source_approval=approval,
                source_approval_reference=MODULE.TRAINING_APPROVAL_REFERENCE,
                source_export_sha256=MODULE.SOURCE_SHA256,
            )
            canonical_bytes = {
                path.name: path.read_bytes() for path in canonical_output.iterdir() if path.is_file()
            }
            self.assertEqual(canonical_bytes, {path.name: path.read_bytes() for path in first.iterdir()})
            self.assertEqual(canonical_bytes, {path.name: path.read_bytes() for path in second.iterdir()})

        self.assertEqual(first_manifest, second_manifest)
        self.assertEqual(45, first_manifest["counts"]["sourceRecords"])
        self.assertEqual(45, first_manifest["counts"]["acceptedRecords"])
        self.assertEqual(0, first_manifest["counts"]["rejectedRecords"])
        self.assertEqual(MODULE.P7T1.dataset_identity(first_manifest), first_manifest["checksum"])

    def test_materialized_dataset_is_a_valid_p7_t2_input_without_running_training(self):
        manifest_path = ROOT / MODULE.MATERIALIZED_DATASET_REFERENCE / "manifest.json"
        manifest = MODULE.load_document(manifest_path)

        validated = MODULE.P7T2.validate_dataset_manifest(
            manifest_path,
            manifest["checksum"],
            "RESEARCH_ASSISTANT",
            {"train", "validation"},
        )

        self.assertEqual(manifest, validated)
        p7t2_config = MODULE.load_document(ROOT / "config/p7-t2-training-pipeline.json")
        self.assertEqual(
            "datasets/p7-research-synthetic-training-dataset-v1/manifest.json",
            p7t2_config["dataset"]["manifestReference"],
        )
        self.assertEqual(manifest["checksum"], p7t2_config["dataset"]["identity"])


if __name__ == "__main__":
    unittest.main()
