import importlib.util
import hashlib
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FINALIZER_PATH = (
    ROOT / "scripts" / "finalize-p7-t1c-research-remediation-governance-v5.py"
)
REQUEST_IDENTITY = (
    "780a5deeb83e30a38e229d91c54cbb8c0c56fd0ef1717a402df8440bc23e06f0"
)
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_DATA_GOVERNANCE_APPROVAL_AUTHORITY"
PIPELINE_PATH = ROOT / "scripts" / "training-pipeline-p7-t2-remediation-v5.py"
BACKEND_PATH = ROOT / "scripts" / "p7-t2-real-training-remediation-v5.py"
BUILDER_PATH = ROOT / "scripts" / "build-p7-t2-research-remediation-v5-bundle.py"
VALIDATOR_PATH = ROOT / "scripts" / "validate-p7-t2-research-remediation-v5-bundle.py"
CONFIG_PATH = ROOT / "config" / "p7-t2-training-pipeline-t4-remediation-v5.json"
DATASET_MANIFEST = (
    ROOT / "datasets" / "p7-research-synthetic-training-dataset-v5"
    / "manifest.approved.json"
)


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T2ResearchRemediationV5TrainingGovernanceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.finalizer = load_module("p7_t1c_remediation_finalizer_v5", FINALIZER_PATH)

    def test_finalizer_binds_exact_request_and_narrow_training_scope(self):
        documents = self.finalizer.build_documents(
            request_identity=REQUEST_IDENTITY,
            approved_by=APPROVAL_AUTHORITY,
            approved_at="2026-08-27T00:00:00Z",
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
        self.assertFalse(approval["authorization"]["runtimeNormalizationAllowed"])
        self.assertFalse(approval["authorization"]["constrainedDecodingAllowed"])
        self.assertEqual(
            [
                "RESEARCH_UC_003",
                "RESEARCH_UC_004",
                "RESEARCH_UC_005",
                "RESEARCH_UC_006",
            ],
            approval["scope"]["includedUseCases"],
        )
        self.assertFalse(approval["scope"]["frozenEvaluationTrainingUseAllowed"])
        self.assertEqual(
            "53d48c6489ecd7bb4f7a4a1c85bbe2813454c94c520310f28c74499d4bfdae05",
            approval["evaluatorSuiteApprovalIdentity"],
        )

        manifest = documents[self.finalizer.APPROVED_MANIFEST_REFERENCE]
        self.assertTrue(manifest["trainingAuthorized"])
        self.assertEqual("APPROVED", manifest["approval_status"])
        self.assertEqual(
            {"evaluation": 24, "train": 144, "validation": 24},
            manifest["counts"]["splits"],
        )
        self.assertEqual(
            manifest["checksum"],
            self.finalizer.artifact_identity(manifest, "checksum"),
        )

    def test_finalizer_rejects_any_other_request_identity(self):
        with self.assertRaisesRegex(self.finalizer.FinalizationError, "request identity"):
            self.finalizer.build_documents(
                request_identity="0" * 64,
                approved_by=APPROVAL_AUTHORITY,
                approved_at="2026-08-27T00:00:00Z",
            )

    def test_checked_in_approval_reproduces_byte_for_byte(self):
        approval = json.loads(
            (ROOT / self.finalizer.TRAINING_APPROVAL_REFERENCE).read_text(
                encoding="utf-8"
            )
        )
        documents = self.finalizer.build_documents(
            request_identity=REQUEST_IDENTITY,
            approved_by=APPROVAL_AUTHORITY,
            approved_at=approval["approval"]["approvedAt"],
        )
        for reference, document in documents.items():
            self.assertEqual(
                self.finalizer.json_bytes(document),
                (ROOT / reference).read_bytes(),
            )


class P7T2ResearchRemediationV5TrainingBundleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pipeline = load_module("p7_t2_remediation_pipeline_v5", PIPELINE_PATH)
        cls.backend = load_module("p7_t2_remediation_backend_v5", BACKEND_PATH)
        cls.builder = load_module("p7_t2_remediation_bundle_builder_v5", BUILDER_PATH)
        cls.validator = load_module("p7_t2_remediation_bundle_validator_v5", VALIDATOR_PATH)
        cls.config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))

    def test_config_and_dataset_contract_gates_pass(self):
        self.pipeline.validate_training_config(self.config)
        gates = self.pipeline.validate_dataset_and_contract_gates(
            DATASET_MANIFEST,
            self.config,
            ROOT,
        )
        self.assertEqual(
            {"train": 144, "validation": 24, "evaluation": 24},
            gates["counts"],
        )
        self.assertEqual("2.0.0", gates["evaluatorContract"]["version"])
        self.assertEqual("2.0.0", gates["evaluationSuite"]["version"])
        self.assertFalse(gates["runtimeControls"]["runtimeNormalizationAllowed"])
        self.assertFalse(gates["runtimeControls"]["constrainedDecodingAllowed"])

    def test_training_messages_preserve_canonical_v5_targets(self):
        record = next(
            json.loads(line)
            for line in (DATASET_MANIFEST.parent / "train.jsonl")
            .read_text(encoding="utf-8")
            .splitlines()
            if json.loads(line)["useCaseId"] == "RESEARCH_UC_006"
        )
        messages = self.backend.training_messages(record)
        self.assertEqual(
            ["system", "user", "assistant"],
            [item["role"] for item in messages],
        )
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
            kwargs = {
                "source_root": ROOT,
                "source_commit": "982af7264c9977edf739b897721106cacbfea4d3",
                "enforce_committed_sources": False,
            }
            first_manifest = self.builder.build_bundle(
                output_dir=first, zip_path=first_zip, **kwargs
            )
            second_manifest = self.builder.build_bundle(
                output_dir=second, zip_path=second_zip, **kwargs
            )
            self.assertEqual(first_manifest, second_manifest)
            self.assertEqual(first_zip.read_bytes(), second_zip.read_bytes())
            self.assertEqual(
                hashlib.sha256(first_zip.read_bytes()).hexdigest(),
                hashlib.sha256(second_zip.read_bytes()).hexdigest(),
            )
            self.assertEqual(first_manifest, self.validator.validate_bundle(first))
            paths = {item["path"] for item in first_manifest["fileInventory"]}
            self.assertIn(
                "datasets/p7-research-synthetic-training-dataset-v5/train.jsonl",
                paths,
            )
            self.assertIn("scripts/p7-t2-real-training-remediation-v5.py", paths)
            self.assertIn("scripts/validate-p7-t4-research-evaluation-v2.py", paths)
            self.assertFalse(
                any(
                    path.endswith((".safetensors", ".bin", ".pt", ".ckpt"))
                    for path in paths
                )
            )

    def test_bundle_tampering_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory) / self.builder.BUNDLE_NAME
            archive = bundle.parent / f"{self.builder.BUNDLE_NAME}.zip"
            self.builder.build_bundle(
                source_root=ROOT,
                output_dir=bundle,
                zip_path=archive,
                source_commit="982af7264c9977edf739b897721106cacbfea4d3",
                enforce_committed_sources=False,
            )
            config = bundle / "config/p7-t2-training-pipeline-t4-remediation-v5.json"
            config.write_bytes(config.read_bytes() + b"\n")
            with self.assertRaisesRegex(ValueError, "checksum"):
                self.validator.validate_bundle(bundle)

    def test_non_finite_metrics_are_rejected(self):
        with self.assertRaisesRegex(
            ValueError, "non-finite logged metric.*grad_norm"
        ):
            self.backend.validate_finite_log_history(
                [{"loss": 0.5, "grad_norm": float("nan"), "epoch": 0.25}],
                "real remediation v5 training",
            )


if __name__ == "__main__":
    unittest.main()
