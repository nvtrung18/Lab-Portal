import hashlib
import importlib.util
import json
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("p7t2", ROOT / "scripts" / "training-pipeline-p7-t2.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class _FakeNumpyRandom:
    def __init__(self):
        self.seed_value = None

    def seed(self, value):
        self.seed_value = value


class _FakeNumpy:
    __version__ = "test-numpy"

    def __init__(self):
        self.random = _FakeNumpyRandom()


class _FakeCuda:
    def __init__(self):
        self.seed_value = None

    def is_available(self):
        return True

    def manual_seed_all(self, value):
        self.seed_value = value


class _FakeCudnn:
    deterministic = False
    benchmark = True


class _FakeTorch:
    __version__ = "test-torch"

    def __init__(self):
        self.seed_value = None
        self.cuda = _FakeCuda()
        self.backends = type("Backends", (), {"cudnn": _FakeCudnn()})()
        self.deterministic_algorithms = None

    def manual_seed(self, value):
        self.seed_value = value

    def use_deterministic_algorithms(self, enabled, warn_only=False):
        self.deterministic_algorithms = (enabled, warn_only)


class P7T2TrainingPipelineTests(unittest.TestCase):
    def decisions(self):
        return {
            "schemaVersion": "1.0.0",
            "decisionRecordVersion": "1.0.0",
            "decisionReference": "docs/architecture/p6-t6-adapter-strategy-decision.md",
            "status": "APPROVED",
            "decisions": {
                "RESEARCH_ASSISTANT": "ADAPTER_REQUIRED",
                "LAB_ASSISTANT": "BASE_ONLY_APPROVED",
                "ADMIN_ASSISTANT": "BASE_ONLY_APPROVED",
            },
        }

    def config(self, dataset_identity, assistant_key="RESEARCH_ASSISTANT"):
        return {
            "schemaVersion": "1.0.0",
            "pipelineVersion": "1.0.0",
            "assistantKey": assistant_key,
            "baseModel": {
                "identifier": "Qwen/Qwen3-4B-Instruct-2507",
                "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
            },
            "adapter": {
                "method": "QLORA",
                "lora": {
                    "rank": 16,
                    "alpha": 32,
                    "dropout": 0.05,
                    "bias": "none",
                    "targetModules": ["q_proj", "k_proj", "v_proj", "o_proj"],
                },
                "quantization": {
                    "bits": 4,
                    "quantType": "nf4",
                    "doubleQuantization": True,
                    "computeDtype": "bfloat16",
                },
            },
            "seed": 20260821,
            "dataset": {
                "manifestReference": "approved-research-training-v1/manifest.json",
                "identity": dataset_identity,
            },
            "splits": {"training": "train", "evaluation": "validation"},
            "training": {
                "epochs": None,
                "maxSteps": 4,
                "learningRate": 0.0002,
                "batchSize": 1,
                "gradientAccumulation": 8,
                "precision": "bfloat16",
                "checkpointFrequency": 2,
            },
            "output": {
                "checkpointDirectory": "checkpoints",
                "exportDirectory": "adapter",
                "metadataFilename": "training-metadata.json",
            },
        }

    def write_dataset(self, root, suffix="one"):
        dataset_directory = Path(root) / f"dataset-{suffix}"
        dataset_directory.mkdir()
        artifacts = []
        for split_name in ("train", "validation", "evaluation"):
            content = (
                json.dumps(
                    {"contentId": f"{suffix}-{split_name}", "input": f"input-{suffix}"},
                    sort_keys=True,
                    separators=(",", ":"),
                )
                + "\n"
            ).encode("utf-8")
            filename = f"{split_name}.jsonl"
            (dataset_directory / filename).write_bytes(content)
            artifacts.append(
                {
                    "filename": filename,
                    "recordCount": 1,
                    "sha256": hashlib.sha256(content).hexdigest(),
                }
            )
        manifest = {
            "pipeline_schema_version": "1.0.0",
            "pipeline_version": "1.0.0",
            "dataset_id": f"approved-research-training-{suffix}",
            "dataset_version": "1.0.0",
            "partition": "RESEARCH",
            "approval_status": "APPROVED",
            "model_development_purpose": "TRAINING",
            "checksum_algorithm": "SHA-256",
            "artifacts": artifacts,
        }
        manifest["checksum"] = MODULE.dataset_identity(manifest)
        manifest_path = dataset_directory / "manifest.json"
        manifest_path.write_text(json.dumps(manifest, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        return manifest_path, manifest

    def run_smoke(self, root, name="run", suffix="one", config_mutator=None, resume_from=None):
        manifest_path, manifest = self.write_dataset(root, suffix)
        config = self.config(manifest["checksum"])
        if config_mutator:
            config_mutator(config)
        result = MODULE.run_pipeline(
            config,
            self.decisions(),
            manifest_path,
            Path(root) / name,
            smoke=True,
            resume_from=resume_from,
            source_commit="test-source-commit",
        )
        return result, Path(root) / name, config, manifest_path, manifest

    def test_identical_config_dataset_and_seed_produce_identical_run_identity(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            first, _, _, _, _ = self.run_smoke(temporary_directory, "first")
            second_manifest_path, second_manifest = self.write_dataset(temporary_directory, "second-copy")
            second_config = self.config(first["datasetIdentity"])
            second_manifest["dataset_id"] = "approved-research-training-one"
            second_manifest["artifacts"] = deepcopy(
                json.loads((Path(temporary_directory) / "dataset-one" / "manifest.json").read_text())["artifacts"]
            )
            second_manifest["checksum"] = MODULE.dataset_identity(second_manifest)
            self.assertEqual(first["datasetIdentity"], second_manifest["checksum"])
            second_manifest_path.write_text(json.dumps(second_manifest, sort_keys=True, indent=2) + "\n")
            for artifact in second_manifest["artifacts"]:
                source = Path(temporary_directory) / "dataset-one" / artifact["filename"]
                (second_manifest_path.parent / artifact["filename"]).write_bytes(source.read_bytes())
            second_config["dataset"]["identity"] = second_manifest["checksum"]
            second = MODULE.run_pipeline(
                second_config,
                self.decisions(),
                second_manifest_path,
                Path(temporary_directory) / "second",
                smoke=True,
                source_commit="test-source-commit",
            )

            self.assertEqual(first["trainingRunIdentity"], second["trainingRunIdentity"])
            self.assertEqual(first["trainingConfigIdentity"], second["trainingConfigIdentity"])

    def test_adapter_required_permits_offline_smoke_execution(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            result, output, _, _, _ = self.run_smoke(temporary_directory)

            self.assertEqual("COMPLETED", result["status"])
            self.assertTrue((output / "adapter" / "adapter-manifest.json").is_file())
            self.assertEqual("SMOKE_ONLY_NO_MODEL_QUALITY_EVIDENCE", result["qualityEvidence"])

    def test_base_only_approved_returns_deterministic_skipped_result(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            manifest_path, manifest = self.write_dataset(temporary_directory)
            config = self.config(manifest["checksum"], assistant_key="LAB_ASSISTANT")
            first = MODULE.run_pipeline(
                config,
                self.decisions(),
                manifest_path,
                Path(temporary_directory) / "first",
                smoke=True,
                source_commit="test-source-commit",
            )
            second = MODULE.run_pipeline(
                deepcopy(config),
                self.decisions(),
                manifest_path,
                Path(temporary_directory) / "second",
                smoke=True,
                source_commit="test-source-commit",
            )

            self.assertEqual(first, second)
            self.assertEqual("SKIPPED", first["status"])
            self.assertEqual("BASE_ONLY_APPROVED", first["decision"])
            self.assertEqual([], first["checkpoints"])
            self.assertEqual([], first["exportedArtifacts"])

    def test_unknown_decision_fails_closed(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            manifest_path, manifest = self.write_dataset(temporary_directory)
            decisions = self.decisions()
            decisions["decisions"]["RESEARCH_ASSISTANT"] = "UNKNOWN"

            with self.assertRaisesRegex(MODULE.TrainingPipelineError, "unsupported decision"):
                MODULE.run_pipeline(
                    self.config(manifest["checksum"]),
                    decisions,
                    manifest_path,
                    Path(temporary_directory) / "denied",
                    smoke=True,
                )

    def test_seed_is_required_and_applied_consistently_to_available_libraries(self):
        config = self.config("0" * 64)
        del config["seed"]
        with self.assertRaisesRegex(MODULE.TrainingPipelineError, "seed"):
            MODULE.validate_training_config(config)

        numpy = _FakeNumpy()
        torch = _FakeTorch()
        report = MODULE.seed_everything(1234, {"numpy": numpy, "torch": torch})
        self.assertEqual(1234, numpy.random.seed_value)
        self.assertEqual(1234, torch.seed_value)
        self.assertEqual(1234, torch.cuda.seed_value)
        self.assertTrue(torch.backends.cudnn.deterministic)
        self.assertFalse(torch.backends.cudnn.benchmark)
        self.assertEqual((True, True), torch.deterministic_algorithms)
        self.assertEqual(1234, report["seed"])

    def test_checkpoint_names_are_deterministic(self):
        self.assertEqual("checkpoint-00000002", MODULE.checkpoint_name(2))
        self.assertEqual("checkpoint-00000100", MODULE.checkpoint_name(100))

    def test_compatible_resume_succeeds_and_preserves_adapter_hashes(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            first, first_output, config, manifest_path, _ = self.run_smoke(temporary_directory, "first")
            resume_checkpoint = first_output / "checkpoints" / "checkpoint-00000002"
            resumed = MODULE.run_pipeline(
                deepcopy(config),
                self.decisions(),
                manifest_path,
                Path(temporary_directory) / "resumed",
                smoke=True,
                resume_from=resume_checkpoint,
                source_commit="test-source-commit",
            )

            self.assertEqual("COMPLETED", resumed["status"])
            self.assertEqual("checkpoint-00000002", resumed["resumeSource"]["checkpointName"])
            self.assertEqual(first["exportedArtifacts"], resumed["exportedArtifacts"])

    def test_incompatible_config_resume_fails(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            _, first_output, _, _, _ = self.run_smoke(temporary_directory, "first")
            manifest_path, manifest = self.write_dataset(temporary_directory, "same-data")
            original_manifest = json.loads((Path(temporary_directory) / "dataset-one" / "manifest.json").read_text())
            for artifact in original_manifest["artifacts"]:
                source = Path(temporary_directory) / "dataset-one" / artifact["filename"]
                (manifest_path.parent / artifact["filename"]).write_bytes(source.read_bytes())
            original_manifest["checksum"] = MODULE.dataset_identity(original_manifest)
            manifest_path.write_text(json.dumps(original_manifest, sort_keys=True, indent=2) + "\n")
            config = self.config(original_manifest["checksum"])
            config["training"]["learningRate"] = 0.0001

            with self.assertRaisesRegex(MODULE.TrainingPipelineError, "config identity mismatch"):
                MODULE.run_pipeline(
                    config,
                    self.decisions(),
                    manifest_path,
                    Path(temporary_directory) / "denied",
                    smoke=True,
                    resume_from=first_output / "checkpoints" / "checkpoint-00000002",
                )

    def test_incompatible_dataset_resume_fails(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            _, first_output, _, _, _ = self.run_smoke(temporary_directory, "first")
            second_manifest_path, second_manifest = self.write_dataset(temporary_directory, "different")

            with self.assertRaisesRegex(MODULE.TrainingPipelineError, "dataset identity mismatch"):
                MODULE.run_pipeline(
                    self.config(second_manifest["checksum"]),
                    self.decisions(),
                    second_manifest_path,
                    Path(temporary_directory) / "denied",
                    smoke=True,
                    resume_from=first_output / "checkpoints" / "checkpoint-00000002",
                )

    def test_incompatible_base_model_revision_resume_fails(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            _, first_output, config, manifest_path, _ = self.run_smoke(temporary_directory, "first")
            config["baseModel"]["revision"] = "different-immutable-revision"

            with self.assertRaisesRegex(MODULE.TrainingPipelineError, "base model revision mismatch"):
                MODULE.run_pipeline(
                    config,
                    self.decisions(),
                    manifest_path,
                    Path(temporary_directory) / "denied",
                    smoke=True,
                    resume_from=first_output / "checkpoints" / "checkpoint-00000002",
                )

    def test_adapter_export_metadata_and_checksums_are_deterministic(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            first, first_output, _, _, _ = self.run_smoke(temporary_directory, "first")
            second_manifest_path = Path(temporary_directory) / "dataset-one" / "manifest.json"
            manifest = json.loads(second_manifest_path.read_text())
            second = MODULE.run_pipeline(
                self.config(manifest["checksum"]),
                self.decisions(),
                second_manifest_path,
                Path(temporary_directory) / "second",
                smoke=True,
                source_commit="test-source-commit",
            )

            self.assertEqual(first["exportedArtifacts"], second["exportedArtifacts"])
            for artifact in first["exportedArtifacts"]:
                content = (first_output / "adapter" / artifact["filename"]).read_bytes()
                self.assertEqual(artifact["sha256"], hashlib.sha256(content).hexdigest())
            self.assertEqual("CANDIDATE_ONLY", first["adapterDisposition"])

    def test_run_metadata_contains_required_provenance(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            result, output, _, _, _ = self.run_smoke(temporary_directory)
            stored = json.loads((output / "training-metadata.json").read_text(encoding="utf-8"))

            required = {
                "schemaVersion",
                "pipelineVersion",
                "assistantKey",
                "decision",
                "status",
                "baseModel",
                "datasetManifestReference",
                "datasetIdentity",
                "trainingConfigIdentity",
                "trainingRunIdentity",
                "seed",
                "adapterMethod",
                "trainingParameters",
                "checkpoints",
                "resumeSource",
                "exportedArtifacts",
                "sourceCommit",
                "runtimeVersions",
            }
            self.assertTrue(required.issubset(stored))
            self.assertEqual(result, stored)

    def test_absolute_local_paths_cannot_enter_canonical_identity(self):
        config = self.config("0" * 64)
        config["dataset"]["manifestReference"] = str(Path.cwd().resolve() / "manifest.json")

        with self.assertRaisesRegex(MODULE.TrainingPipelineError, "absolute paths are forbidden"):
            MODULE.training_config_identity(config)

    def test_p7_t1_manifest_identity_and_artifact_checksums_are_validated(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            manifest_path, manifest = self.write_dataset(temporary_directory)
            manifest["checksum"] = "f" * 64
            manifest_path.write_text(json.dumps(manifest, sort_keys=True, indent=2) + "\n")

            with self.assertRaisesRegex(MODULE.TrainingPipelineError, "manifest identity mismatch"):
                MODULE.run_pipeline(
                    self.config("f" * 64),
                    self.decisions(),
                    manifest_path,
                    Path(temporary_directory) / "denied",
                    smoke=True,
                )

    def test_tampered_p7_t1_artifact_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            manifest_path, manifest = self.write_dataset(temporary_directory)
            (manifest_path.parent / "train.jsonl").write_text("tampered\n", encoding="utf-8")

            with self.assertRaisesRegex(MODULE.TrainingPipelineError, "artifact checksum mismatch"):
                MODULE.run_pipeline(
                    self.config(manifest["checksum"]),
                    self.decisions(),
                    manifest_path,
                    Path(temporary_directory) / "denied",
                    smoke=True,
                )

    def test_smoke_mode_requires_no_optional_ml_library_gpu_or_download(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            manifest_path, manifest = self.write_dataset(temporary_directory)
            result = MODULE.run_pipeline(
                self.config(manifest["checksum"]),
                self.decisions(),
                manifest_path,
                Path(temporary_directory) / "offline",
                smoke=True,
                optional_seed_modules={},
                source_commit="test-source-commit",
            )

            self.assertEqual("SMOKE", result["backend"])
            self.assertEqual({"python": MODULE.platform.python_version()}, result["runtimeVersions"])

    def test_checked_in_training_config_and_decision_manifest_are_valid(self):
        training_config = json.loads((ROOT / "config" / "p7-t2-training-pipeline.json").read_text(encoding="utf-8"))
        decision_manifest = json.loads((ROOT / "config" / "p6-t6-adapter-decisions.json").read_text(encoding="utf-8"))

        MODULE.validate_training_config(training_config)
        MODULE.validate_decision_manifest(decision_manifest)
        self.assertEqual("QLORA", training_config["adapter"]["method"])
        self.assertEqual("ADAPTER_REQUIRED", decision_manifest["decisions"]["RESEARCH_ASSISTANT"])
        self.assertEqual("BASE_ONLY_APPROVED", decision_manifest["decisions"]["LAB_ASSISTANT"])
        self.assertEqual("BASE_ONLY_APPROVED", decision_manifest["decisions"]["ADMIN_ASSISTANT"])


if __name__ == "__main__":
    unittest.main()
