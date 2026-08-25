import importlib.util
import hashlib
import json
import shutil
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def load_module(name, relative_path):
    specification = importlib.util.spec_from_file_location(name, ROOT / relative_path)
    module = importlib.util.module_from_spec(specification)
    assert specification.loader is not None
    specification.loader.exec_module(module)
    return module


BUILDER = load_module(
    "p7t4_bundle_builder", "scripts/build-p7-t4-research-evaluation-bundle.py"
)
VALIDATOR = load_module(
    "p7t4_bundle_validator", "scripts/validate-p7-t4-research-evaluation-bundle.py"
)


class P7T4ResearchEvaluationBundleTests(unittest.TestCase):
    def test_bundle_module_loaders_do_not_create_python_bytecode(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            module_path = Path(temporary_directory) / "temporary_module.py"
            module_path.write_text("VALUE = 1\n", encoding="utf-8")

            BUILDER._load_module("p7t4_builder_bytecode_test", module_path)
            VALIDATOR._load_module("p7t4_validator_bytecode_test", module_path)

            self.assertFalse((module_path.parent / "__pycache__").exists())

    def test_remediation_evidence_is_projected_without_changing_training_facts(self):
        reference = (
            "evidence/p7-t2-real-training/remediation-v2/real-training-evidence.json"
        )
        source_path = ROOT / reference
        source_payload = source_path.read_bytes()
        source = json.loads(source_payload)

        projected = BUILDER.build_evaluation_compatibility_evidence(
            source_payload,
            source_reference=reference,
            expected_source_sha256=hashlib.sha256(source_payload).hexdigest(),
        )

        self.assertEqual("1.0.0", projected["schemaVersion"])
        self.assertEqual(
            "P7-T2-REAL-TRAINING-EXECUTION-EVIDENCE",
            projected["artifactType"],
        )
        self.assertEqual(source["candidateId"], projected["candidateId"])
        self.assertEqual(source["metrics"], projected["metrics"])
        self.assertEqual(source["actualTraining"], projected["actualTraining"])
        self.assertEqual(
            source["artifactIdentity"],
            projected["remediationSourceEvidence"]["artifactIdentity"],
        )
        self.assertEqual(
            projected["artifactIdentity"],
            BUILDER.P7T4.artifact_identity(projected),
        )

    def test_remediation_evidence_projection_rejects_wrong_source_hash(self):
        reference = (
            "evidence/p7-t2-real-training/remediation-v2/real-training-evidence.json"
        )
        source_payload = (ROOT / reference).read_bytes()

        with self.assertRaisesRegex(BUILDER.BundleBuildError, "source SHA-256"):
            BUILDER.build_evaluation_compatibility_evidence(
                source_payload,
                source_reference=reference,
                expected_source_sha256="0" * 64,
            )

    def test_stability_retry_evidence_projects_without_rewriting_training_facts(self):
        reference = (
            "evidence/p7-t2-real-training/remediation-v3-stability/"
            "real-training-evidence.json"
        )
        source_payload = (ROOT / reference).read_bytes()
        source = json.loads(source_payload)

        projected = BUILDER.build_evaluation_compatibility_evidence(
            source_payload,
            source_reference=reference,
            expected_source_sha256=(
                "710584c0bc23beba0c0e76f17472eb4aea09351b04f7c93ed08dc535eed17937"
            ),
        )

        self.assertEqual(source["candidateId"], projected["candidateId"])
        self.assertEqual(source["trainingRunIdentity"], projected["trainingRunIdentity"])
        self.assertEqual(source["metrics"], projected["metrics"])
        self.assertEqual(source["actualTraining"], projected["actualTraining"])
        self.assertEqual(
            reference,
            projected["remediationSourceEvidence"]["reference"],
        )
        self.assertEqual(
            projected["artifactIdentity"],
            BUILDER.P7T4.artifact_identity(projected),
        )

    def test_remediation_bundle_sources_map_new_evidence_to_frozen_logical_paths(self):
        sources = BUILDER.bundle_sources(ROOT, remediation=True)

        self.assertEqual(
            ROOT / "config/p7-t4-research-independent-evaluation-remediation.json",
            sources["config/p7-t4-research-independent-evaluation.json"],
        )
        self.assertEqual(
            ROOT / "evidence/p7-t2-real-training/remediation-v2/adapter-manifest.json",
            sources["evidence/p7-t2-real-training/adapter-manifest.json"],
        )
        self.assertNotIn(
            "evidence/p7-t2-real-training/real-training-evidence.json",
            sources,
        )
        self.assertIn(
            "evidence/p7-t2-real-training/remediation-v2/training-metadata.json",
            sources,
        )

    def test_stability_retry_bundle_sources_preserve_old_failures_and_bind_new_candidate(self):
        sources = BUILDER.bundle_sources(ROOT, stability_retry=True)

        self.assertEqual(
            ROOT
            / "config/p7-t4-research-independent-evaluation-remediation-v3-stability.json",
            sources["config/p7-t4-research-independent-evaluation.json"],
        )
        self.assertEqual(
            ROOT
            / "evidence/p7-t2-real-training/remediation-v3-stability/adapter-manifest.json",
            sources["evidence/p7-t2-real-training/adapter-manifest.json"],
        )
        self.assertIn(
            "evidence/p7-t2-real-training/remediation-v3-stability/real-training-evidence.json",
            sources,
        )
        self.assertNotIn(
            "evidence/p7-t4-research-independent-evaluation/automatic-fail-remediation-v2/comparison.json",
            sources,
        )

    def test_bundle_sources_reject_multiple_candidate_modes(self):
        with self.assertRaisesRegex(BUILDER.BundleBuildError, "one candidate mode"):
            BUILDER.bundle_sources(ROOT, remediation=True, stability_retry=True)

    def test_bundle_preflight_resolves_suite_paths_from_staged_bundle(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            source_root = temporary_root / "source"
            for relative in BUILDER.SOURCE_FILES:
                destination = source_root / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(ROOT / relative, destination)
            (source_root / "bundle-manifest.json").write_text(
                json.dumps({"sourceCommit": "a" * 40}), encoding="utf-8"
            )

            adapter = temporary_root / "adapter"
            adapter.mkdir()
            (adapter / "adapter_config.json").write_text("{}\n", encoding="utf-8")
            (adapter / "adapter_model.safetensors").write_bytes(b"adapter")
            inventory = BUILDER.P7T4._adapter_inventory(adapter)
            adapter_identity = hashlib.sha256(
                BUILDER.canonical_bytes(inventory)
            ).hexdigest()
            training_run_identity = "b" * 64
            candidate_id = hashlib.sha256(
                BUILDER.canonical_bytes(
                    {
                        "trainingRunIdentity": training_run_identity,
                        "adapterIdentity": adapter_identity,
                    }
                )
            ).hexdigest()
            manifest = {
                "schemaVersion": "1.0.0",
                "pipelineVersion": "test",
                "backend": "REAL_QLORA",
                "realTraining": True,
                "adapterDisposition": "CANDIDATE_ONLY",
                "qualityEvidence": "REAL_TRAINING_EXECUTION",
                "assistantKey": "RESEARCH_ASSISTANT",
                "baseModel": BUILDER.P7T4._load_json(
                    source_root / "config/p7-t4-research-independent-evaluation.json"
                )["baseModel"],
                "datasetIdentity": "c" * 64,
                "trainingConfigIdentity": "d" * 64,
                "trainingRunIdentity": training_run_identity,
                "candidateId": candidate_id,
                "adapterIdentity": adapter_identity,
                "seed": 1,
                "sourceCommit": "a" * 40,
                "artifacts": inventory,
            }
            BUILDER._write_json(adapter / "adapter-manifest.json", manifest)
            config_path = source_root / BUILDER.CANONICAL_EVALUATION_CONFIG_REFERENCE
            config = json.loads(config_path.read_text(encoding="utf-8"))
            config["adapter"]["candidateId"] = candidate_id
            config["adapter"]["adapterIdentity"] = adapter_identity
            BUILDER._write_json(config_path, config)
            evidence_path = source_root / BUILDER.CANONICAL_REAL_EVIDENCE_REFERENCE
            evidence = {
                "artifactType": "P7-T2-REAL-TRAINING-EXECUTION-EVIDENCE",
                "schemaVersion": "1.0.0",
                "backend": "REAL_QLORA",
                "realTraining": True,
                "qualityEvidence": "REAL_TRAINING_EXECUTION",
                "candidateId": candidate_id,
                "baseModel": manifest["baseModel"],
                "exportedArtifacts": [
                    {
                        "filename": path.name,
                        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                        "size": path.stat().st_size,
                    }
                    for path in sorted(adapter.iterdir(), key=lambda item: item.name)
                    if path.is_file()
                ],
            }
            evidence["artifactIdentity"] = BUILDER.P7T4.artifact_identity(evidence)
            BUILDER._write_json(evidence_path, evidence)
            manifest_path = source_root / BUILDER.CANONICAL_ADAPTER_MANIFEST_REFERENCE
            BUILDER._write_json(manifest_path, manifest)

            bundle_root, _, result = BUILDER.build_bundle(
                source_root,
                adapter,
                temporary_root / "output",
            )

            self.assertEqual(candidate_id, result["candidateId"])
            self.assertTrue((bundle_root / "bundle-manifest.json").is_file())
            self.assertFalse(
                any(
                    "__pycache__" in path.parts or path.suffix == ".pyc"
                    for path in bundle_root.rglob("*")
                )
            )

    def test_bundle_sources_exclude_training_data_checkpoints_and_base_weights(self):
        sources = set(BUILDER.SOURCE_FILES)

        self.assertIn("scripts/research-independent-evaluation-p7-t4.py", sources)
        self.assertIn("evals/p6-t4-evaluation-suites.yaml", sources)
        self.assertFalse(any(value.startswith("datasets/") for value in sources))
        self.assertFalse(any("checkpoint" in value.lower() for value in sources))
        self.assertFalse(any(Path(value).name in {"model.safetensors", "pytorch_model.bin"} for value in sources))

    def test_bundle_inventory_rejects_payload_tampering(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            bundle_root = Path(temporary_directory)
            payload = bundle_root / "payload.txt"
            payload.write_text("governed", encoding="utf-8")
            inventory = BUILDER.bundle_inventory(bundle_root)
            manifest = {
                "artifactType": "P7-T4-RESEARCH-EVALUATION-BUNDLE",
                "schemaVersion": "1.0.0",
                "sourceCommit": "a" * 40,
                "candidateId": "b" * 64,
                "adapterIdentity": "c" * 64,
                "suite": {"id": "suite", "version": "1", "digest": "d" * 64},
                "files": inventory,
            }
            manifest["bundleIdentity"] = BUILDER.manifest_identity(manifest)
            (bundle_root / "bundle-manifest.json").write_text(
                json.dumps(manifest, sort_keys=True), encoding="utf-8"
            )

            VALIDATOR.validate_bundle_inventory(bundle_root, manifest)
            payload.write_text("tampered", encoding="utf-8")

            with self.assertRaisesRegex(VALIDATOR.BundleValidationError, "inventory"):
                VALIDATOR.validate_bundle_inventory(bundle_root, manifest)

    def test_bundle_inventory_rejects_unlisted_file(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            bundle_root = Path(temporary_directory)
            (bundle_root / "payload.txt").write_text("governed", encoding="utf-8")
            inventory = BUILDER.bundle_inventory(bundle_root)
            manifest = {
                "artifactType": "P7-T4-RESEARCH-EVALUATION-BUNDLE",
                "schemaVersion": "1.0.0",
                "sourceCommit": "a" * 40,
                "candidateId": "b" * 64,
                "adapterIdentity": "c" * 64,
                "suite": {"id": "suite", "version": "1", "digest": "d" * 64},
                "files": inventory,
            }
            manifest["bundleIdentity"] = BUILDER.manifest_identity(manifest)
            (bundle_root / "bundle-manifest.json").write_text(
                json.dumps(manifest, sort_keys=True), encoding="utf-8"
            )
            (bundle_root / "unlisted.txt").write_text("unexpected", encoding="utf-8")

            with self.assertRaisesRegex(VALIDATOR.BundleValidationError, "inventory"):
                VALIDATOR.validate_bundle_inventory(bundle_root, manifest)


if __name__ == "__main__":
    unittest.main()
