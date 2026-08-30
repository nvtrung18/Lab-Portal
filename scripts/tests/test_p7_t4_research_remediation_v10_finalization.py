import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FINALIZER_PATH = (
    ROOT / "scripts" / "finalize-p7-t4-research-remediation-v10-governance.py"
)
APPROVAL_PATH = (
    ROOT / "evidence" / "p7-t4-research-remediation-v10-governance-approval.json"
)
REQUEST_IDENTITY = (
    "fa0914adea87c54c8fc53ce79738f196045bf3f1e0031cdced28530898ed4df7"
)


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T4ResearchRemediationV10FinalizationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.finalizer = load_module("p7_t4_remediation_v10_finalizer", FINALIZER_PATH)

    def test_checked_in_approval_reproduces_byte_for_byte(self):
        artifacts = self.finalizer.build_artifacts()

        self.assertEqual(
            {"evidence/p7-t4-research-remediation-v10-governance-approval.json"},
            set(artifacts),
        )
        self.assertEqual(next(iter(artifacts.values())), APPROVAL_PATH.read_bytes())

    def test_approval_is_exactly_request_bound(self):
        approval = next(iter(self.finalizer.build_documents().values()))

        self.assertEqual("APPROVED", approval["status"])
        self.assertEqual(REQUEST_IDENTITY, approval["requestIdentity"])
        self.assertEqual(
            self.finalizer.artifact_identity(approval), approval["artifactIdentity"]
        )

    def test_approval_allows_preparation_but_not_training_or_evaluation(self):
        approval = next(iter(self.finalizer.build_documents().values()))
        authorization = approval["authorization"]

        self.assertTrue(authorization["datasetV10PreparationAllowed"])
        self.assertTrue(authorization["v9AdapterWarmStartProposalAllowed"])
        self.assertTrue(authorization["v9TrainValidationReplaySelectionAllowed"])
        self.assertFalse(authorization["v9EvaluationReplayAllowed"])
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
