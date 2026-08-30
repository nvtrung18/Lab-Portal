import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FINALIZER_PATH = ROOT / "scripts" / "finalize-p7-t1c-research-remediation-governance-v4.py"
PIPELINE_PATH = ROOT / "scripts" / "training-pipeline-p7-t2-remediation-v4.py"
BACKEND_PATH = ROOT / "scripts" / "p7-t2-real-training-remediation-v4.py"
BUILDER_PATH = ROOT / "scripts" / "build-p7-t2-research-remediation-v4-bundle.py"
VALIDATOR_PATH = ROOT / "scripts" / "validate-p7-t2-research-remediation-v4-bundle.py"
CONFIG_PATH = ROOT / "config" / "p7-t2-training-pipeline-t4-remediation-v4.json"
DATASET_MANIFEST = (
    ROOT / "datasets" / "p7-research-synthetic-training-dataset-v4" / "manifest.approved.json"
)
REQUEST_IDENTITY = "d052e13698d1d4902a0b5018b2563889d2ea8fb52c55f0112d193efd088f6a9e"
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_DATA_GOVERNANCE_APPROVAL_AUTHORITY"


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T2ResearchRemediationV4TrainingBundleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.finalizer = load_module("p7_t1c_remediation_finalizer_v4", FINALIZER_PATH)
        cls.pipeline = load_module("p7_t2_remediation_pipeline_v4", PIPELINE_PATH)
        cls.backend = load_module("p7_t2_remediation_backend_v4", BACKEND_PATH)
        cls.builder = load_module("p7_t2_remediation_bundle_builder_v4", BUILDER_PATH)
        cls.validator = load_module("p7_t2_remediation_bundle_validator_v4", VALIDATOR_PATH)
        cls.config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))

    def test_finalizer_binds_exact_request_and_narrow_training_scope(self):
        documents = self.finalizer.build_documents(
            request_identity=REQUEST_IDENTITY,
            approved_by=APPROVAL_AUTHORITY,
            approved_at="2026-08-26T00:00:00Z",
        )
        self.finalizer.validate_documents(documents)
        self.assertEqual(
            {
                self.finalizer.TRAINING_APPROVAL_REFERENCE,
                self.finalizer.APPROVED_CARD_REFERENCE,
                self.finalizer.APPROVED_MANIFEST_REFERENCE,
            },
            set(documents),
        )
        approval = documents[self.finalizer.TRAINING_APPROVAL_REFERENCE]
        self.assertEqual(REQUEST_IDENTITY, approval["requestIdentity"])
        self.assertTrue(approval["authorization"]["externalTrainingAllowed"])
        self.assertFalse(approval["authorization"]["evaluationAllowed"])
        self.assertFalse(approval["authorization"]["promotionAllowed"])
        self.assertEqual(
            ["RESEARCH_UC_003", "RESEARCH_UC_004", "RESEARCH_UC_005", "RESEARCH_UC_006"],
            approval["scope"]["includedUseCases"],
        )
        self.assertFalse(approval["scope"]["frozenEvaluationTrainingUseAllowed"])
        approved_manifest = documents[self.finalizer.APPROVED_MANIFEST_REFERENCE]
        self.assertTrue(approved_manifest["trainingAuthorized"])
        self.assertEqual("APPROVED", approved_manifest["approval_status"])
        self.assertEqual(
            approved_manifest["checksum"],
            self.finalizer.artifact_identity(approved_manifest, "checksum"),
        )

    def test_finalizer_rejects_any_other_request_identity(self):
        with self.assertRaisesRegex(self.finalizer.FinalizationError, "request identity"):
            self.finalizer.build_documents(
                request_identity="0" * 64,
                approved_by=APPROVAL_AUTHORITY,
                approved_at="2026-08-26T00:00:00Z",
            )

    def test_config_and_dataset_contract_gates_pass(self):
        self.pipeline.validate_training_config(self.config)
        gates = self.pipeline.validate_dataset_and_contract_gates(
            DATASET_MANIFEST,
            self.config,
            ROOT,
        )
        self.assertEqual(
            {"train": 112, "validation": 16, "evaluation": 16},
            gates["counts"],
        )
        self.assertEqual("research-assistant-output-v2", gates["preparedRuntimeContract"]["schemaBundle"])
        self.assertEqual("PASS", gates["baselineStabilityControls"]["state"])
        self.assertEqual(32768, self.config["training"]["gradientScalerInitialScale"])

    def test_training_messages_accept_v4_report_review_records(self):
        record = next(
            json.loads(line)
            for line in (DATASET_MANIFEST.parent / "train.jsonl").read_text(encoding="utf-8").splitlines()
            if json.loads(line)["useCaseId"] == "RESEARCH_UC_006"
        )
        messages = self.backend.training_messages(record)
        self.assertEqual(["system", "user", "assistant"], [item["role"] for item in messages])
        self.assertEqual(
            self.backend.canonical_bytes(record["trainingTarget"]).decode("utf-8"),
            messages[2]["content"],
        )

    def test_bundle_is_deterministic_valid_and_weight_free(self):
        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "first" / self.builder.BUNDLE_NAME
            second = Path(directory) / "second" / self.builder.BUNDLE_NAME
            first_zip = first.parent / f"{self.builder.BUNDLE_NAME}.zip"
            second_zip = second.parent / f"{self.builder.BUNDLE_NAME}.zip"
            first_manifest = self.builder.build_bundle(
                source_root=ROOT,
                output_dir=first,
                zip_path=first_zip,
                source_commit="23f920f4da5b4f7266d0991af3af34921994600b",
                enforce_committed_sources=False,
            )
            second_manifest = self.builder.build_bundle(
                source_root=ROOT,
                output_dir=second,
                zip_path=second_zip,
                source_commit="23f920f4da5b4f7266d0991af3af34921994600b",
                enforce_committed_sources=False,
            )
            self.assertEqual(first_manifest, second_manifest)
            self.assertEqual(first_zip.read_bytes(), second_zip.read_bytes())
            self.assertEqual(
                hashlib.sha256(first_zip.read_bytes()).hexdigest(),
                hashlib.sha256(second_zip.read_bytes()).hexdigest(),
            )
            self.assertEqual(first_manifest, self.validator.validate_bundle(first))
            paths = {item["path"] for item in first_manifest["fileInventory"]}
            self.assertIn("datasets/p7-research-synthetic-training-dataset-v4/train.jsonl", paths)
            self.assertIn("scripts/p7-t2-real-training-remediation-v4.py", paths)
            self.assertFalse(
                any(path.endswith((".safetensors", ".bin", ".pt", ".ckpt")) for path in paths)
            )

    def test_bundle_tampering_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory) / self.builder.BUNDLE_NAME
            archive = bundle.parent / f"{self.builder.BUNDLE_NAME}.zip"
            self.builder.build_bundle(
                source_root=ROOT,
                output_dir=bundle,
                zip_path=archive,
                source_commit="23f920f4da5b4f7266d0991af3af34921994600b",
                enforce_committed_sources=False,
            )
            config = bundle / "config/p7-t2-training-pipeline-t4-remediation-v4.json"
            config.write_bytes(config.read_bytes() + b"\n")
            with self.assertRaisesRegex(ValueError, "checksum"):
                self.validator.validate_bundle(bundle)

    def test_non_finite_metrics_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "non-finite logged metric.*grad_norm"):
            self.backend.validate_finite_log_history(
                [{"loss": 0.5, "grad_norm": float("nan"), "epoch": 0.25}],
                "real remediation v4 training",
            )


if __name__ == "__main__":
    unittest.main()
