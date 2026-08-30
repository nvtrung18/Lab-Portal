import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FINALIZER_PATH = (
    ROOT / "scripts" / "finalize-p7-t4-research-remediation-v8-governance.py"
)
APPROVAL_PATH = (
    ROOT / "evidence" / "p7-t4-research-remediation-v8-governance-approval.json"
)


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T4ResearchRemediationV8FinalizationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.finalizer = load_module("p7_t4_remediation_v8_finalizer", FINALIZER_PATH)

    def test_checked_in_approval_reproduces_byte_for_byte(self):
        artifacts = self.finalizer.build_artifacts()

        self.assertEqual(
            {
                "evidence/p7-t4-research-remediation-v8-governance-approval.json"
            },
            set(artifacts),
        )
        self.assertEqual(next(iter(artifacts.values())), APPROVAL_PATH.read_bytes())

    def test_approval_authorizes_only_v8_dataset_preparation(self):
        approval = next(iter(self.finalizer.build_documents().values()))
        authorization = approval["authorization"]

        self.assertEqual("APPROVED", approval["status"])
        self.assertEqual(
            "d5f66d6287dbf14c2ca27620705ca4567a13e1af4211b5dceecebcba8fef5889",
            approval["requestIdentity"],
        )
        self.assertTrue(authorization["datasetV8PreparationAllowed"])
        self.assertTrue(authorization["approvedV7RetentionReuseAllowed"])
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
