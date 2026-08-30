import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CONFIG_PATH = ROOT / "config/p7-t2-training-pipeline-t4-remediation-v3-stability.json"
PIPELINE_PATH = ROOT / "scripts/training-pipeline-p7-t2-remediation-v3.py"
BUILDER_PATH = ROOT / "scripts/build-p7-t2-research-remediation-v3-stability-bundle.py"
VALIDATOR_PATH = ROOT / "scripts/validate-p7-t2-research-remediation-v3-stability-bundle.py"
DATASET_MANIFEST = ROOT / "datasets/p7-research-synthetic-training-dataset-v3/manifest.json"
APPROVAL_PATH = (
    ROOT
    / "evidence/p7-t2-real-training/remediation-v3-quarantine/stability-retry-approval.json"
)


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T2ResearchRemediationV3StabilityBundleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pipeline = load_module("p7_t2_stability_pipeline", PIPELINE_PATH)
        cls.builder = load_module("p7_t2_stability_builder", BUILDER_PATH)
        cls.validator = load_module("p7_t2_stability_validator", VALIDATOR_PATH)
        cls.config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))

    def build(self, parent: str, suffix: str):
        output = Path(parent) / suffix / self.builder.BUNDLE_NAME
        archive = output.parent / f"{self.builder.BUNDLE_NAME}.zip"
        manifest = self.builder.build_bundle(
            source_root=ROOT,
            output_dir=output,
            zip_path=archive,
            source_commit="304804c096fe2c0299d4ec4e229eca637dc9a5c0",
            enforce_committed_sources=False,
        )
        return output, archive, manifest

    def test_retry_approval_and_runtime_gate_are_exact(self):
        approval = json.loads(APPROVAL_PATH.read_text(encoding="utf-8"))
        self.assertEqual(
            "67520e81e5c0bc9a326f17b437c1b4193a4f4ebf9b8a79d515d7b99266debfda",
            approval["artifactIdentity"],
        )
        self.assertEqual(
            approval["artifactIdentity"],
            self.pipeline.artifact_identity(approval, "artifactIdentity"),
        )
        self.assertEqual("APPROVED", approval["status"])
        self.assertEqual("ACTIVE", approval["revocation"]["status"])

        gates = self.pipeline.validate_dataset_and_contract_gates(
            DATASET_MANIFEST,
            self.config,
            ROOT,
        )
        self.assertEqual(
            {
                "state": "PASS",
                "approvalIdentity": approval["artifactIdentity"],
                "maximumRuns": 1,
                "freshBaseModelStartRequired": True,
            },
            gates["stabilityRetryApproval"],
        )

    def test_stability_bundle_is_deterministic_approved_and_weight_free(self):
        with tempfile.TemporaryDirectory() as directory:
            first, first_zip, first_manifest = self.build(directory, "first")
            second, second_zip, second_manifest = self.build(directory, "second")
            self.assertEqual(first_manifest, second_manifest)
            self.assertEqual(first_zip.read_bytes(), second_zip.read_bytes())
            self.assertEqual(
                hashlib.sha256(first_zip.read_bytes()).hexdigest(),
                hashlib.sha256(second_zip.read_bytes()).hexdigest(),
            )
            self.assertEqual(first_manifest, self.validator.validate_bundle(first))
            self.assertEqual(
                "67520e81e5c0bc9a326f17b437c1b4193a4f4ebf9b8a79d515d7b99266debfda",
                first_manifest["executionApprovalIdentity"],
            )
            self.assertEqual(
                "58f945c432208e032edcd4180116de8540b429cc4a5d070a4c90e7f9a8111667",
                first_manifest["trainingConfigIdentity"],
            )
            paths = {item["path"] for item in first_manifest["fileInventory"]}
            self.assertIn(
                "evidence/p7-t2-real-training/remediation-v3-quarantine/stability-retry-approval.json",
                paths,
            )
            self.assertIn(
                "evidence/p7-t2-real-training/remediation-v3-quarantine/incident.json",
                paths,
            )
            self.assertFalse(
                any(path.endswith((".safetensors", ".bin", ".pt", ".ckpt")) for path in paths)
            )

    def test_stability_bundle_tampering_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle, _, _ = self.build(directory, "tamper")
            approval = (
                bundle
                / "evidence/p7-t2-real-training/remediation-v3-quarantine/stability-retry-approval.json"
            )
            approval.write_bytes(approval.read_bytes() + b"\n")
            with self.assertRaisesRegex(ValueError, "inventory|checksum"):
                self.validator.validate_bundle(bundle)


if __name__ == "__main__":
    unittest.main()
