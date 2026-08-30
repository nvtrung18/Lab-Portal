import hashlib
import importlib.util
import json
import unittest
from copy import deepcopy
from pathlib import Path
from tempfile import TemporaryDirectory


ROOT = Path(__file__).resolve().parents[2]
VALIDATOR_PATH = ROOT / "scripts" / "validate-p7-t5-research-promotion.py"


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T5ResearchPromotionValidationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.validator = load_module("p7_t5_research_promotion_validator", VALIDATOR_PATH)

    def test_checked_in_proposal_is_approved_but_needs_adapter_verification(self):
        result = self.validator.validate()

        self.assertEqual("APPROVED_PENDING_ADAPTER_VERIFICATION", result["state"])
        self.assertFalse(result["promotionMaterialized"])
        self.assertFalse(result["servingAllowed"])
        self.assertEqual(
            "e40580fbfccd40bdebb8bf72acf0d53f066f6952effb8cb0d7550218f6845f42",
            result["requestIdentity"],
        )

    def test_adapter_inventory_is_verified_by_size_and_checksum(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            payload = b"adapter"
            (root / "adapter.bin").write_bytes(payload)
            manifest = {
                "artifact": {
                    "files": [
                        {
                            "path": "research-assistant/1.0.0/adapter.bin",
                            "sha256": hashlib.sha256(payload).hexdigest(),
                            "sizeBytes": len(payload),
                        }
                    ]
                }
            }

            self.validator.validate_adapter_root(manifest, root)

            (root / "adapter.bin").write_bytes(b"tampered")
            with self.assertRaisesRegex(
                self.validator.PromotionValidationError, "checksum"
            ):
                self.validator.validate_adapter_root(manifest, root)

    def test_missing_or_mismatched_exact_approval_fails_closed(self):
        request = json.loads(
            (ROOT / "config/p7-t5-research-promotion/promotion-request.json").read_text(
                encoding="utf-8"
            )
        )
        approval = {
            "artifactType": "P7-T5-RESEARCH-PROMOTION-APPROVAL",
            "schemaVersion": "1.0.0",
            "status": "APPROVED",
            "requestIdentity": "0" * 64,
            "authorization": {
                "promotionAllowed": True,
                "registryMaterializationAllowed": True,
                "adapterCopyAllowed": True,
                "servingLoadAllowed": False,
            },
        }

        with self.assertRaisesRegex(
            self.validator.PromotionValidationError, "exact approval"
        ):
            self.validator.validate_approval(request, approval)

        exact = deepcopy(approval)
        exact["requestIdentity"] = request["requestIdentity"]
        self.validator.validate_approval(request, exact)


if __name__ == "__main__":
    unittest.main()
