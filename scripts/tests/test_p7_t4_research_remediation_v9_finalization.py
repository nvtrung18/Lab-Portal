import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FINALIZER_PATH = (
    ROOT / "scripts" / "finalize-p7-t4-research-remediation-v9-governance.py"
)
APPROVAL_PATH = (
    ROOT / "evidence" / "p7-t4-research-remediation-v9-governance-approval.json"
)


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T4ResearchRemediationV9FinalizationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.finalizer = load_module("p7_t4_remediation_v9_finalizer", FINALIZER_PATH)

    def test_checked_in_approval_reproduces_byte_for_byte(self):
        artifacts = self.finalizer.build_artifacts()

        self.assertEqual(
            {
                "evidence/p7-t4-research-remediation-v9-governance-approval.json"
            },
            set(artifacts),
        )
        self.assertEqual(next(iter(artifacts.values())), APPROVAL_PATH.read_bytes())

    def test_approval_authorizes_only_v9_dataset_preparation(self):
        approval = next(iter(self.finalizer.build_documents().values()))
        authorization = approval["authorization"]

        self.assertEqual("APPROVED", approval["status"])
        self.assertEqual(
            "c3ff0765440913682aef66e087fff95ace4cc0693703938a7fc5e8f6db99be05",
            approval["requestIdentity"],
        )
        self.assertTrue(authorization["datasetV9PreparationAllowed"])
        self.assertTrue(authorization["approvedV8RetentionReuseAllowed"])
        self.assertTrue(authorization["promptProfileV3ReuseAllowed"])
        self.assertFalse(authorization["externalTrainingAllowed"])
        self.assertFalse(authorization["externalEvaluationExecutionAllowed"])
        self.assertFalse(authorization["promotionAllowed"])
        self.assertFalse(authorization["evaluatorOrSuiteMutationAllowed"])
        self.assertFalse(authorization["runtimeNormalizationAllowed"])
        self.assertFalse(authorization["constrainedDecodingAllowed"])
        self.assertTrue(authorization["separateTrainingApprovalRequired"])
        self.assertTrue(authorization["separateExternalEvaluationApprovalRequired"])


if __name__ == "__main__":
    unittest.main()
