import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def load_module(name, relative_path):
    specification = importlib.util.spec_from_file_location(name, ROOT / relative_path)
    module = importlib.util.module_from_spec(specification)
    assert specification.loader is not None
    specification.loader.exec_module(module)
    return module


BUILDER = load_module(
    "p7t4_bundle_builder", "scripts/build-p7-t4-research-evaluation-bundle.py"
)
VALIDATOR = load_module(
    "p7t4_bundle_validator", "scripts/validate-p7-t4-research-evaluation-bundle.py"
)


class P7T4ResearchEvaluationBundleTests(unittest.TestCase):
    def test_bundle_sources_exclude_training_data_checkpoints_and_base_weights(self):
        sources = set(BUILDER.SOURCE_FILES)

        self.assertIn("scripts/research-independent-evaluation-p7-t4.py", sources)
        self.assertIn("evals/p6-t4-evaluation-suites.yaml", sources)
        self.assertFalse(any(value.startswith("datasets/") for value in sources))
        self.assertFalse(any("checkpoint" in value.lower() for value in sources))
        self.assertFalse(any(Path(value).name in {"model.safetensors", "pytorch_model.bin"} for value in sources))

    def test_bundle_inventory_rejects_payload_tampering(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            bundle_root = Path(temporary_directory)
            payload = bundle_root / "payload.txt"
            payload.write_text("governed", encoding="utf-8")
            inventory = BUILDER.bundle_inventory(bundle_root)
            manifest = {
                "artifactType": "P7-T4-RESEARCH-EVALUATION-BUNDLE",
                "schemaVersion": "1.0.0",
                "sourceCommit": "a" * 40,
                "candidateId": "b" * 64,
                "adapterIdentity": "c" * 64,
                "suite": {"id": "suite", "version": "1", "digest": "d" * 64},
                "files": inventory,
            }
            manifest["bundleIdentity"] = BUILDER.manifest_identity(manifest)
            (bundle_root / "bundle-manifest.json").write_text(
                json.dumps(manifest, sort_keys=True), encoding="utf-8"
            )

            VALIDATOR.validate_bundle_inventory(bundle_root, manifest)
            payload.write_text("tampered", encoding="utf-8")

            with self.assertRaisesRegex(VALIDATOR.BundleValidationError, "inventory"):
                VALIDATOR.validate_bundle_inventory(bundle_root, manifest)

    def test_bundle_inventory_rejects_unlisted_file(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            bundle_root = Path(temporary_directory)
            (bundle_root / "payload.txt").write_text("governed", encoding="utf-8")
            inventory = BUILDER.bundle_inventory(bundle_root)
            manifest = {
                "artifactType": "P7-T4-RESEARCH-EVALUATION-BUNDLE",
                "schemaVersion": "1.0.0",
                "sourceCommit": "a" * 40,
                "candidateId": "b" * 64,
                "adapterIdentity": "c" * 64,
                "suite": {"id": "suite", "version": "1", "digest": "d" * 64},
                "files": inventory,
            }
            manifest["bundleIdentity"] = BUILDER.manifest_identity(manifest)
            (bundle_root / "bundle-manifest.json").write_text(
                json.dumps(manifest, sort_keys=True), encoding="utf-8"
            )
            (bundle_root / "unlisted.txt").write_text("unexpected", encoding="utf-8")

            with self.assertRaisesRegex(VALIDATOR.BundleValidationError, "inventory"):
                VALIDATOR.validate_bundle_inventory(bundle_root, manifest)


if __name__ == "__main__":
    unittest.main()
