import importlib.util
import json
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[2]
EVALUATOR_PATH = ROOT / "scripts" / "validate-p7-t4-research-evaluation-v2.py"
PREPARATION_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-v5-preparation.py"
SOURCE_BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-source-v5.py"
DATASET_VALIDATOR_PATH = ROOT / "scripts" / "validate-p7-t4-research-remediation-v5-dataset.py"
GOVERNANCE_FINALIZER_PATH = ROOT / "scripts" / "finalize-p7-t4-research-remediation-v5-governance.py"
CONFIG_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v5"
DATASET_DIRECTORY = ROOT / "datasets" / "p7-research-synthetic-training-dataset-v5"
EVALUATOR_APPROVAL_PATH = (
    ROOT / "evidence" / "p7-t4-research-remediation-v5-evaluator-governance-approval.json"
)
TRAINING_REQUEST_PATH = CONFIG_DIRECTORY / "training-approval-request.json"


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T4ResearchEvaluatorV2Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evaluator = load_module("p7_t4_research_evaluator_v2", EVALUATOR_PATH)

    def test_report_review_contract_accepts_only_the_approved_closed_shape(self):
        valid = {
            "advisoryOnly": True,
            "issues": ["The synthetic method omits its replicate count."],
            "kind": "RESEARCH_REPORT_REVIEW_DRAFT",
            "reportRef": "syn-v5-report-01",
            "requiresHumanReview": True,
            "reviewSummary": "A bounded synthetic advisory review.",
            "suggestions": ["State the replicate count."],
        }

        self.assertEqual(
            [],
            self.evaluator.validate_output(valid, "RESEARCH_REPORT_REVIEW_DRAFT"),
        )
        for mutation in (
            {**valid, "extra": "forbidden"},
            {key: value for key, value in valid.items() if key != "issues"},
            {**valid, "issues": []},
            {**valid, "advisoryOnly": False},
        ):
            with self.subTest(mutation=mutation):
                self.assertTrue(
                    self.evaluator.validate_output(
                        mutation, "RESEARCH_REPORT_REVIEW_DRAFT"
                    )
                )


class P7T4ResearchRemediationV5PreparationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("p7_t4_remediation_v5_preparation", PREPARATION_PATH)

    def test_checked_in_governance_packet_reproduces_byte_for_byte(self):
        artifacts = self.builder.build_artifacts()

        self.assertEqual(
            {
                "evaluator-contract-v2.pending.json",
                "evaluation-suite-v2.pending.json",
                "governance-amendment-request.json",
                "training-data-quality-spec-v5.json",
            },
            set(artifacts),
        )
        for filename, expected in artifacts.items():
            self.assertEqual(expected, (CONFIG_DIRECTORY / filename).read_bytes())

    def test_amendment_is_fail_closed_and_preserves_all_frozen_inputs(self):
        documents = self.builder.build_documents()
        request = documents["governance-amendment-request.json"]
        suite = documents["evaluation-suite-v2.pending.json"]
        evaluator = documents["evaluator-contract-v2.pending.json"]

        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertFalse(request["currentState"]["trainingAuthorized"])
        self.assertFalse(request["currentState"]["evaluationExecutionAuthorized"])
        self.assertFalse(request["currentState"]["runtimeNormalizationAuthorized"])
        self.assertEqual("2.0.0", suite["suiteVersion"])
        self.assertEqual("PENDING_GOVERNANCE_APPROVAL", suite["status"])
        self.assertTrue(suite["EVALUATION_ONLY"])
        self.assertTrue(suite["TRAINING_PROHIBITED"])
        self.assertEqual("2.0.0", evaluator["evaluatorVersion"])
        self.assertFalse(evaluator["runtimeNormalizationAllowed"])
        self.assertEqual(
            "RESEARCH_REPORT_REVIEW_DRAFT",
            evaluator["structuredOutputContracts"][-1]["kind"],
        )
        self.assertTrue(all(item["unchanged"] for item in request["preservedFrozenInputs"]))
        self.assertEqual(self.builder.request_identity(request), request["requestIdentity"])


class P7T4ResearchRemediationV5GovernanceMaterializationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.finalizer = load_module(
            "p7_t4_remediation_v5_governance_finalizer", GOVERNANCE_FINALIZER_PATH
        )

    def test_checked_in_approved_artifacts_reproduce_byte_for_byte(self):
        artifacts = self.finalizer.build_artifacts()
        self.assertEqual(
            {
                "config/p7-t4-research-remediation-governance-v5/evaluator-contract-v2.approved.json",
                "config/p7-t4-research-remediation-governance-v5/evaluation-suite-v2.approved.json",
                "evidence/p7-t4-research-remediation-v5-evaluator-governance-approval.json",
            },
            set(artifacts),
        )
        for relative_path, expected in artifacts.items():
            self.assertEqual(expected, (ROOT / relative_path).read_bytes())

    def test_approval_authorizes_versioned_contracts_but_not_external_execution(self):
        approval = json.loads(EVALUATOR_APPROVAL_PATH.read_text(encoding="utf-8"))
        evaluator = json.loads(
            (CONFIG_DIRECTORY / "evaluator-contract-v2.approved.json").read_text(
                encoding="utf-8"
            )
        )
        suite = json.loads(
            (CONFIG_DIRECTORY / "evaluation-suite-v2.approved.json").read_text(
                encoding="utf-8"
            )
        )

        self.assertEqual(
            "4a9ceb3be319bc2fb96b3d856bfdcb2c6c263a325fab5863f45265b4fe52d93f",
            approval["requestIdentity"],
        )
        self.assertEqual("APPROVED", approval["status"])
        self.assertTrue(approval["authorization"]["evaluatorV2UseAllowed"])
        self.assertTrue(approval["authorization"]["suiteV2UseAllowed"])
        self.assertFalse(approval["authorization"]["externalEvaluationExecutionAllowed"])
        self.assertFalse(approval["authorization"]["externalTrainingAllowed"])
        self.assertFalse(approval["authorization"]["runtimeNormalizationAllowed"])
        self.assertEqual("APPROVED", evaluator["status"])
        self.assertEqual("APPROVED", suite["status"])
        self.assertEqual("2.0.0", evaluator["evaluatorVersion"])
        self.assertEqual("2.0.0", suite["suiteVersion"])
        self.assertFalse(evaluator["runtimeNormalizationAllowed"])
        self.assertFalse(suite["externalExecutionAllowed"])
        self.assertTrue(all(item["unchanged"] for item in approval["preservedFrozenInputs"]))

    def test_finalizer_rejects_tampered_pending_request(self):
        pending = self.finalizer.PREPARATION.build_documents()
        pending["governance-amendment-request.json"]["status"] = "APPROVED"

        with mock.patch.object(
            self.finalizer.PREPARATION, "build_documents", return_value=pending
        ), self.assertRaises(self.finalizer.FinalizationError):
            self.finalizer.build_documents()


class P7T4ResearchRemediationV5DatasetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("p7_t4_remediation_v5_source", SOURCE_BUILDER_PATH)
        cls.evaluator = load_module("p7_t4_research_evaluator_v2_dataset", EVALUATOR_PATH)
        cls.validator = load_module("p7_t4_remediation_v5_validator", DATASET_VALIDATOR_PATH)

    def test_checked_in_dataset_reproduces_byte_for_byte(self):
        artifacts = self.builder.build_artifacts()

        for relative_path, expected in artifacts.items():
            self.assertEqual(expected, (ROOT / relative_path).read_bytes())

    def test_training_request_is_bound_to_approved_evaluator_and_suite(self):
        request = json.loads(TRAINING_REQUEST_PATH.read_text(encoding="utf-8"))
        approval = json.loads(EVALUATOR_APPROVAL_PATH.read_text(encoding="utf-8"))

        self.assertEqual(
            EVALUATOR_APPROVAL_PATH.relative_to(ROOT).as_posix(),
            request["evaluatorSuiteApprovalReference"],
        )
        self.assertEqual(
            approval["artifactIdentity"],
            request["evaluatorSuiteApprovalIdentity"],
        )
        self.assertEqual(
            approval["approvedArtifacts"]["evaluatorReference"],
            request["evaluatorReference"],
        )
        self.assertEqual(
            approval["approvedArtifacts"]["evaluatorIdentity"],
            request["evaluatorIdentity"],
        )
        self.assertEqual(
            approval["approvedArtifacts"]["suiteReference"],
            request["evaluationSuiteReference"],
        )
        self.assertEqual(
            approval["approvedArtifacts"]["suiteIdentity"],
            request["evaluationSuiteIdentity"],
        )
        self.assertFalse(request["trainingAuthorized"])
        self.assertFalse(request["externalTrainingAllowed"])
        self.assertFalse(request["runtimeNormalizationAllowed"])
        self.assertEqual(
            self.builder.artifact_identity(request, "requestIdentity"),
            request["requestIdentity"],
        )

    def test_dataset_is_disjoint_canonical_and_targets_the_v4_failures(self):
        records = self.builder.generate_records()
        self.assertEqual(192, len(records))
        self.assertEqual(192, len({record["recordId"] for record in records}))

        output_kinds = set()
        reference_modes = set()
        authorization_states = set()
        for record in records:
            prompt = record["trainingPrompt"]
            target = record["trainingTarget"]
            self.assertFalse(record["metadata"]["evaluationDerived"])
            self.assertFalse(prompt["evalCaseId"].startswith("E-"))
            self.assertEqual([], self.evaluator.validate_tool(target["toolRequest"]))
            self.assertEqual(
                self.builder.canonical_bytes(target),
                self.builder.canonical_bytes(
                    json.loads(self.builder.canonical_bytes(target).decode("utf-8"))
                ),
            )
            self.assertTrue(
                set(target["response"]["markers"])
                <= self.evaluator.RESPONSE_MARKERS
            )
            authorization_states.add(
                (prompt["caseState"], target["observedBehavior"])
            )
            structured = target["structuredOutput"]
            if structured is not None:
                output_kinds.add(structured["kind"])
                self.assertEqual(
                    [],
                    self.evaluator.validate_output(
                        structured, prompt["structuredOutputContract"]
                    ),
                )
                reference_modes.add(
                    (prompt["caseState"], bool(target["referencedContextIds"]))
                )

        self.assertEqual(
            {
                "RESEARCH_TASK_PROPOSAL_DRAFT",
                "RESEARCH_TASK_SUGGESTION_DRAFT",
                "RESEARCH_REPORT_REVIEW_DRAFT",
            },
            output_kinds,
        )
        self.assertIn(("authorizedNoToolNoCitations", False), reference_modes)
        self.assertIn(("authorizedNoToolWithCitations", True), reference_modes)
        self.assertIn(("authorizedNoToolNoCitations", "SUCCESS"), authorization_states)
        self.assertIn(("authorizationNoTool", "DENY"), authorization_states)

    def test_dataset_outputs_only_closed_tool_envelopes(self):
        records = self.builder.generate_records()
        tool_kinds = {record["trainingTarget"]["toolRequest"]["kind"] for record in records}

        self.assertEqual({"NONE", "REQUEST", "REJECTED"}, tool_kinds)
        for record in records:
            tool = record["trainingTarget"]["toolRequest"]
            expected_keys = {
                "NONE": {"kind"},
                "REQUEST": {"kind", "group", "name", "intent"},
                "REJECTED": {"kind", "group", "name", "intent", "reason"},
            }[tool["kind"]]
            self.assertEqual(expected_keys, set(tool))

    def test_source_validator_rejects_exact_frozen_evaluation_prompts(self):
        frozen_suite = json.loads(
            (ROOT / "evals" / "p6-t4-evaluation-suites.yaml").read_text(
                encoding="utf-8"
            )
        )
        frozen_prompt = next(
            case["input"]
            for case in frozen_suite["caseInventory"]
            if isinstance(case.get("input"), str)
        )
        records = deepcopy(self.builder.generate_records())
        records[0]["trainingPrompt"]["input"] = frozen_prompt

        with self.assertRaises(self.builder.SourceBuildError):
            self.builder.validate_records(records)

    def test_validator_accepts_exact_pending_dataset_and_rejects_tampering(self):
        result = self.validator.validate(ROOT)
        self.assertEqual("PREPARED_AWAITING_TRAINING_APPROVAL", result["state"])
        self.assertFalse(result["trainingAuthorized"])
        self.assertEqual(
            {"evaluation": 24, "train": 144, "validation": 24},
            result["counts"],
        )

        with tempfile.TemporaryDirectory() as name:
            artifact_root = Path(name)
            for relative_path, content in self.builder.build_artifacts().items():
                path = artifact_root / relative_path
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(content)
            train = (
                artifact_root
                / "datasets"
                / "p7-research-synthetic-training-dataset-v5"
                / "train.jsonl"
            )
            train.write_bytes(train.read_bytes() + b"{}\n")
            with self.assertRaises(self.validator.DatasetValidationError):
                self.validator.validate(artifact_root)


if __name__ == "__main__":
    unittest.main()
