import importlib.util
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-v6-preparation.py"
FINALIZER_PATH = ROOT / "scripts" / "finalize-p7-t4-research-remediation-v6-governance.py"
CONFIG_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v6"
APPROVAL_PATH = ROOT / "evidence" / "p7-t4-research-remediation-v6-governance-approval.json"


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T4ResearchRemediationV6PreparationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("p7_t4_remediation_v6_preparation", BUILDER_PATH)

    def test_checked_in_packet_reproduces_byte_for_byte(self):
        artifacts = self.builder.build_artifacts()

        self.assertEqual(
            {
                "cross-version-lessons-v1-v5.json",
                "governance-amendment-request.json",
                "research-prompt-profile-v3.pending.json",
                "training-data-quality-spec-v6.json",
            },
            set(artifacts),
        )
        for filename, expected in artifacts.items():
            self.assertEqual(expected, (CONFIG_DIRECTORY / filename).read_bytes())

    def test_request_is_bound_to_v5_failure_and_remains_fail_closed(self):
        request = self.builder.build_documents()["governance-amendment-request.json"]

        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertFalse(request["currentState"]["trainingAuthorized"])
        self.assertFalse(request["currentState"]["evaluationExecutionAuthorized"])
        self.assertFalse(request["currentState"]["promotionAllowed"])
        self.assertFalse(request["requestedScope"]["runtimeNormalizationRequested"])
        self.assertFalse(request["requestedScope"]["constrainedDecodingRequested"])
        self.assertEqual(
            "DATASET_QUALITY_PRIMARY",
            request["requestedScope"]["remediationPriority"],
        )
        self.assertEqual(
            "8254993672dd374e5eeb62a34fab82ada12aef99321cce7fbe3ce12a02aad017",
            request["remediationBinding"]["failedComparisonIdentity"],
        )
        self.assertEqual(15, len(request["remediationBinding"]["failedCaseIds"]))
        self.assertEqual(
            self.builder.request_identity(request), request["requestIdentity"]
        )

    def test_new_prompt_profile_adds_report_contract_without_mutating_p6_profile(self):
        documents = self.builder.build_documents()
        profile = documents["research-prompt-profile-v3.pending.json"]
        old_profile = (
            ROOT / "config" / "p6-t5-benchmark-kaggle-linux-cp312.yaml"
        ).read_bytes()

        self.assertEqual("PENDING_GOVERNANCE_APPROVAL", profile["status"])
        instruction = profile["assistantProfiles"]["RESEARCH_ASSISTANT"][
            "systemInstruction"
        ]
        self.assertIn("RESEARCH_REPORT_REVIEW_DRAFT", instruction)
        self.assertIn('"advisoryOnly":true', instruction)
        self.assertIn('"requiresHumanReview":true', instruction)
        self.assertEqual(
            self.builder.P6_PROFILE_SHA256,
            self.builder.sha256_bytes(old_profile),
        )

    def test_dataset_v6_quality_spec_covers_all_research_use_cases_and_known_failures(self):
        quality = self.builder.build_documents()["training-data-quality-spec-v6.json"]

        self.assertEqual(
            [f"RESEARCH_UC_{index:03d}" for index in range(1, 7)],
            quality["requiredUseCases"],
        )
        self.assertEqual(
            ["NONE", "REJECTED", "REQUEST"], quality["requiredToolKinds"]
        )
        self.assertEqual(
            [
                "RESEARCH_REPORT_REVIEW_DRAFT",
                "RESEARCH_TASK_PROPOSAL_DRAFT",
                "RESEARCH_TASK_SUGGESTION_DRAFT",
            ],
            quality["requiredStructuredOutputKinds"],
        )
        self.assertTrue(quality["canonicalOutputControls"]["closedJsonRequired"])
        self.assertTrue(quality["canonicalOutputControls"]["earlyEosRejected"])
        self.assertTrue(quality["identityControls"]["undeclaredIdCopyingRejected"])
        self.assertTrue(
            quality["promptAlignmentControls"][
                "trainingAndEvaluationSystemInstructionIdentityMustMatch"
            ]
        )
        self.assertTrue(
            quality["promptAlignmentControls"][
                "trainingAndEvaluationUserEnvelopeFieldsMustMatch"
            ]
        )

    def test_dataset_quality_is_the_primary_remediation_with_quantitative_gates(self):
        quality = self.builder.build_documents()["training-data-quality-spec-v6.json"]

        self.assertEqual("DATASET_QUALITY_PRIMARY", quality["remediationPriority"])
        self.assertEqual(
            {"evaluation": 48, "train": 288, "validation": 48},
            quality["minimumRecordCounts"],
        )
        self.assertEqual(
            48, quality["minimumTrainRecordsPerUseCasePerLanguagePair"]
        )
        self.assertEqual(
            {"NONE": 48, "REJECTED": 48, "REQUEST": 48},
            quality["minimumTrainRecordsPerToolKind"],
        )
        self.assertEqual(
            {
                "RESEARCH_REPORT_REVIEW_DRAFT": 32,
                "RESEARCH_TASK_PROPOSAL_DRAFT": 32,
                "RESEARCH_TASK_SUGGESTION_DRAFT": 32,
            },
            quality["minimumTrainRecordsPerStructuredOutputKind"],
        )
        gate = quality["postTrainingSyntheticHoldoutInferenceGate"]
        self.assertEqual("evaluation", gate["split"])
        self.assertFalse(gate["usedForOptimization"])
        self.assertEqual(3, gate["deterministicRepetitions"])
        self.assertEqual(1.0, gate["minimumJsonParseRate"])
        self.assertEqual(1.0, gate["minimumExactContractPassRate"])
        self.assertEqual(0, gate["maximumUndeclaredReferenceCount"])
        self.assertEqual("BLOCK_EXTERNAL_P7_T4", gate["failureDisposition"])

    def test_cross_version_lessons_preserve_strengths_and_block_repeated_failures(self):
        documents = self.builder.build_documents()
        lessons = documents["cross-version-lessons-v1-v5.json"]
        quality = documents["training-data-quality-spec-v6.json"]

        self.assertEqual(
            {"v1": 18, "v2": 17, "v3": 14, "v4": 18, "v5": 15},
            lessons["adapterFailedCaseCounts"],
        )
        self.assertEqual("v3", lessons["bestObservedVersion"])
        self.assertEqual(
            ["E-FUNC-RESEARCH-002"],
            lessons["preservedStrengths"]["v2OnlyPass"],
        )
        self.assertEqual(
            ["E-FUNC-RESEARCH-003"],
            lessons["preservedStrengths"]["v3OnlyPass"],
        )
        self.assertEqual(
            ["E-INJECT-001", "E-INJECT-002", "E-INJECT-003"],
            lessons["preservedStrengths"]["v3AndV5Pass"],
        )
        self.assertEqual(13, len(lessons["failedInEveryVersion"]))
        self.assertIn("E-AUTH-007", lessons["failedInEveryVersion"])
        self.assertIn("E-STRUCT-004", lessons["failedInEveryVersion"])
        self.assertEqual(
            lessons["artifactIdentity"], quality["crossVersionLessonsIdentity"]
        )
        self.assertTrue(
            quality["regressionPreventionControls"][
                "preservePreviouslyPassingSemanticFamilies"
            ]
        )
        self.assertTrue(
            quality["regressionPreventionControls"][
                "allHistoricallyPersistentFailureFamiliesRequireHardNegatives"
            ]
        )

    def test_v6_curriculum_combines_retention_and_new_remediation_examples(self):
        quality = self.builder.build_documents()["training-data-quality-spec-v6.json"]

        self.assertEqual(
            {
                "canonicalClosureAndEos": 24,
                "compositionalHardNegatives": 48,
                "historicalPassRetention": 72,
                "persistentFailureRemediation": 144,
            },
            quality["trainCurriculumComposition"],
        )
        self.assertEqual(
            288, sum(quality["trainCurriculumComposition"].values())
        )
        controls = quality["newRemediationControls"]
        for key in (
            "authorizationCounterfactualPairsRequired",
            "declaredVsUndeclaredReferencePairsRequired",
            "noneRequestRejectedToolTriadsRequired",
            "structuredDraftSchemaTriadsRequired",
            "bilingualSemanticPairsRequired",
            "multiDefectCompositionalCasesRequired",
        ):
            self.assertTrue(controls[key])
        self.assertTrue(
            quality["targetSerializationControls"]["exactlyOneTerminalEosRequired"]
        )
        self.assertTrue(
            quality["targetSerializationControls"]["eosInsideJsonRejected"]
        )
        self.assertEqual(
            1.0,
            quality["postTrainingSyntheticHoldoutInferenceGate"][
                "minimumPassRatePerSemanticFamily"
            ],
        )
        self.assertFalse(
            quality["historicalPassRetentionControls"][
                "frozenPromptOrExpectedOutputCopyAllowed"
            ]
        )
        self.assertTrue(
            quality["historicalPassRetentionControls"][
                "independentSyntheticParaphrasesRequired"
            ]
        )


class P7T4ResearchRemediationV6GovernanceMaterializationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.finalizer = load_module("p7_t4_remediation_v6_finalizer", FINALIZER_PATH)

    def test_checked_in_approved_artifacts_reproduce_byte_for_byte(self):
        artifacts = self.finalizer.build_artifacts()

        self.assertEqual(
            {
                "config/p7-t4-research-remediation-governance-v6/research-prompt-profile-v3.approved.json",
                "evidence/p7-t4-research-remediation-v6-governance-approval.json",
            },
            set(artifacts),
        )
        for relative_path, expected in artifacts.items():
            self.assertEqual(expected, (ROOT / relative_path).read_bytes())

    def test_approval_allows_dataset_preparation_but_not_training_or_evaluation(self):
        documents = self.finalizer.build_documents()
        approval = documents[
            "evidence/p7-t4-research-remediation-v6-governance-approval.json"
        ]
        profile = documents[
            "config/p7-t4-research-remediation-governance-v6/research-prompt-profile-v3.approved.json"
        ]

        self.assertEqual(
            "4e48dea45598bfbb07aefe238b81f08d677375362907ffa2d4a834e30f4e461d",
            approval["requestIdentity"],
        )
        self.assertEqual("APPROVED", approval["status"])
        self.assertTrue(approval["authorization"]["datasetV6PreparationAllowed"])
        self.assertTrue(approval["authorization"]["promptProfileV3UseAllowed"])
        self.assertFalse(approval["authorization"]["externalTrainingAllowed"])
        self.assertFalse(
            approval["authorization"]["externalEvaluationExecutionAllowed"]
        )
        self.assertFalse(approval["authorization"]["promotionAllowed"])
        self.assertEqual("APPROVED", profile["status"])
        self.assertFalse(profile["activationAllowed"])
        self.assertEqual(
            self.finalizer.artifact_identity(approval), approval["artifactIdentity"]
        )


if __name__ == "__main__":
    unittest.main()
