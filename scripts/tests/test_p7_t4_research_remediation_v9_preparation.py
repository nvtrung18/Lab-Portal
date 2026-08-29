import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-v9-preparation.py"
CONFIG_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v9"


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T4ResearchRemediationV9PreparationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("p7_t4_remediation_v9_preparation", BUILDER_PATH)

    def test_checked_in_packet_reproduces_byte_for_byte(self):
        artifacts = self.builder.build_artifacts()

        self.assertEqual(
            {
                "failure-analysis-v8.json",
                "governance-amendment-request.json",
                "training-data-quality-spec-v9.json",
            },
            set(artifacts),
        )
        for filename, expected in artifacts.items():
            self.assertEqual(expected, (CONFIG_DIRECTORY / filename).read_bytes())

    def test_request_is_fail_closed_and_binds_exact_v8_result(self):
        request = self.builder.build_documents()["governance-amendment-request.json"]

        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertFalse(request["currentState"]["datasetMaterializationAuthorized"])
        self.assertFalse(request["currentState"]["trainingAuthorized"])
        self.assertFalse(request["currentState"]["evaluationExecutionAuthorized"])
        self.assertFalse(request["currentState"]["promotionAllowed"])
        self.assertEqual(
            "4ef7a6ee8b2f924bb2a3381b23a5396feede87fbe5e38aed0813403c8c73fa89",
            request["remediationBinding"]["comparisonIdentity"],
        )
        self.assertEqual(
            "cb8c3e4addd20d6de84ed2c135a41baa2841666e631629617dceb3445db04403",
            request["remediationBinding"]["candidateId"],
        )
        self.assertEqual(
            self.builder.request_identity(request), request["requestIdentity"]
        )

    def test_only_report_review_semantics_are_targeted(self):
        documents = self.builder.build_documents()
        analysis = documents["failure-analysis-v8.json"]
        quality = documents["training-data-quality-spec-v9.json"]

        self.assertEqual(["E-FUNC-RESEARCH-006"], analysis["targetedCaseIds"])
        self.assertEqual("AUTOMATIC_PASS", analysis["automaticDecision"])
        self.assertFalse(analysis["formalHumanReviewCompleted"])
        self.assertEqual(18, len(analysis["preservedAutomaticPassCaseIds"]))
        self.assertEqual(
            analysis["preservedAutomaticPassCaseIds"],
            quality["v8RetentionControls"]["automaticPassCasesToProtect"],
        )

    def test_deterministic_semantic_issue_is_bound_without_copying_eval_content(self):
        analysis = self.builder.build_documents()["failure-analysis-v8.json"]

        self.assertEqual(3, analysis["deterministicRepetitions"])
        self.assertTrue(analysis["sameSemanticIssueAcrossRepetitions"])
        self.assertEqual(
            ["BOUNDED_INTERPRETED_AS_REPETITION", "EVIDENCE_LINK_FOCUS_MISSED"],
            analysis["semanticIssueGroups"],
        )

    def test_v9_additions_are_disjoint_and_preserve_v8(self):
        quality = self.builder.build_documents()["training-data-quality-spec-v9.json"]

        self.assertEqual(
            {"evaluation": 8, "train": 48, "validation": 8},
            quality["targetedAdditionCounts"],
        )
        self.assertEqual(
            {"evaluation": 80, "train": 480, "validation": 80},
            quality["plannedRecordCounts"],
        )
        self.assertEqual(
            {"evaluation": 72, "train": 432, "validation": 72},
            quality["v8RetentionControls"]["retainedRecordCounts"],
        )
        self.assertTrue(quality["v8RetentionControls"]["retainApprovedV8SyntheticRecords"])
        self.assertFalse(
            quality["v8RetentionControls"]["v8EvaluationRecordsMayEnterOptimization"]
        )
        self.assertFalse(
            quality["evaluationBoundary"]["frozenCaseContentCopiedIntoTraining"]
        )
        self.assertTrue(
            quality["targetedFailureControls"]["unknownMustNotBecomeConfirmedMissing"]
        )
        self.assertTrue(
            quality["targetedFailureControls"]["boundedMustNotImplyRepetition"]
        )

    def test_suite_prompt_and_runtime_controls_remain_unchanged(self):
        quality = self.builder.build_documents()["training-data-quality-spec-v9.json"]

        self.assertEqual("2.0.0", quality["evaluationBoundary"]["suiteVersion"])
        self.assertFalse(quality["runtimeControls"]["constrainedDecodingAllowed"])
        self.assertFalse(quality["runtimeControls"]["runtimeNormalizationAllowed"])
        self.assertEqual(
            "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d",
            quality["promptProfileIdentity"],
        )


if __name__ == "__main__":
    unittest.main()
