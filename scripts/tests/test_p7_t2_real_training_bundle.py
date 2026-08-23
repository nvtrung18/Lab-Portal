import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


BUILDER = load_module(
    "p7_t2_real_bundle_builder",
    ROOT / "scripts" / "build-p7-t2-real-training-bundle.py",
)
VALIDATOR = load_module(
    "p7_t2_real_bundle_validator",
    ROOT / "scripts" / "validate-p7-t2-real-training-bundle.py",
)


class P7T2RealTrainingBundleTests(unittest.TestCase):
    def build(self, parent, suffix):
        output = Path(parent) / suffix / BUILDER.BUNDLE_NAME
        archive = output.parent / f"{BUILDER.BUNDLE_NAME}.zip"
        manifest = BUILDER.build_bundle(
            source_root=ROOT,
            output_dir=output,
            zip_path=archive,
            source_commit="f9de3c1f3838ec2815276fabce3460b0824e0909",
        )
        return output, archive, manifest

    def test_bundle_is_deterministic_and_valid(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            first, first_zip, first_manifest = self.build(temporary_directory, "first")
            second, second_zip, second_manifest = self.build(temporary_directory, "second")

            VALIDATOR.validate_bundle(first)
            VALIDATOR.validate_bundle(second)
            self.assertEqual(first_manifest, second_manifest)
            self.assertEqual(first_zip.read_bytes(), second_zip.read_bytes())
            self.assertEqual(
                hashlib.sha256(first_zip.read_bytes()).hexdigest(),
                hashlib.sha256(second_zip.read_bytes()).hexdigest(),
            )

    def test_bundle_binds_dataset_model_and_real_backend_without_model_weights(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            bundle, _, manifest = self.build(temporary_directory, "bundle")
            config = json.loads(
                (bundle / "config" / "p7-t2-training-pipeline.json").read_text(encoding="utf-8")
            )

            self.assertEqual(BUILDER.DATASET_IDENTITY, config["dataset"]["identity"])
            self.assertEqual(BUILDER.BASE_MODEL, config["baseModel"])
            self.assertIn("scripts/p7-t2-real-training.py", {item["path"] for item in manifest["fileInventory"]})
            self.assertFalse(
                any(
                    item["path"].endswith((".safetensors", ".bin", ".pt", ".ckpt"))
                    for item in manifest["fileInventory"]
                )
            )

    def test_bundle_tampering_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            bundle, _, _ = self.build(temporary_directory, "tamper")
            config_path = bundle / "config" / "p7-t2-training-pipeline.json"
            config_path.write_bytes(config_path.read_bytes() + b"\n")

            with self.assertRaisesRegex(ValueError, "checksum"):
                VALIDATOR.validate_bundle(bundle)

    def test_t4_bundle_is_additive_and_uses_the_fp16_profile(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = Path(temporary_directory) / BUILDER.T4_BUNDLE_NAME
            archive = output.parent / f"{BUILDER.T4_BUNDLE_NAME}.zip"
            manifest = BUILDER.build_bundle(
                source_root=ROOT,
                output_dir=output,
                zip_path=archive,
                source_commit="f9de3c1f3838ec2815276fabce3460b0824e0909",
                profile="t4",
            )

            VALIDATOR.validate_bundle(output)
            config = json.loads(
                (output / "config" / "p7-t2-training-pipeline.json").read_text(encoding="utf-8")
            )
            self.assertEqual("float16", config["training"]["precision"])
            self.assertEqual("float16", config["adapter"]["quantization"]["computeDtype"])
            self.assertEqual(BUILDER.DATASET_IDENTITY, config["dataset"]["identity"])
            self.assertEqual(BUILDER.BASE_MODEL, config["baseModel"])
            self.assertIn("Tesla T4", (output / "README.md").read_text(encoding="utf-8"))
            self.assertTrue(archive.is_file())
            self.assertEqual(
                manifest["trainingConfigIdentity"],
                VALIDATOR.validate_bundle(output)["trainingConfigIdentity"],
            )


if __name__ == "__main__":
    unittest.main()
