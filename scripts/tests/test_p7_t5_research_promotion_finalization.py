import hashlib
import importlib.util
import json
import unittest
from copy import deepcopy
from pathlib import Path
from tempfile import TemporaryDirectory


ROOT = Path(__file__).resolve().parents[2]
FINALIZER_PATH = ROOT / "scripts" / "finalize-p7-t5-research-promotion.py"


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T5ResearchPromotionFinalizationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.finalizer = load_module("p7_t5_research_promotion_finalizer", FINALIZER_PATH)

    def test_checked_in_final_artifacts_reproduce_byte_for_byte(self):
        for relative_path, content in self.finalizer.build_artifacts().items():
            self.assertEqual(content, (ROOT / relative_path).read_bytes())

    def test_final_documents_bind_exact_approval_and_remain_not_loaded(self):
        documents = self.finalizer.build_documents()
        approval = documents["approval"]
        manifest = documents["model-manifest.json"]
        registry = documents["model-registry.json"]
        rollback = documents["rollback-manifest.json"]
        decision = documents["decision.json"]
        descriptor = documents["model-artifacts.json"]
        profiles = documents["assistant-profiles.json"]

        self.assertEqual(
            "e40580fbfccd40bdebb8bf72acf0d53f066f6952effb8cb0d7550218f6845f42",
            approval["requestIdentity"],
        )
        self.assertEqual("APPROVED", manifest["status"])
        self.assertEqual("APPROVED", registry["status"])
        self.assertEqual("COMPLETE", decision["state"])
        self.assertTrue(decision["promotionAllowed"])
        self.assertFalse(decision["servingLoadAllowed"])
        self.assertEqual("DISABLE_ASSISTANT", rollback["action"])
        self.assertEqual("BLOCKED", rollback["restoredAdapterStatus"])
        self.assertFalse(rollback["servingAllowed"])

        research = descriptor["assistantAdapters"]["RESEARCH_ASSISTANT"]
        profile = profiles["profiles"]["RESEARCH_ASSISTANT"]
        self.assertEqual("APPROVED", research["status"])
        self.assertEqual(research["identifier"], profile["adapter"]["identifier"])
        self.assertEqual(research["version"], profile["adapter"]["version"])
        self.assertEqual(
            research["artifact"]["identity"],
            profile["adapter"]["artifactChecksum"],
        )
        self.assertEqual("METADATA_ONLY", profile["modelProfile"]["servingMode"])

    def test_tampered_approval_fails_closed(self):
        approval = json.loads(self.finalizer.APPROVAL_PATH.read_text(encoding="utf-8"))
        tampered = deepcopy(approval)
        tampered["requestIdentity"] = "0" * 64

        with self.assertRaisesRegex(self.finalizer.PromotionFinalizationError, "approval"):
            self.finalizer.build_documents(approval=tampered)

    def test_adapter_materialization_is_idempotent_and_rejects_tampering(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"
            target = root / "target"
            source.mkdir()
            payload = b"approved-adapter"
            (source / "adapter.bin").write_bytes(payload)
            entry = {
                "path": "research-assistant/1.0.0/adapter.bin",
                "sha256": hashlib.sha256(payload).hexdigest(),
                "sizeBytes": len(payload),
            }
            manifest = {"artifact": {"files": [entry]}}

            self.finalizer.materialize_adapter(manifest, source, target)
            self.finalizer.materialize_adapter(manifest, source, target)
            self.assertEqual(payload, (target / entry["path"]).read_bytes())

            (source / "adapter.bin").write_bytes(b"tampered")
            with self.assertRaisesRegex(
                self.finalizer.PromotionFinalizationError, "checksum"
            ):
                self.finalizer.materialize_adapter(manifest, source, target)


if __name__ == "__main__":
    unittest.main()
