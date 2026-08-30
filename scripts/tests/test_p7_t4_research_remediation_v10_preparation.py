import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-v10-preparation.py"
CONFIG_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v10"

V9_CANDIDATE_ID = (
    "99596dcf3c00c6a25a4c38f01de7f12d917100e539cf4ea777556ef0c0a055c2"
)
V9_ADAPTER_IDENTITY = (
    "ceab7b262b050cf9195001e4d7e5f40aa7ef67011e0e7271e62d250ffe96c717"
)
V9_TRAINING_RUN_IDENTITY = (
    "9ee7cd3cb56cb5b18c096ce8764fe14a23d69bdd39ef027a8f1472710b152314"
)
TARGET_CASE_IDS = ["E-FUNC-RESEARCH-006"]
REPLAY_CASE_IDS = [
    "E-AUTH-011",
    "E-AUTH-012",
    "E-FUNC-RESEARCH-004",
    "E-FUNC-RESEARCH-005",
    "E-HUMAN-003",
    "E-HUMAN-004",
    "E-INJECT-001",
    "E-INJECT-002",
    "E-INJECT-003",
    "E-ROUTE-002",
    "E-STRUCT-003",
    "E-STRUCT-004",
]


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T4ResearchRemediationV10PreparationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("p7_t4_remediation_v10_preparation", BUILDER_PATH)

    def test_checked_in_packet_reproduces_byte_for_byte(self):
        artifacts = self.builder.build_artifacts()

        self.assertEqual(
            {
                "governance-amendment-request.json",
                "human-review-finding-v9.json",
                "targeted-continuation-quality-spec-v10.json",
            },
            set(artifacts),
        )
        for filename, expected in artifacts.items():
            self.assertEqual(expected, (CONFIG_DIRECTORY / filename).read_bytes())

    def test_request_is_fail_closed_and_requests_only_v10_preparation(self):
        request = self.builder.build_documents()["governance-amendment-request.json"]
        current = request["currentState"]
        scope = request["requestedScope"]

        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertFalse(current["datasetMaterializationAuthorized"])
        self.assertFalse(current["trainingAuthorized"])
        self.assertFalse(current["evaluationExecutionAuthorized"])
        self.assertFalse(current["promotionAllowed"])
        self.assertTrue(scope["datasetV10PreparationRequested"])
        self.assertTrue(scope["warmStartAmendmentRequested"])
        self.assertFalse(scope["externalTrainingRequested"])
        self.assertFalse(scope["externalEvaluationExecutionRequested"])
        self.assertFalse(scope["evaluatorOrSuiteMutationRequested"])
        self.assertFalse(scope["priorEvidenceMutationAllowed"])
        self.assertEqual(
            self.builder.request_identity(request), request["requestIdentity"]
        )

    def test_human_finding_targets_only_e006_and_preserves_twelve_passes(self):
        finding = self.builder.build_documents()["human-review-finding-v9.json"]

        self.assertEqual("AUTOMATIC_PASS", finding["automaticDecision"])
        self.assertEqual(TARGET_CASE_IDS, finding["humanFailedCaseIds"])
        self.assertEqual(REPLAY_CASE_IDS, finding["humanPassedCaseIds"])
        self.assertEqual(
            ["TASK_CORRECTNESS", "USEFULNESS", "VIETNAMESE_QUALITY"],
            finding["failedDimensions"],
        )
        self.assertEqual(V9_CANDIDATE_ID, finding["candidateId"])
        self.assertEqual(12, finding["humanPassCount"])
        self.assertEqual(1, finding["humanFailCount"])

    def test_warm_start_is_bound_to_exact_v9_adapter_and_remains_unapproved(self):
        spec = self.builder.build_documents()[
            "targeted-continuation-quality-spec-v10.json"
        ]
        warm_start = spec["warmStartProposal"]

        self.assertEqual(V9_CANDIDATE_ID, warm_start["parentCandidateId"])
        self.assertEqual(V9_ADAPTER_IDENTITY, warm_start["parentAdapterIdentity"])
        self.assertEqual(
            V9_TRAINING_RUN_IDENTITY, warm_start["parentTrainingRunIdentity"]
        )
        self.assertEqual("QLORA_ADAPTER_CONTINUATION", warm_start["method"])
        self.assertTrue(warm_start["freshBaseModelLoadRequired"])
        self.assertFalse(warm_start["freshAdapterInitializationRequired"])
        self.assertFalse(warm_start["authorized"])

    def test_dataset_design_uses_only_e006_target_and_pass_case_replay(self):
        spec = self.builder.build_documents()[
            "targeted-continuation-quality-spec-v10.json"
        ]
        dataset = spec["datasetProposal"]

        self.assertEqual(TARGET_CASE_IDS, dataset["targetedCaseIds"])
        self.assertEqual(REPLAY_CASE_IDS, dataset["replayGuardCaseIds"])
        self.assertEqual("TRAIN_AND_VALIDATION_ONLY", dataset["v9ReplaySourceSplits"])
        self.assertFalse(dataset["v9EvaluationRecordsAllowedForOptimization"])
        self.assertFalse(dataset["frozenSuiteContentCopiedIntoTraining"])
        self.assertTrue(dataset["splitContentIdsDisjoint"])
        self.assertTrue(dataset["syntheticOnly"])

    def test_continuation_is_low_rate_bounded_and_early_stopped(self):
        spec = self.builder.build_documents()[
            "targeted-continuation-quality-spec-v10.json"
        ]
        training = spec["trainingProposal"]

        self.assertEqual(0.00002, training["learningRateMaximum"])
        self.assertEqual(48, training["maximumSteps"])
        self.assertEqual(1, training["earlyStoppingPatience"])
        self.assertEqual("validation", training["earlyStoppingSplit"])
        self.assertEqual("evaluation", training["contractHoldoutSplit"])
        self.assertFalse(training["contractHoldoutUsedForOptimization"])
        self.assertFalse(training["contractHoldoutUsedForEarlyStopping"])

    def test_suite_prompt_and_runtime_controls_are_not_changed(self):
        spec = self.builder.build_documents()[
            "targeted-continuation-quality-spec-v10.json"
        ]

        self.assertEqual("2.0.0", spec["evaluationContract"]["suiteVersion"])
        self.assertEqual("2.0.0", spec["evaluationContract"]["evaluatorVersion"])
        self.assertEqual("3.0.0", spec["promptProfile"]["version"])
        self.assertFalse(spec["runtimeControls"]["constrainedDecodingAllowed"])
        self.assertFalse(spec["runtimeControls"]["runtimeNormalizationAllowed"])


if __name__ == "__main__":
    unittest.main()
