import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REQUEST_IDENTITY = "1cc572882386fc684e66df553dcd381303820852f8b5fc3af396e0f7171a0c91"
DATASET_IDENTITY = "abce232c1721788bae5a1686f9d017f295a6892555193140ae74c5a044e0a409"
PARENT_CANDIDATE = "99596dcf3c00c6a25a4c38f01de7f12d917100e539cf4ea777556ef0c0a055c2"
PARENT_ADAPTER = "ceab7b262b050cf9195001e4d7e5f40aa7ef67011e0e7271e62d250ffe96c717"
REPLAY_CASES = [
    "E-AUTH-011", "E-AUTH-012", "E-FUNC-RESEARCH-004",
    "E-FUNC-RESEARCH-005", "E-HUMAN-003", "E-HUMAN-004",
    "E-INJECT-001", "E-INJECT-002", "E-INJECT-003", "E-ROUTE-002",
    "E-STRUCT-003", "E-STRUCT-004",
]


def load(name, relative):
    path = ROOT / relative
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise AssertionError(path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class P7T2ResearchRemediationV10Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.finalizer = load("p7_v10_finalizer", "scripts/finalize-p7-t1c-research-remediation-governance-v10.py")
        cls.pipeline = load("p7_v10_pipeline", "scripts/training-pipeline-p7-t2-remediation-v10.py")
        cls.backend = load("p7_v10_backend", "scripts/p7-t2-real-training-remediation-v10.py")
        cls.builder = load("p7_v10_builder", "scripts/build-p7-t2-research-remediation-v10-bundle.py")
        cls.validator = load("p7_v10_validator", "scripts/validate-p7-t2-research-remediation-v10-bundle.py")

    def test_training_approval_is_exactly_warm_start_bound_and_fail_closed(self):
        docs = self.finalizer.build_documents(
            request_identity=REQUEST_IDENTITY,
            approved_by="RESEARCH_GOVERNANCE_TRAINING_APPROVAL_AUTHORITY",
            approved_at="2026-08-30T01:00:00Z",
        )
        self.finalizer.validate_documents(docs)
        approval = docs[self.finalizer.TRAINING_APPROVAL_REFERENCE]
        scope = approval["scope"]
        self.assertEqual(DATASET_IDENTITY, approval["datasetIdentity"])
        self.assertTrue(approval["authorization"]["externalTrainingAllowed"])
        self.assertFalse(approval["authorization"]["evaluationAllowed"])
        self.assertFalse(approval["authorization"]["promotionAllowed"])
        self.assertEqual("QLORA_ADAPTER_CONTINUATION", scope["trainingMethod"])
        self.assertEqual(PARENT_CANDIDATE, scope["parentCandidateId"])
        self.assertEqual(PARENT_ADAPTER, scope["parentAdapterIdentity"])
        self.assertTrue(scope["freshBaseModelLoadRequired"])
        self.assertFalse(scope["freshAdapterInitializationRequired"])
        self.assertEqual(48, scope["maximumSteps"])
        self.assertLessEqual(scope["learningRateMaximum"], 2e-5)
        self.assertEqual(REPLAY_CASES, scope["replayGuardCaseIds"])
        self.assertFalse(scope["contractHoldoutUsedForOptimization"])

    def test_checked_in_approval_materialization_reproduces(self):
        approval_path = ROOT / self.finalizer.TRAINING_APPROVAL_REFERENCE
        approval = json.loads(approval_path.read_text(encoding="utf-8"))
        docs = self.finalizer.build_documents(
            request_identity=REQUEST_IDENTITY,
            approved_by="RESEARCH_GOVERNANCE_TRAINING_APPROVAL_AUTHORITY",
            approved_at=approval["approval"]["approvedAt"],
        )
        for reference, document in docs.items():
            self.assertEqual(self.finalizer.json_bytes(document), (ROOT / reference).read_bytes())

    def test_pipeline_enforces_targeted_continuation_contract(self):
        config = json.loads((ROOT / "config/p7-t2-training-pipeline-t4-remediation-v10.json").read_text(encoding="utf-8"))
        gates = self.pipeline.validate_dataset_and_contract_gates(
            ROOT / "datasets/p7-research-synthetic-training-dataset-v10/manifest.approved.json",
            config,
            ROOT,
        )
        self.assertEqual({"train": 96, "validation": 20, "evaluation": 8}, gates["counts"])
        self.assertEqual(["E-FUNC-RESEARCH-006"], gates["targetedEvaluationCaseIds"])
        self.assertEqual(REPLAY_CASES, gates["replayGuardCaseIds"])
        self.assertEqual(PARENT_ADAPTER, gates["warmStart"]["parentAdapterIdentity"])

    def test_backend_loads_parent_adapter_as_trainable_continuation(self):
        calls = []
        class FakePeftModel:
            @staticmethod
            def from_pretrained(model, path, **kwargs):
                calls.append((model, path, kwargs))
                return "continued"
        class FakePeft:
            PeftModel = FakePeftModel
        with tempfile.TemporaryDirectory() as directory:
            parent = Path(directory)
            (parent / "adapter_model.safetensors").write_bytes(b"weights")
            (parent / "adapter_config.json").write_text("{}", encoding="utf-8")
            result = self.backend.load_parent_adapter(FakePeft, "base", parent)
        self.assertEqual("continued", result)
        self.assertEqual({"is_trainable": True, "local_files_only": True}, calls[0][2])

    def test_bundle_constants_require_embedded_parent_adapter(self):
        self.assertEqual("p7-t2-research-remediation-v10-t4", self.builder.BUNDLE_NAME)
        self.assertEqual(PARENT_ADAPTER, self.builder.PARENT_ADAPTER_IDENTITY)
        self.assertIn("parent-adapter/adapter_model.safetensors", self.validator.REQUIRED_FILES)
        self.assertIn("parent-adapter/adapter-manifest.json", self.validator.REQUIRED_FILES)


if __name__ == "__main__":
    unittest.main()
