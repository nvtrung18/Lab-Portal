import copy
import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CONFIG_PATH = ROOT / "config" / "p7-t2-training-pipeline-t4-remediation-v3.json"
STABILITY_CONFIG_PATH = (
    ROOT / "config" / "p7-t2-training-pipeline-t4-remediation-v3-stability.json"
)
PIPELINE_PATH = ROOT / "scripts" / "training-pipeline-p7-t2-remediation-v3.py"
BACKEND_PATH = ROOT / "scripts" / "p7-t2-real-training-remediation-v3.py"
BUILDER_PATH = ROOT / "scripts" / "build-p7-t2-research-remediation-v3-bundle.py"
VALIDATOR_PATH = ROOT / "scripts" / "validate-p7-t2-research-remediation-v3-bundle.py"
DATASET_MANIFEST = (
    ROOT / "datasets" / "p7-research-synthetic-training-dataset-v3" / "manifest.json"
)
INCIDENT_PATH = (
    ROOT / "evidence" / "p7-t2-real-training" / "remediation-v3-quarantine" / "incident.json"
)
RETRY_REQUEST_PATH = (
    ROOT
    / "config"
    / "p7-t4-research-remediation-governance-v3"
    / "stability-retry-approval-request.json"
)


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T2ResearchRemediationV3TrainingBundleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pipeline = load_module("p7_t2_remediation_pipeline_v3", PIPELINE_PATH)
        cls.backend = load_module("p7_t2_remediation_backend_v3", BACKEND_PATH)
        cls.builder = load_module("p7_t2_remediation_bundle_builder_v3", BUILDER_PATH)
        cls.validator = load_module("p7_t2_remediation_bundle_validator_v3", VALIDATOR_PATH)
        cls.config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
        cls.stability_config = json.loads(STABILITY_CONFIG_PATH.read_text(encoding="utf-8"))

    def build(self, parent: str, suffix: str):
        output = Path(parent) / suffix / self.builder.BUNDLE_NAME
        archive = output.parent / f"{self.builder.BUNDLE_NAME}.zip"
        manifest = self.builder.build_bundle(
            source_root=ROOT,
            output_dir=output,
            zip_path=archive,
            source_commit="23f920f4da5b4f7266d0991af3af34921994600b",
            enforce_committed_sources=False,
        )
        return output, archive, manifest

    def test_config_binds_v3_governance_dataset_and_finite_schedule(self):
        self.pipeline.validate_training_config(self.config)
        self.assertEqual(
            "430390b22936bdea27c7e5b4022795ef483b55ac21f84e3e52cc663b9aaf9d10",
            self.config["dataset"]["identity"],
        )
        self.assertEqual("3.0.0", self.config["pipelineVersion"])
        self.assertEqual(4, self.config["training"]["epochs"])
        self.assertIsNone(self.config["training"]["maxSteps"])
        self.assertTrue(self.config["training"]["loadBestModelAtEnd"])
        self.assertEqual(
            "a1f92aec9caca9b053daf780c1bfde951abdb88e2fe3e92f4f2545c676d45015",
            self.config["contractGates"]["trainingApprovalIdentity"],
        )

    def test_dataset_contract_gates_and_closed_messages_pass(self):
        result = self.pipeline.validate_dataset_and_contract_gates(
            DATASET_MANIFEST, self.config, ROOT
        )
        self.assertEqual(
            {"train": 214, "validation": 22, "evaluation": 34},
            result["counts"],
        )
        self.assertEqual("PASS", result["contractHoldout"]["state"])
        self.assertEqual("PASS", result["preparedRuntimeContract"]["state"])
        record = json.loads(
            (DATASET_MANIFEST.parent / "train.jsonl")
            .read_text(encoding="utf-8")
            .splitlines()[0]
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

    def test_bundle_is_deterministic_valid_and_weight_free(self):
        with tempfile.TemporaryDirectory() as directory:
            first, first_zip, first_manifest = self.build(directory, "first")
            second, second_zip, second_manifest = self.build(directory, "second")
            self.assertEqual(first_manifest, second_manifest)
            self.assertEqual(first_zip.read_bytes(), second_zip.read_bytes())
            self.assertEqual(
                hashlib.sha256(first_zip.read_bytes()).hexdigest(),
                hashlib.sha256(second_zip.read_bytes()).hexdigest(),
            )
            validated = self.validator.validate_bundle(first)
            self.assertEqual(first_manifest, validated)
            paths = {item["path"] for item in first_manifest["fileInventory"]}
            self.assertIn("scripts/p7-t2-real-training-remediation-v3.py", paths)
            self.assertIn("datasets/p7-research-synthetic-training-dataset-v3/train.jsonl", paths)
            self.assertFalse(
                any(path.endswith((".safetensors", ".bin", ".pt", ".ckpt")) for path in paths)
            )

    def test_bundle_tampering_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle, _, _ = self.build(directory, "tamper")
            config = bundle / "config/p7-t2-training-pipeline-t4-remediation-v3.json"
            config.write_bytes(config.read_bytes() + b"\n")
            with self.assertRaisesRegex(ValueError, "checksum"):
                self.validator.validate_bundle(bundle)

    def test_non_finite_trainer_log_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "non-finite logged metric.*grad_norm"):
            self.backend.validate_finite_log_history(
                [
                    {"loss": 1.0525, "grad_norm": float("nan"), "epoch": 0.22},
                ],
                "real remediation training",
            )

    def test_checkpoint_validator_rejects_non_finite_trainer_state(self):
        with tempfile.TemporaryDirectory() as directory:
            checkpoint = Path(directory) / "checkpoint-00000104"
            checkpoint.mkdir()
            (checkpoint / "trainer_state.json").write_text(
                json.dumps(
                    {
                        "log_history": [
                            {"loss": 1.0525, "grad_norm": float("nan"), "epoch": 0.22}
                        ]
                    }
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "non-finite logged metric.*grad_norm"):
                self.backend.validate_finite_checkpoint_metrics(Path(directory))

    def test_runtime_callback_stops_on_non_finite_logged_metric(self):
        class TrainerCallback:
            pass

        transformers = type("Transformers", (), {"TrainerCallback": TrainerCallback})
        callback = self.backend.non_finite_metric_callback(transformers)
        state = type("State", (), {"global_step": 6})()

        with self.assertRaisesRegex(ValueError, "non-finite logged metric.*grad_norm"):
            callback.on_log(
                None,
                state,
                None,
                logs={"loss": 1.0525, "grad_norm": float("nan"), "epoch": 0.22},
            )

    def test_stability_retry_configures_lower_initial_gradient_scale(self):
        stability_config = copy.deepcopy(self.config)
        stability_config["training"]["gradientScalerInitialScale"] = 32768
        self.pipeline.validate_training_config(stability_config)

        class Scaler:
            def __init__(self):
                self.value = {
                    "scale": 65536.0,
                    "growth_factor": 2.0,
                    "backoff_factor": 0.5,
                    "growth_interval": 2000,
                    "_growth_tracker": 0,
                }

            def state_dict(self):
                return dict(self.value)

            def load_state_dict(self, value):
                self.value = dict(value)

        scaler = Scaler()
        trainer = type(
            "Trainer",
            (),
            {"accelerator": type("Accelerator", (), {"scaler": scaler})()},
        )()
        self.backend.configure_gradient_scaler(trainer, stability_config)

        self.assertEqual(32768.0, scaler.state_dict()["scale"])

    def test_stability_retry_rejects_unsupported_gradient_scale(self):
        stability_config = copy.deepcopy(self.config)
        stability_config["training"]["gradientScalerInitialScale"] = 16384

        with self.assertRaisesRegex(
            self.pipeline.TrainingPipelineError,
            "gradientScalerInitialScale",
        ):
            self.pipeline.validate_training_config(stability_config)

    def test_stability_retry_is_exactly_bound_and_pending_approval(self):
        self.pipeline.validate_training_config(self.stability_config)
        self.assertEqual(
            "58f945c432208e032edcd4180116de8540b429cc4a5d070a4c90e7f9a8111667",
            self.pipeline.training_config_identity(self.stability_config),
        )
        self.assertEqual(
            "961521eff75736427b4d9b405e6616dd0b989af41cf5d5a017ba3d2a64a94005",
            self.pipeline.training_run_identity(self.stability_config),
        )
        expected = copy.deepcopy(self.config)
        expected["training"]["gradientScalerInitialScale"] = 32768
        self.assertEqual(expected, self.stability_config)

        incident = json.loads(INCIDENT_PATH.read_text(encoding="utf-8"))
        self.assertEqual(
            incident["incidentIdentity"],
            self.pipeline.artifact_identity(incident, "incidentIdentity"),
        )
        request = json.loads(RETRY_REQUEST_PATH.read_text(encoding="utf-8"))
        self.assertEqual(
            request["requestIdentity"],
            self.pipeline.artifact_identity(request, "requestIdentity"),
        )
        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertIsNone(request["approval"])
        self.assertFalse(request["currentState"]["retryAuthorized"])


if __name__ == "__main__":
    unittest.main()
