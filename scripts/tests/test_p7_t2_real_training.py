import importlib.util
import copy
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "p7_t2_real_training",
    ROOT / "scripts" / "p7-t2-real-training.py",
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class P7T2RealTrainingTests(unittest.TestCase):
    def config(self):
        return json.loads(
            (ROOT / "config" / "p7-t2-training-pipeline.json").read_text(encoding="utf-8")
        )

    def t4_config(self):
        return json.loads(
            (ROOT / "config" / "p7-t2-training-pipeline-t4.json").read_text(encoding="utf-8")
        )

    def record(self):
        return {
            "contentId": "a" * 64,
            "schemaVersion": "1.0.0",
            "domain": "RESEARCH",
            "recordType": "TASK_PROPOSAL_DRAFT",
            "visibility": "RESEARCH_ASSISTANT_ONLY",
            "useCaseId": "RESEARCH_UC_004",
            "input": {"task": "Draft a synthetic proposal."},
            "payload": {"proposalKind": "TASK_PROPOSAL_DRAFT"},
            "expectedOutput": {
                "behavior": "DRAFT_ONLY",
                "contentType": "NON_OFFICIAL_STRUCTURED_DRAFT",
                "actionRisk": "DRAFT_ONLY",
                "draft": {"title": "Synthetic draft", "steps": ["Keep it advisory."]},
            },
        }

    def test_training_messages_keep_expected_output_in_assistant_turn_only(self):
        messages = MODULE.training_messages(self.record())

        self.assertEqual(["system", "user", "assistant"], [item["role"] for item in messages])
        self.assertNotIn("expectedOutput", messages[1]["content"])
        self.assertNotIn("contentId", messages[1]["content"])
        self.assertEqual(
            self.record()["expectedOutput"],
            json.loads(messages[2]["content"]),
        )

    def test_model_snapshot_requires_exact_identifier_and_revision_marker(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            model_path = Path(temporary_directory)
            (model_path / "config.json").write_text("{}\n", encoding="utf-8")
            marker = {
                "identifier": self.config()["baseModel"]["identifier"],
                "revision": self.config()["baseModel"]["revision"],
            }
            (model_path / MODULE.MODEL_IDENTITY_FILENAME).write_text(
                json.dumps(marker), encoding="utf-8"
            )

            self.assertEqual(marker, MODULE.validate_model_snapshot(model_path, self.config()))
            marker["revision"] = "0" * 40
            (model_path / MODULE.MODEL_IDENTITY_FILENAME).write_text(
                json.dumps(marker), encoding="utf-8"
            )
            with self.assertRaisesRegex(ValueError, "revision"):
                MODULE.validate_model_snapshot(model_path, self.config())

    def test_real_metadata_contract_rejects_smoke_and_accepts_real_candidate(self):
        config = self.config()
        metadata = {
            "status": "COMPLETED",
            "backend": "REAL_QLORA",
            "realTraining": True,
            "qualityEvidence": "REAL_TRAINING_EXECUTION",
            "adapterDisposition": "CANDIDATE_ONLY",
            "assistantKey": "RESEARCH_ASSISTANT",
            "baseModel": config["baseModel"],
            "datasetIdentity": config["dataset"]["identity"],
            "trainingConfigIdentity": MODULE.training_config_identity(config),
            "trainingRunIdentity": MODULE.training_run_identity(config),
            "candidateId": "3" * 64,
            "seed": config["seed"],
            "adapterMethod": "QLORA",
            "checkpoints": [{"checkpointName": "checkpoint-00001000", "globalStep": 1000}],
            "exportedArtifacts": [{"filename": "adapter_model.safetensors", "sha256": "4" * 64}],
            "sourceCommit": "5" * 40,
            "metrics": {"trainLoss": 0.5, "validationLoss": 0.6},
            "actualTraining": {"globalSteps": 1000, "trainRecords": 36, "validationRecords": 3},
            "runtimeVersions": dict(MODULE.EXPECTED_RUNTIME_VERSIONS),
        }

        MODULE.validate_real_metadata_contract(metadata, config)
        metadata["backend"] = "SMOKE"
        with self.assertRaisesRegex(ValueError, "REAL_QLORA"):
                MODULE.validate_real_metadata_contract(metadata, config)

    def test_checked_in_dataset_serialization_uses_only_train_and_validation(self):
        manifest_path = ROOT / self.config()["dataset"]["manifestReference"]
        inputs = MODULE.load_training_inputs(manifest_path, self.config())

        self.assertEqual(36, len(inputs["trainingRecords"]))
        self.assertEqual(3, len(inputs["validationRecords"]))
        self.assertEqual("train.jsonl", inputs["trainingArtifact"]["filename"])
        self.assertEqual("validation.jsonl", inputs["validationArtifact"]["filename"])
        self.assertNotEqual(
            "evaluation.jsonl", inputs["trainingArtifact"]["filename"]
        )

    def test_real_resume_checkpoint_is_bound_to_config_and_file_inventory(self):
        config = self.config()
        with tempfile.TemporaryDirectory() as temporary_directory:
            checkpoint = Path(temporary_directory) / "checkpoint-00001000"
            checkpoint.mkdir()
            state = checkpoint / "adapter_model.safetensors"
            state.write_bytes(b"real-test-adapter-state")
            inventory = MODULE._inventory(checkpoint)
            document = {
                "schemaVersion": "1.0.0",
                "pipelineVersion": config["pipelineVersion"],
                "backend": "REAL_QLORA",
                "trainingRunIdentity": MODULE.training_run_identity(config),
                "trainingConfigIdentity": MODULE.training_config_identity(config),
                "datasetIdentity": config["dataset"]["identity"],
                "baseModel": config["baseModel"],
                "assistantKey": config["assistantKey"],
                "adapterMethod": config["adapter"]["method"],
                "globalStep": 1000,
                "seed": config["seed"],
                "artifactInventory": inventory,
            }
            document["checkpointIdentity"] = MODULE._identity(document, "checkpointIdentity")
            (checkpoint / "checkpoint-metadata.json").write_text(
                json.dumps(document, sort_keys=True, indent=2) + "\n", encoding="utf-8"
            )

            self.assertEqual(document, MODULE.validate_resume_checkpoint(checkpoint, config))
            state.write_bytes(b"tampered")
            with self.assertRaisesRegex(ValueError, "checksum|inventory"):
                MODULE.validate_resume_checkpoint(checkpoint, config)

    def test_t4_profile_changes_only_compute_precision(self):
        expected = copy.deepcopy(self.config())
        expected["adapter"]["quantization"]["computeDtype"] = "float16"
        expected["training"]["precision"] = "float16"

        self.assertEqual(expected, self.t4_config())

    def test_t4_cuda_runtime_accepts_float16_and_rejects_bfloat16(self):
        class Properties:
            name = "Tesla T4"
            total_memory = 16 * 1024**3

        class Cuda:
            @staticmethod
            def is_available():
                return True

            @staticmethod
            def device_count():
                return 1

            @staticmethod
            def is_bf16_supported():
                return False

            @staticmethod
            def get_device_properties(_index):
                return Properties()

        class Torch:
            cuda = Cuda()
            version = type("Version", (), {"cuda": "11.8"})()
            float16 = object()
            bfloat16 = object()
            float32 = object()

        gpu = MODULE._validate_cuda_runtime(Torch(), "float16")
        self.assertEqual("Tesla T4", gpu["name"])
        self.assertIs(Torch.float16, MODULE._training_dtype(Torch(), self.t4_config()))
        with self.assertRaisesRegex(ValueError, "bfloat16"):
            MODULE._validate_cuda_runtime(Torch(), "bfloat16")


if __name__ == "__main__":
    unittest.main()
