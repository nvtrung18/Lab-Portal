import hashlib
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REQUEST_IDENTITY = "bdd4227a12c9695071ce397ff19e20eb7b9972dadd70bf9d7cb2952c90d73c63"
DATASET_IDENTITY = "6091c1be0a482bda5bc51d51e64583bd4a87bd4befe71b1f220535d6d03216a3"
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_TRAINING_APPROVAL_AUTHORITY"
FINALIZER_PATH = ROOT / "scripts/finalize-p7-t1c-research-remediation-governance-v9.py"
PIPELINE_PATH = ROOT / "scripts/training-pipeline-p7-t2-remediation-v9.py"
BACKEND_PATH = ROOT / "scripts/p7-t2-real-training-remediation-v9.py"
BUILDER_PATH = ROOT / "scripts/build-p7-t2-research-remediation-v9-bundle.py"
VALIDATOR_PATH = ROOT / "scripts/validate-p7-t2-research-remediation-v9-bundle.py"
CONFIG_PATH = ROOT / "config/p7-t2-training-pipeline-t4-remediation-v9.json"
MANIFEST_PATH = ROOT / "datasets/p7-research-synthetic-training-dataset-v9/manifest.approved.json"


def load_module(name, path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T2ResearchRemediationV9Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.finalizer = load_module("p7_t1c_remediation_finalizer_v9", FINALIZER_PATH)
        cls.pipeline = load_module("p7_t2_remediation_pipeline_v9", PIPELINE_PATH)
        cls.backend = load_module("p7_t2_remediation_backend_v9", BACKEND_PATH)
        cls.builder = load_module("p7_t2_remediation_bundle_builder_v9", BUILDER_PATH)
        cls.validator = load_module("p7_t2_remediation_bundle_validator_v9", VALIDATOR_PATH)

    def test_training_approval_is_exactly_request_bound_and_fail_closed(self):
        documents = self.finalizer.build_documents(
            request_identity=REQUEST_IDENTITY,
            approved_by=APPROVAL_AUTHORITY,
            approved_at="2026-08-29T13:00:00Z",
        )
        self.finalizer.validate_documents(documents)
        approval = documents[self.finalizer.TRAINING_APPROVAL_REFERENCE]
        self.assertEqual(REQUEST_IDENTITY, approval["requestIdentity"])
        self.assertEqual(DATASET_IDENTITY, approval["datasetIdentity"])
        self.assertTrue(approval["authorization"]["externalTrainingAllowed"])
        self.assertFalse(approval["authorization"]["evaluationAllowed"])
        self.assertFalse(approval["authorization"]["promotionAllowed"])
        self.assertTrue(approval["scope"]["freshBaseModelStartRequired"])
        self.assertFalse(approval["scope"]["frozenEvaluationTrainingUseAllowed"])
        self.assertEqual("CANDIDATE_ONLY", approval["scope"]["candidateDispositionAfterTraining"])
        self.assertEqual(["E-FUNC-RESEARCH-006"], approval["scope"]["targetedEvaluationCaseIds"])

    def test_checked_in_approval_and_manifest_reproduce(self):
        approval = json.loads((ROOT / self.finalizer.TRAINING_APPROVAL_REFERENCE).read_text(encoding="utf-8"))
        documents = self.finalizer.build_documents(
            request_identity=REQUEST_IDENTITY,
            approved_by=APPROVAL_AUTHORITY,
            approved_at=approval["approval"]["approvedAt"],
        )
        for reference, document in documents.items():
            self.assertEqual(self.finalizer.json_bytes(document), (ROOT / reference).read_bytes())

    def test_pipeline_accepts_retained_v8_plus_targeted_v9_only(self):
        config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
        self.pipeline.validate_training_config(config)
        gates = self.pipeline.validate_dataset_and_contract_gates(MANIFEST_PATH, config, ROOT)
        self.assertEqual({"train": 480, "validation": 80, "evaluation": 80}, gates["counts"])
        self.assertEqual({"train": 432, "validation": 72, "evaluation": 72}, gates["retention"]["retainedRecordCounts"])
        self.assertEqual({"train": 48, "validation": 8, "evaluation": 8}, gates["retention"]["targetedAdditionCounts"])
        self.assertEqual(["E-FUNC-RESEARCH-006"], gates["targetedEvaluationCaseIds"])

    def test_backend_contract_and_counts_are_v9(self):
        self.assertEqual(480, self.backend.BASE.EXPECTED_TRAIN_RECORDS)
        self.assertEqual(80, self.backend.BASE.EXPECTED_VALIDATION_RECORDS)
        self.assertEqual(80, self.backend.BASE.EXPECTED_CONTRACT_HOLDOUT_RECORDS)
        record = json.loads((MANIFEST_PATH.parent / "train.jsonl").read_text(encoding="utf-8").splitlines()[-1])
        messages = self.backend.training_messages(record)
        self.assertEqual("RESEARCH_REPORT_REVIEW_DRAFT", json.loads(messages[2]["content"])["structuredOutput"]["kind"])

    def test_bundle_is_deterministic_valid_and_weight_free(self):
        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "first" / self.builder.BUNDLE_NAME
            second = Path(directory) / "second" / self.builder.BUNDLE_NAME
            first_zip = first.parent / f"{self.builder.BUNDLE_NAME}.zip"
            second_zip = second.parent / f"{self.builder.BUNDLE_NAME}.zip"
            kwargs = {"source_root": ROOT, "source_commit": "0" * 40, "enforce_committed_sources": False}
            first_manifest = self.builder.build_bundle(output_dir=first, zip_path=first_zip, **kwargs)
            second_manifest = self.builder.build_bundle(output_dir=second, zip_path=second_zip, **kwargs)
            self.assertEqual(first_manifest, second_manifest)
            self.assertEqual(hashlib.sha256(first_zip.read_bytes()).hexdigest(), hashlib.sha256(second_zip.read_bytes()).hexdigest())
            self.assertEqual(first_manifest, self.validator.validate_bundle(first))
            paths = {item["path"] for item in first_manifest["fileInventory"]}
            self.assertIn("datasets/p7-research-synthetic-training-dataset-v9/train.jsonl", paths)
            self.assertFalse(any(path.endswith((".safetensors", ".bin", ".pt", ".ckpt", ".pyc")) for path in paths))

            portable = subprocess.run(
                [
                    sys.executable,
                    "-B",
                    str(first / "scripts/validate-p7-t2-research-remediation-v9-bundle.py"),
                    "--bundle-root",
                    str(first),
                ],
                cwd=first,
                capture_output=True,
                text=True,
            )
            self.assertEqual(0, portable.returncode, portable.stdout + portable.stderr)


if __name__ == "__main__":
    unittest.main()
