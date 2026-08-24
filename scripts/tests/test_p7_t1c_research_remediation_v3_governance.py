import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FINALIZER_PATH = (
    ROOT / "scripts" / "finalize-p7-t1c-research-remediation-governance-v3.py"
)
PIPELINE_PATH = ROOT / "scripts" / "dataset-pipeline-p7-t1-remediation.py"


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T1CResearchRemediationV3GovernanceTests(unittest.TestCase):
    APPROVED_AT = "2026-08-24T15:50:53Z"

    @classmethod
    def setUpClass(cls):
        cls.finalizer = load_module("p7_t1c_research_remediation_v3", FINALIZER_PATH)
        cls.pipeline = load_module("p7_t1_research_remediation_pipeline_v3", PIPELINE_PATH)

    def documents(self):
        approval_path = ROOT / self.finalizer.TRAINING_APPROVAL_REFERENCE
        approved_at = self.APPROVED_AT
        if approval_path.is_file():
            approved_at = self.finalizer.load_document(approval_path)["approval"]["approvedAt"]
        return self.finalizer.build_documents(
            request_identity=self.finalizer.TRAINING_REQUEST_IDENTITY,
            approved_by=self.finalizer.APPROVAL_AUTHORITY,
            approved_at=approved_at,
        )

    def test_approval_binds_exact_v3_request_and_second_failed_candidate(self):
        documents = self.documents()
        approval = documents[self.finalizer.TRAINING_APPROVAL_REFERENCE]

        self.assertEqual(
            "6b98270d32015aaf1f8f04aa43089a18128baf5fd55a785f675f0d56698851d1",
            approval["requestIdentity"],
        )
        self.assertEqual("APPROVED", approval["status"])
        self.assertEqual("3.0.0", approval["dataset"]["datasetVersion"])
        self.assertEqual(
            "445a2c33e7cf7a7b9dc8b69c3ebe01ab0d7cf2565463ffb3d30920d9509baf61",
            approval["remediationBinding"]["failedCandidateId"],
        )
        self.assertEqual(
            "aecbab9a601b20716821bd1ec8454924ff23b6088a35e7e1e21e4e7d654982f2",
            approval["remediationBinding"]["failedComparisonIdentity"],
        )
        self.assertFalse(approval["scope"]["frozenEvaluationTrainingUseAllowed"])
        self.assertEqual(
            self.finalizer.artifact_identity(approval),
            approval["artifactIdentity"],
        )

    def test_wrong_request_identity_cannot_be_finalized(self):
        with self.assertRaisesRegex(self.finalizer.FinalizationError, "request identity"):
            self.finalizer.build_documents(
                request_identity="0" * 64,
                approved_by=self.finalizer.APPROVAL_AUTHORITY,
                approved_at=self.APPROVED_AT,
            )

    def test_approved_card_remains_narrow_and_source_bound(self):
        documents = self.documents()
        card = documents[self.finalizer.APPROVED_CARD_REFERENCE]
        approval = documents[self.finalizer.TRAINING_APPROVAL_REFERENCE]

        self.assertEqual("3.0.0", card["dataset_version"])
        self.assertEqual("APPROVED", card["approval_status"])
        self.assertEqual("VERIFIED", card["source_permission_status"])
        self.assertEqual(["TRAINING"], card["approved_purposes"])
        self.assertEqual(
            approval["source"]["sourceSha256"],
            card["integrity"]["checksum"],
        )
        self.assertNotIn("CAT_RESEARCH_REPORT_METADATA", card["category_ids"])

    def test_scope_tampering_is_rejected_after_identity_recomputation(self):
        documents = self.documents()
        tampered = copy.deepcopy(documents)
        approval = tampered[self.finalizer.TRAINING_APPROVAL_REFERENCE]
        approval["scope"]["includedUseCases"].append("RESEARCH_UC_006")
        approval["artifactIdentity"] = self.finalizer.artifact_identity(approval)

        with self.assertRaisesRegex(self.finalizer.FinalizationError, "approval"):
            self.finalizer.validate_documents(tampered)

    def test_checked_in_finalization_and_dataset_reproduce_byte_for_byte(self):
        documents = self.documents()
        for reference, value in documents.items():
            self.assertEqual(
                self.finalizer.json_bytes(value),
                (ROOT / reference).read_bytes(),
            )

        source = self.finalizer.load_document(self.finalizer.SOURCE_EXPORT_PATH)
        contract = self.finalizer.load_document(self.finalizer.TRAINING_CONTRACT_PATH)
        config = self.finalizer.load_document(self.finalizer.P7T1_CONFIG_PATH)
        approval = documents[self.finalizer.TRAINING_APPROVAL_REFERENCE]
        card = documents[self.finalizer.APPROVED_CARD_REFERENCE]
        canonical_output = ROOT / self.finalizer.MATERIALIZED_DATASET_REFERENCE
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "dataset"
            manifest = self.pipeline.build_dataset(
                source, contract, card, approval, config, output
            )
            expected = {
                path.name: path.read_bytes()
                for path in canonical_output.iterdir()
                if path.is_file()
            }
            self.assertEqual(
                expected,
                {path.name: path.read_bytes() for path in output.iterdir()},
            )

        self.assertEqual(270, manifest["counts"]["sourceRecords"])
        self.assertEqual(270, manifest["counts"]["acceptedRecords"])
        self.assertEqual(0, manifest["counts"]["rejectedRecords"])
        self.assertTrue(all(manifest["counts"]["splits"].values()))
        self.assertEqual(
            self.pipeline.dataset_identity(manifest),
            manifest["checksum"],
        )

    def test_materialized_records_preserve_closed_public_contract(self):
        output = ROOT / self.finalizer.MATERIALIZED_DATASET_REFERENCE
        records = [
            json.loads(line)
            for split in ("train", "validation", "evaluation")
            for line in (output / f"{split}.jsonl").read_text(encoding="utf-8").splitlines()
        ]
        self.assertEqual(270, len(records))
        for record in records:
            self.assertEqual(
                record["trainingPrompt"]["evalCaseId"],
                record["trainingTarget"]["evalCaseId"],
            )
            self.assertEqual(set(self.pipeline.OUTPUT_KEYS), set(record["trainingTarget"]))

    def test_pending_card_still_fails_closed(self):
        source = self.finalizer.load_document(self.finalizer.SOURCE_EXPORT_PATH)
        contract = self.finalizer.load_document(self.finalizer.TRAINING_CONTRACT_PATH)
        pending = self.finalizer.load_document(self.finalizer.PENDING_CARD_PATH)
        config = self.finalizer.load_document(self.finalizer.P7T1_CONFIG_PATH)

        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(self.pipeline.DatasetPipelineError, "APPROVED"):
                self.pipeline.build_dataset(
                    source,
                    contract,
                    pending,
                    None,
                    config,
                    Path(directory) / "blocked",
                )


if __name__ == "__main__":
    unittest.main()
