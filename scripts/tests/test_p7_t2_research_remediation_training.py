import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CONFIG_PATH = ROOT / "config" / "p7-t2-training-pipeline-t4-remediation.json"
PIPELINE_PATH = ROOT / "scripts" / "training-pipeline-p7-t2-remediation.py"
BACKEND_PATH = ROOT / "scripts" / "p7-t2-real-training-remediation.py"
DATASET_MANIFEST = ROOT / "datasets" / "p7-research-synthetic-training-dataset-v2" / "manifest.json"


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class P7T2ResearchRemediationTrainingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pipeline = load_module("p7_t2_remediation_pipeline", PIPELINE_PATH)
        cls.backend = load_module("p7_t2_remediation_backend", BACKEND_PATH)
        cls.config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))

    def test_config_binds_approved_v2_dataset_and_safe_schedule(self):
        self.pipeline.validate_training_config(self.config)
        self.assertEqual(
            "0409e9087efe7332e298d0c3812d11f2edac7cedf538a8db475776d9c190eb30",
            self.config["dataset"]["identity"],
        )
        self.assertEqual("2.0.0", self.config["pipelineVersion"])
        training = self.config["training"]
        self.assertEqual(8, training["epochs"])
        self.assertIsNone(training["maxSteps"])
        self.assertEqual("epoch", training["evaluationStrategy"])
        self.assertEqual("epoch", training["saveStrategy"])
        self.assertTrue(training["loadBestModelAtEnd"])
        self.assertEqual("eval_loss", training["metricForBestModel"])
        self.assertFalse(training["greaterIsBetter"])
        self.assertEqual(2, training["earlyStoppingPatience"])
        self.assertEqual(2, training["saveTotalLimit"])
        self.assertEqual(
            {"training": "train", "validation": "validation", "contractHoldout": "evaluation"},
            self.config["splits"],
        )

    def test_validation_rejects_fixed_steps_or_missing_early_stopping(self):
        fixed = copy.deepcopy(self.config)
        fixed["training"]["epochs"] = None
        fixed["training"]["maxSteps"] = 1000
        with self.assertRaisesRegex(self.pipeline.TrainingPipelineError, "finite epoch"):
            self.pipeline.validate_training_config(fixed)

        no_early_stop = copy.deepcopy(self.config)
        no_early_stop["training"]["earlyStoppingPatience"] = 0
        with self.assertRaisesRegex(self.pipeline.TrainingPipelineError, "earlyStoppingPatience"):
            self.pipeline.validate_training_config(no_early_stop)

    def test_dataset_and_independent_contract_holdout_are_validated(self):
        result = self.pipeline.validate_dataset_and_contract_gates(
            DATASET_MANIFEST, self.config, ROOT
        )
        self.assertEqual({"train": 38, "validation": 4, "evaluation": 3}, result["counts"])
        self.assertEqual("PASS", result["contractHoldout"]["state"])
        self.assertEqual("PASS", result["preparedRuntimeContract"]["state"])
        self.assertTrue(result["contentIdsDisjoint"])

    def test_training_messages_use_only_governed_prompt_and_target(self):
        record = json.loads(
            (DATASET_MANIFEST.parent / "train.jsonl").read_text(encoding="utf-8").splitlines()[0]
        )
        messages = self.backend.training_messages(record)
        self.assertEqual(["system", "user", "assistant"], [item["role"] for item in messages])
        self.assertEqual(
            self.backend.canonical_bytes(record["trainingPrompt"]).decode("utf-8"),
            messages[1]["content"],
        )
        self.assertEqual(
            self.backend.canonical_bytes(record["trainingTarget"]).decode("utf-8"),
            messages[2]["content"],
        )

    def test_backend_argument_policy_enables_periodic_validation_and_best_model(self):
        kwargs = self.backend.training_argument_values(self.config, Path("checkpoints"))
        self.assertEqual(8.0, kwargs["num_train_epochs"])
        self.assertEqual(-1, kwargs["max_steps"])
        self.assertEqual("epoch", kwargs["eval_strategy"])
        self.assertEqual("epoch", kwargs["save_strategy"])
        self.assertTrue(kwargs["load_best_model_at_end"])
        self.assertEqual("eval_loss", kwargs["metric_for_best_model"])
        self.assertFalse(kwargs["greater_is_better"])
        self.assertEqual(2, kwargs["save_total_limit"])
        self.assertEqual(
            "checkpoint-00000012",
            self.backend.canonical_checkpoint_name("/tmp/checkpoint-12"),
        )
        with self.assertRaisesRegex(ValueError, "best checkpoint"):
            self.backend.canonical_checkpoint_name("/tmp/not-a-checkpoint")

    def test_tampered_holdout_or_runtime_schema_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            dataset = root / "datasets" / "p7-research-synthetic-training-dataset-v2"
            dataset.mkdir(parents=True)
            for path in DATASET_MANIFEST.parent.iterdir():
                (dataset / path.name).write_bytes(path.read_bytes())
            for source in (
                ROOT / "evidence/p7-t1c-research-remediation-training-governance-approval.json",
                ROOT / "datasets/p7-t4-research-remediation-source-v2/training-contract.json",
                ROOT / "ai-service/config/assistant-profiles.json",
                ROOT / "ai-service/config/schemas/structured-output-schemas.json",
            ):
                target = root / source.relative_to(ROOT)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(source.read_bytes())

            schema = root / "ai-service/config/schemas/structured-output-schemas.json"
            schema.write_bytes(schema.read_bytes() + b"\n")
            with self.assertRaisesRegex(self.pipeline.TrainingPipelineError, "runtime schema"):
                self.pipeline.validate_dataset_and_contract_gates(dataset / "manifest.json", self.config, root)


if __name__ == "__main__":
    unittest.main()
