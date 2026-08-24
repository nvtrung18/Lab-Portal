import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t2-research-remediation-bundle.py"
VALIDATOR_PATH = ROOT / "scripts" / "validate-p7-t2-research-remediation-bundle.py"


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class P7T2ResearchRemediationBundleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("p7_t2_remediation_bundle_builder", BUILDER_PATH)
        cls.validator = load_module("p7_t2_remediation_bundle_validator", VALIDATOR_PATH)

    def build(self, parent: str, suffix: str):
        output = Path(parent) / suffix / self.builder.BUNDLE_NAME
        archive = output.parent / f"{self.builder.BUNDLE_NAME}.zip"
        manifest = self.builder.build_bundle(
            source_root=ROOT,
            output_dir=output,
            zip_path=archive,
            source_commit="804b18ec8336bf91cc0f71c2661683da48540188",
            enforce_committed_sources=False,
        )
        return output, archive, manifest

    def test_bundle_is_deterministic_and_valid(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            first, first_zip, first_manifest = self.build(temporary_directory, "first")
            second, second_zip, second_manifest = self.build(temporary_directory, "second")
            self.assertEqual(first_manifest, second_manifest)
            self.assertEqual(first_zip.read_bytes(), second_zip.read_bytes())
            self.assertEqual(
                hashlib.sha256(first_zip.read_bytes()).hexdigest(),
                hashlib.sha256(second_zip.read_bytes()).hexdigest(),
            )
            self.validator.validate_bundle(first)

    def test_bundle_binds_remediation_dataset_approval_and_training_policy(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            bundle, _, manifest = self.build(temporary_directory, "bundle")
            config = json.loads(
                (bundle / "config/p7-t2-training-pipeline-t4-remediation.json").read_text(
                    encoding="utf-8"
                )
            )
            paths = {item["path"] for item in manifest["fileInventory"]}
            self.assertEqual(self.builder.DATASET_IDENTITY, manifest["datasetIdentity"])
            self.assertEqual(self.builder.TRAINING_APPROVAL_IDENTITY, manifest["trainingApprovalIdentity"])
            self.assertEqual("epoch", config["training"]["evaluationStrategy"])
            self.assertTrue(config["training"]["loadBestModelAtEnd"])
            self.assertIn("scripts/p7-t2-real-training-remediation.py", paths)
            self.assertIn("ai-service/config/schemas/structured-output-schemas.json", paths)
            self.assertFalse(
                any(path.endswith((".safetensors", ".bin", ".pt", ".ckpt")) for path in paths)
            )

    def test_bundle_tampering_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            bundle, _, _ = self.build(temporary_directory, "tamper")
            config = bundle / "config/p7-t2-training-pipeline-t4-remediation.json"
            config.write_bytes(config.read_bytes() + b"\n")
            with self.assertRaisesRegex(ValueError, "checksum"):
                self.validator.validate_bundle(bundle)

    def test_invalid_source_commit_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "full source commit"):
            self.builder.verify_committed_sources(ROOT, "804b18e")


if __name__ == "__main__":
    unittest.main()
