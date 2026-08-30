import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-v8-preparation.py"
CONFIG_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v8"


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T4ResearchRemediationV8PreparationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("p7_t4_remediation_v8_preparation", BUILDER_PATH)

    def test_checked_in_packet_reproduces_byte_for_byte(self):
        artifacts = self.builder.build_artifacts()

        self.assertEqual(
            {
                "failure-analysis-v7.json",
                "governance-amendment-request.json",
                "training-data-quality-spec-v8.json",
            },
            set(artifacts),
        )
        for filename, expected in artifacts.items():
            self.assertEqual(expected, (CONFIG_DIRECTORY / filename).read_bytes())

    def test_request_is_pending_and_binds_exact_v7_failure(self):
        request = self.builder.build_documents()["governance-amendment-request.json"]

        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertFalse(request["currentState"]["datasetMaterializationAuthorized"])
        self.assertFalse(request["currentState"]["trainingAuthorized"])
        self.assertFalse(request["currentState"]["evaluationExecutionAuthorized"])
        self.assertFalse(request["currentState"]["promotionAllowed"])
        self.assertEqual(
            "35c2126b5ab86ec39dce6f14442c60f9d7a1937bee1a94da13060340c0b612b0",
            request["remediationBinding"]["failedComparisonIdentity"],
        )
        self.assertEqual(
            "b2e61ccba5e79dde268d5cb96e1426fbd0d42136a0b8cdfbd1f4a414e523a9e0",
            request["remediationBinding"]["failedCandidateId"],
        )
        self.assertEqual(
            self.builder.request_identity(request), request["requestIdentity"]
        )

    def test_only_e_inject_001_is_targeted_and_all_passes_are_protected(self):
        documents = self.builder.build_documents()
        analysis = documents["failure-analysis-v7.json"]
        quality = documents["training-data-quality-spec-v8.json"]

        self.assertEqual(["E-INJECT-001"], analysis["targetedFailedCaseIds"])
        self.assertEqual(17, len(analysis["preservedPassingCaseIds"]))
        self.assertNotIn("E-INJECT-001", analysis["preservedPassingCaseIds"])
        self.assertEqual(
            analysis["preservedPassingCaseIds"],
            quality["v7RetentionControls"]["semanticPassCasesToProtect"],
        )
        self.assertEqual(
            {"evaluation": 64, "train": 384, "validation": 64},
            quality["v7RetentionControls"]["retainedRecordCounts"],
        )
        self.assertTrue(
            quality["v7RetentionControls"]["retainApprovedV7SyntheticRecords"]
        )
        self.assertTrue(
            quality["v7RetentionControls"]["preserveOriginalSplitAssignment"]
        )
        self.assertFalse(
            quality["v7RetentionControls"]["v7EvaluationRecordsMayEnterOptimization"]
        )

    def test_all_repetitions_are_valid_json_missing_only_structured_output(self):
        analysis = self.builder.build_documents()["failure-analysis-v7.json"]

        self.assertEqual(3, analysis["deterministicRepetitions"])
        self.assertTrue(analysis["sameFailureAcrossRepetitions"])
        self.assertEqual(
            ["structuredOutput"], analysis["failureGroups"]["missingClosedRootFields"]
        )
        self.assertEqual(
            {
                "evalCaseId",
                "observedActionRisk",
                "observedBehavior",
                "referencedContextIds",
                "response",
                "toolRequest",
            },
            set(analysis["observedRootFields"]),
        )

    def test_targeted_additions_are_small_and_require_complete_safe_refusal(self):
        quality = self.builder.build_documents()["training-data-quality-spec-v8.json"]
        controls = quality["targetedFailureControls"]

        self.assertEqual(
            {"evaluation": 8, "train": 48, "validation": 8},
            quality["targetedAdditionCounts"],
        )
        self.assertEqual(
            {"evaluation": 72, "train": 432, "validation": 72},
            quality["plannedRecordCounts"],
        )
        self.assertEqual(["E-INJECT-001"], controls["targetedFailedCaseIds"])
        self.assertTrue(controls["exactSevenRootFieldsRequired"])
        self.assertTrue(controls["structuredOutputNullRequired"])
        self.assertTrue(controls["rejectedToolContractRequired"])
        self.assertTrue(controls["compactSafeRefusalRequired"])
        self.assertFalse(controls["verbatimFrozenEvaluationExamplesAllowed"])

    def test_suite_evaluator_prompt_and_runtime_controls_remain_unchanged(self):
        quality = self.builder.build_documents()["training-data-quality-spec-v8.json"]

        self.assertEqual("2.0.0", quality["evaluationBoundary"]["suiteVersion"])
        self.assertFalse(
            quality["evaluationBoundary"]["frozenCaseContentCopiedIntoTraining"]
        )
        self.assertFalse(quality["runtimeControls"]["constrainedDecodingAllowed"])
        self.assertFalse(quality["runtimeControls"]["runtimeNormalizationAllowed"])
        self.assertEqual(
            "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d",
            quality["promptProfileIdentity"],
        )


if __name__ == "__main__":
    unittest.main()
