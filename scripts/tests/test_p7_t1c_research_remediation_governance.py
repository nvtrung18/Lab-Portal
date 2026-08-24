import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FINALIZER_PATH = ROOT / "scripts" / "finalize-p7-t1c-research-remediation-governance.py"
PIPELINE_PATH = ROOT / "scripts" / "dataset-pipeline-p7-t1-remediation.py"


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T1CResearchRemediationGovernanceTests(unittest.TestCase):
    APPROVED_AT = "2026-08-24T09:06:29Z"

    @classmethod
    def setUpClass(cls):
        cls.finalizer = load_module("p7_t1c_research_remediation", FINALIZER_PATH)
        cls.pipeline = load_module("p7_t1_research_remediation_pipeline", PIPELINE_PATH)

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

    def test_approval_binds_exact_corrected_request_and_narrow_scope(self):
        documents = self.documents()
        approval = documents[self.finalizer.TRAINING_APPROVAL_REFERENCE]

        self.assertEqual(self.finalizer.TRAINING_REQUEST_IDENTITY, approval["requestIdentity"])
        self.assertEqual("APPROVED", approval["status"])
        self.assertEqual(["TRAINING"], approval["scope"]["permittedPurposes"])
        self.assertEqual(
            ["RESEARCH_UC_003", "RESEARCH_UC_004", "RESEARCH_UC_005"],
            approval["scope"]["includedUseCases"],
        )
        self.assertEqual(
            {"RESEARCH_UC_006": "TRAINING_PROHIBITED_BY_CURRENT_GOVERNANCE"},
            approval["scope"]["excludedUseCases"],
        )
        self.assertFalse(approval["scope"]["frozenEvaluationTrainingUseAllowed"])
        self.assertEqual("VERIFIED", approval["sourcePermission"]["status"])
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

    def test_approved_card_is_source_bound_and_does_not_widen_report_scope(self):
        documents = self.documents()
        card = documents[self.finalizer.APPROVED_CARD_REFERENCE]
        approval = documents[self.finalizer.TRAINING_APPROVAL_REFERENCE]

        self.assertEqual("2.0.0", card["dataset_version"])
        self.assertEqual("APPROVED", card["approval_status"])
        self.assertEqual("VERIFIED", card["source_permission_status"])
        self.assertEqual(["TRAINING"], card["approved_purposes"])
        self.assertEqual(
            [self.finalizer.TRAINING_APPROVAL_REFERENCE],
            card["approval_references"],
        )
        self.assertEqual(
            approval["source"]["sourceSha256"],
            card["integrity"]["checksum"],
        )
        self.assertNotIn("CAT_RESEARCH_REPORT_METADATA", card["category_ids"])

    def test_approval_scope_tampering_is_rejected_even_after_identity_recomputation(self):
        documents = self.documents()
        tampered = copy.deepcopy(documents)
        approval = tampered[self.finalizer.TRAINING_APPROVAL_REFERENCE]
        approval["scope"]["includedUseCases"].append("RESEARCH_UC_006")
        approval["artifactIdentity"] = self.finalizer.artifact_identity(approval)

        with self.assertRaisesRegex(self.finalizer.FinalizationError, "approval"):
            self.finalizer.validate_documents(tampered)

    def test_checked_in_finalization_reproduces_byte_for_byte(self):
        documents = self.documents()
        for reference, value in documents.items():
            self.assertEqual(
                self.finalizer.json_bytes(value),
                (ROOT / reference).read_bytes(),
            )

    def test_materialized_dataset_reproduces_byte_for_byte(self):
        documents = self.documents()
        approval = documents[self.finalizer.TRAINING_APPROVAL_REFERENCE]
        card = documents[self.finalizer.APPROVED_CARD_REFERENCE]
        source = self.finalizer.load_document(self.finalizer.SOURCE_EXPORT_PATH)
        contract = self.finalizer.load_document(self.finalizer.TRAINING_CONTRACT_PATH)
        config = self.finalizer.load_document(self.finalizer.P7T1_CONFIG_PATH)
        canonical_output = ROOT / self.finalizer.MATERIALIZED_DATASET_REFERENCE

        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "first"
            second = Path(directory) / "second"
            first_manifest = self.pipeline.build_dataset(
                source, contract, card, approval, config, first
            )
            second_manifest = self.pipeline.build_dataset(
                source, contract, card, approval, config, second
            )
            expected = {
                path.name: path.read_bytes()
                for path in canonical_output.iterdir()
                if path.is_file()
            }
            self.assertEqual(expected, {path.name: path.read_bytes() for path in first.iterdir()})
            self.assertEqual(expected, {path.name: path.read_bytes() for path in second.iterdir()})

        self.assertEqual(first_manifest, second_manifest)
        self.assertEqual(45, first_manifest["counts"]["sourceRecords"])
        self.assertEqual(45, first_manifest["counts"]["acceptedRecords"])
        self.assertEqual(0, first_manifest["counts"]["rejectedRecords"])
        self.assertTrue(all(first_manifest["counts"]["splits"].values()))
        self.assertEqual(
            self.pipeline.dataset_identity(first_manifest),
            first_manifest["checksum"],
        )

    def test_training_records_use_only_contract_prompt_and_target(self):
        output = ROOT / self.finalizer.MATERIALIZED_DATASET_REFERENCE
        split_ids = {}
        for split in ("train", "validation", "evaluation"):
            records = [
                json.loads(line)
                for line in (output / f"{split}.jsonl").read_text(encoding="utf-8").splitlines()
            ]
            split_ids[split] = {record["contentId"] for record in records}
            for record in records:
                self.assertEqual(
                    {
                        "schemaVersion",
                        "assistantKey",
                        "domain",
                        "recordType",
                        "visibility",
                        "useCaseId",
                        "trainingPrompt",
                        "trainingTarget",
                        "contentId",
                    },
                    set(record),
                )
                self.assertEqual(
                    record["trainingPrompt"]["evalCaseId"],
                    record["trainingTarget"]["evalCaseId"],
                )
                self.assertEqual(
                    set(self.pipeline.OUTPUT_KEYS),
                    set(record["trainingTarget"]),
                )
        self.assertTrue(split_ids["train"].isdisjoint(split_ids["validation"]))
        self.assertTrue(split_ids["train"].isdisjoint(split_ids["evaluation"]))
        self.assertTrue(split_ids["validation"].isdisjoint(split_ids["evaluation"]))

    def test_pipeline_rejects_pending_card_and_missing_approval(self):
        source = self.finalizer.load_document(self.finalizer.SOURCE_EXPORT_PATH)
        contract = self.finalizer.load_document(self.finalizer.TRAINING_CONTRACT_PATH)
        pending_card = self.finalizer.load_document(self.finalizer.PENDING_CARD_PATH)
        config = self.finalizer.load_document(self.finalizer.P7T1_CONFIG_PATH)

        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(self.pipeline.DatasetPipelineError, "APPROVED"):
                self.pipeline.build_dataset(
                    source,
                    contract,
                    pending_card,
                    None,
                    config,
                    Path(directory) / "blocked",
                )


if __name__ == "__main__":
    unittest.main()
