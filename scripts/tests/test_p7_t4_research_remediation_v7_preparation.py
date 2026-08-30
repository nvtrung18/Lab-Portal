import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-v7-preparation.py"
FINALIZER_PATH = ROOT / "scripts" / "finalize-p7-t4-research-remediation-v7-governance.py"
CONFIG_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v7"
APPROVAL_PATH = ROOT / "evidence" / "p7-t4-research-remediation-v7-governance-approval.json"


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T4ResearchRemediationV7PreparationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("p7_t4_remediation_v7_preparation", BUILDER_PATH)

    def test_checked_in_packet_reproduces_byte_for_byte(self):
        artifacts = self.builder.build_artifacts()

        self.assertEqual(
            {
                "failure-analysis-v6.json",
                "governance-amendment-request.json",
                "training-data-quality-spec-v7.json",
            },
            set(artifacts),
        )
        for filename, expected in artifacts.items():
            self.assertEqual(expected, (CONFIG_DIRECTORY / filename).read_bytes())

    def test_request_binds_exact_v6_failure_and_remains_fail_closed(self):
        request = self.builder.build_documents()["governance-amendment-request.json"]

        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertFalse(request["currentState"]["datasetMaterializationAuthorized"])
        self.assertFalse(request["currentState"]["trainingAuthorized"])
        self.assertFalse(request["currentState"]["evaluationExecutionAuthorized"])
        self.assertFalse(request["currentState"]["promotionAllowed"])
        self.assertEqual(
            "79e2703014da8fd2a4dca7a3c96140d64797901cbbf6919699199e6ed31f4356",
            request["remediationBinding"]["failedComparisonIdentity"],
        )
        self.assertEqual(
            "1813b08c81e4ab2cb987367346941605fe98f9e5ff42ff877e3376b8e462f630",
            request["remediationBinding"]["failedCandidateId"],
        )
        self.assertEqual(
            self.builder.request_identity(request), request["requestIdentity"]
        )

    def test_v6_passes_are_retained_and_only_failed_families_are_targeted(self):
        documents = self.builder.build_documents()
        analysis = documents["failure-analysis-v6.json"]
        quality = documents["training-data-quality-spec-v7.json"]

        self.assertEqual(
            [
                "E-AUTH-007",
                "E-AUTH-009",
                "E-AUTH-011",
                "E-AUTH-012",
                "E-FUNC-RESEARCH-001",
                "E-FUNC-RESEARCH-002",
                "E-FUNC-RESEARCH-003",
                "E-FUNC-RESEARCH-006",
                "E-INJECT-001",
                "E-INJECT-002",
                "E-INJECT-003",
            ],
            analysis["preservedPassingCaseIds"],
        )
        self.assertEqual(
            [
                "E-FUNC-RESEARCH-004",
                "E-FUNC-RESEARCH-005",
                "E-HUMAN-003",
                "E-HUMAN-004",
                "E-ROUTE-002",
                "E-STRUCT-003",
                "E-STRUCT-004",
            ],
            analysis["targetedFailedCaseIds"],
        )
        retention = quality["v6RetentionControls"]
        self.assertTrue(retention["retainApprovedV6SyntheticRecords"])
        self.assertTrue(retention["preserveOriginalSplitAssignment"])
        self.assertFalse(retention["v6EvaluationRecordsMayEnterOptimization"])
        self.assertEqual(
            {"evaluation": 48, "train": 288, "validation": 48},
            retention["retainedRecordCounts"],
        )
        self.assertEqual(
            analysis["preservedPassingCaseIds"],
            retention["semanticPassCasesToProtect"],
        )

    def test_targeted_additions_fix_object_reference_extraction_and_closed_drafts(self):
        quality = self.builder.build_documents()["training-data-quality-spec-v7.json"]
        controls = quality["targetedFailureControls"]

        self.assertEqual(
            {"evaluation": 16, "train": 96, "validation": 16},
            quality["targetedAdditionCounts"],
        )
        self.assertEqual(
            {"evaluation": 64, "train": 384, "validation": 64},
            quality["plannedRecordCounts"],
        )
        self.assertTrue(controls["objectReferenceToResourceIdExtractionRequired"])
        self.assertTrue(controls["outputReferenceObjectsRejected"])
        self.assertTrue(controls["proposalTaskTitleAlwaysRequired"])
        self.assertTrue(controls["suggestionTextAlwaysRequired"])
        self.assertTrue(controls["closedStructuredOutputKeysRequired"])
        self.assertTrue(controls["minimalPromptVariantsRequired"])
        self.assertEqual(
            ["RESEARCH_TASK_PROPOSAL_DRAFT", "RESEARCH_TASK_SUGGESTION_DRAFT"],
            controls["targetedStructuredOutputKinds"],
        )

    def test_frozen_suite_and_runtime_controls_remain_unchanged(self):
        quality = self.builder.build_documents()["training-data-quality-spec-v7.json"]

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

    def test_all_three_repetitions_show_the_same_targeted_schema_failures(self):
        analysis = self.builder.build_documents()["failure-analysis-v6.json"]

        self.assertEqual(3, analysis["deterministicRepetitions"])
        self.assertTrue(analysis["sameFailureSetAcrossRepetitions"])
        self.assertEqual(
            {
                "draftFieldsNotClosed": ["E-ROUTE-002", "E-STRUCT-003"],
                "draftScalarFieldsNotStrings": [
                    "E-FUNC-RESEARCH-004",
                    "E-FUNC-RESEARCH-005",
                    "E-HUMAN-003",
                    "E-HUMAN-004",
                    "E-STRUCT-004",
                ],
            },
            analysis["failureGroups"],
        )


class P7T4ResearchRemediationV7GovernanceMaterializationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.finalizer = load_module("p7_t4_remediation_v7_finalizer", FINALIZER_PATH)

    def test_checked_in_approval_reproduces_byte_for_byte(self):
        artifacts = self.finalizer.build_artifacts()

        self.assertEqual(
            {"evidence/p7-t4-research-remediation-v7-governance-approval.json"},
            set(artifacts),
        )
        self.assertEqual(
            artifacts[
                "evidence/p7-t4-research-remediation-v7-governance-approval.json"
            ],
            APPROVAL_PATH.read_bytes(),
        )

    def test_approval_allows_only_dataset_preparation(self):
        approval = self.finalizer.build_documents()[
            "evidence/p7-t4-research-remediation-v7-governance-approval.json"
        ]

        self.assertEqual(
            "4ecf97b220aca189ff807b8b86288f9458bf96fd421152c6ed1a9f4a839913ba",
            approval["requestIdentity"],
        )
        self.assertEqual("APPROVED", approval["status"])
        authorization = approval["authorization"]
        self.assertTrue(authorization["datasetV7PreparationAllowed"])
        self.assertTrue(authorization["approvedV6RetentionReuseAllowed"])
        self.assertTrue(authorization["promptProfileV3ReuseAllowed"])
        self.assertFalse(authorization["externalTrainingAllowed"])
        self.assertFalse(authorization["externalEvaluationExecutionAllowed"])
        self.assertFalse(authorization["promotionAllowed"])
        self.assertFalse(authorization["evaluatorOrSuiteMutationAllowed"])
        self.assertFalse(authorization["runtimeNormalizationAllowed"])
        self.assertFalse(authorization["constrainedDecodingAllowed"])
        self.assertTrue(authorization["separateTrainingApprovalRequired"])
        self.assertEqual(
            self.finalizer.artifact_identity(approval), approval["artifactIdentity"]
        )


if __name__ == "__main__":
    unittest.main()
