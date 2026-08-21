import hashlib
import importlib.util
import json
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path
from unittest.mock import Mock, patch

import yaml


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("p7t3", ROOT / "scripts" / "research-model-decision-p7-t3.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class P7T3ResearchModelDecisionTests(unittest.TestCase):
    def suite(self):
        def case(case_id, use_case, tags, mode="DRAFT_PRESENTATION"):
            return {
                "evalCaseId": case_id,
                "assistantKey": "RESEARCH_ASSISTANT",
                "useCaseId": use_case,
                "suiteTags": tags,
                "caseState": "ACTIVE",
                "responseContract": {"mode": mode, "language": "VI", "markers": []},
            }

        return {
            "suiteId": "P6-T4-EVALUATION-SUITES",
            "suiteVersion": "1.0.0",
            "EVALUATION_ONLY": True,
            "TRAINING_PROHIBITED": True,
            "caseInventory": [
                case("CASE-PROPOSAL", "RESEARCH_UC_004", ["FUNCTIONAL"]),
                case("CASE-SUGGESTION", "RESEARCH_UC_005", ["FUNCTIONAL"]),
                case("CASE-REPORT", "RESEARCH_UC_006", ["FUNCTIONAL"]),
                case("CASE-REFUSAL", "RESEARCH_UC_003", ["SAFE_REFUSAL"], mode="SAFE_REFUSAL"),
                case("CASE-STRUCTURED", "RESEARCH_UC_001", ["STRUCTURED_OUTPUT"]),
            ],
        }

    def benchmark_config(self):
        return {
            "assistantProfiles": {
                "RESEARCH_ASSISTANT": {"profile": "research", "prompt": "research-v2"},
            },
            "suite": {
                "id": "P6-T4-EVALUATION-SUITES",
                "version": "1.0.0",
                "digest": "8b75d356890a8a5c2318305589301b6ee6d73fbd3665b9af2063f98e13ea7417",
            },
            "candidates": [
                {
                    "id": "qwen3_4b",
                    "repository": "Qwen/Qwen3-4B-Instruct-2507",
                    "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
                }
            ],
        }

    def evidence(self, suite=None, default_result="PASS"):
        suite = suite or self.suite()
        return {
            "schemaVersion": "1.0.0",
            "assistantKey": "RESEARCH_ASSISTANT",
            "baseModel": {
                "identifier": "Qwen/Qwen3-4B-Instruct-2507",
                "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
            },
            "promptProfile": {"profile": "research", "promptVersion": "research-v2"},
            "evaluationSuite": {
                "id": suite["suiteId"],
                "version": suite["suiteVersion"],
                "digest": "8b75d356890a8a5c2318305589301b6ee6d73fbd3665b9af2063f98e13ea7417",
            },
            "evidenceReference": "retained/p7-t3-research-baseline-evidence.json",
            "caseResults": [
                {
                    "evalCaseId": case["evalCaseId"],
                    "result": default_result,
                    "evidenceSha256": hashlib.sha256(case["evalCaseId"].encode()).hexdigest(),
                }
                for case in suite["caseInventory"]
            ],
        }

    def decisions(self):
        return json.loads((ROOT / "config" / "p6-t6-adapter-decisions.json").read_text(encoding="utf-8"))

    def training_config(self):
        return json.loads((ROOT / "config" / "p7-t2-training-pipeline.json").read_text(encoding="utf-8"))

    def decide(self, evidence=None, **kwargs):
        return MODULE.decide_research_model(
            self.suite(),
            self.benchmark_config(),
            self.decisions(),
            self.training_config(),
            self.evidence() if evidence is None else evidence,
            frozen_evidence=[],
            source_commit="test-source-commit",
            **kwargs,
        )

    def write_approved_dataset(self, root):
        governance = yaml.safe_load(
            (ROOT / "docs" / "architecture" / "ai" / "data-governance.yml").read_text(encoding="utf-8")
        )["contract"]
        manifest = deepcopy(governance["validation_fixtures"]["approved_adapter_training_export"]["manifest"])
        dataset_directory = Path(root) / "approved-research-dataset"
        dataset_directory.mkdir()
        artifacts = []
        for split in ("train", "validation", "evaluation"):
            content = (json.dumps({"contentId": split}, sort_keys=True, separators=(",", ":")) + "\n").encode()
            filename = f"{split}.jsonl"
            (dataset_directory / filename).write_bytes(content)
            artifacts.append(
                {"filename": filename, "recordCount": 1, "sha256": hashlib.sha256(content).hexdigest()}
            )
        manifest.update(
            {
                "pipeline_schema_version": "1.0.0",
                "pipeline_version": "1.0.0",
                "checksum_algorithm": "SHA-256",
                "artifacts": artifacts,
            }
        )
        manifest["checksum"] = MODULE.P7T2.dataset_identity(manifest)
        manifest_path = dataset_directory / "manifest.json"
        manifest_path.write_text(json.dumps(manifest, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        return manifest_path, manifest

    def configured_dataset(self, manifest):
        config = self.training_config()
        config["dataset"] = {
            "manifestReference": "approved-research-dataset/manifest.json",
            "identity": manifest["checksum"],
        }
        return config

    def candidate_metadata(self, config, manifest):
        return {
            "schemaVersion": "1.0.0",
            "pipelineVersion": "1.0.0",
            "assistantKey": "RESEARCH_ASSISTANT",
            "decision": "ADAPTER_REQUIRED",
            "status": "COMPLETED",
            "backend": "REAL_QLORA",
            "qualityEvidence": "REAL_TRAINING_EXECUTION",
            "adapterDisposition": "CANDIDATE_ONLY",
            "baseModel": config["baseModel"],
            "datasetIdentity": manifest["checksum"],
            "trainingConfigIdentity": MODULE.P7T2.training_config_identity(config),
            "seed": config["seed"],
            "trainingRunIdentity": MODULE.P7T2.training_run_identity(config),
            "adapterMethod": config["adapter"]["method"],
            "checkpoints": [{"checkpointName": "checkpoint-00000002", "globalStep": 2}],
            "exportedArtifacts": [{"filename": "adapter_model.safetensors", "sha256": "1" * 64}],
            "sourceCommit": "test-source-commit",
        }

    def test_all_required_research_baseline_gates_are_represented(self):
        baseline = MODULE.evaluate_research_baseline(self.suite(), self.evidence())

        self.assertEqual(
            {
                "TASK_PROPOSAL_DRAFT",
                "TASK_SUGGESTION",
                "REPORT_REVIEW_DRAFT",
                "SAFE_REFUSAL",
                "STRUCTURED_OUTPUT",
            },
            {gate["gate"] for gate in baseline["gates"]},
        )

    def test_passing_all_required_gates_produces_base_only_approved(self):
        decision = self.decide()

        self.assertEqual("PASS", decision["overallBaselineResult"])
        self.assertEqual("BASE_ONLY_APPROVED", decision["decision"])
        self.assertEqual("BASE_ONLY_APPROVED", decision["outcome"])
        self.assertFalse(decision["training"]["invoked"])

    def test_material_required_gate_failure_produces_adapter_required(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"

        decision = self.decide(evidence=evidence)

        self.assertEqual("FAIL", decision["overallBaselineResult"])
        self.assertEqual("ADAPTER_REQUIRED", decision["decision"])

    def test_missing_evaluation_evidence_fails_closed(self):
        evidence = self.evidence()
        evidence["caseResults"].pop()

        decision = self.decide(evidence=evidence)

        self.assertEqual("UNRESOLVED", decision["overallBaselineResult"])
        self.assertEqual("ADAPTER_REQUIRED+CANDIDATE_BUILD_BLOCKED", decision["outcome"])
        self.assertEqual("BASELINE_EVIDENCE_INCOMPLETE", decision["candidateBuild"]["reason"])
        self.assertFalse(decision["training"]["invoked"])

    def test_base_only_approved_never_invokes_training(self):
        training_invoker = Mock(side_effect=AssertionError("training must not run"))

        decision = self.decide(training_invoker=training_invoker)

        training_invoker.assert_not_called()
        self.assertEqual("NOT_REQUIRED", decision["training"]["status"])

    def test_base_only_path_does_not_validate_adapter_prerequisites(self):
        config = self.training_config()
        config["assistantKey"] = "LAB_ASSISTANT"

        decision = MODULE.decide_research_model(
            self.suite(),
            self.benchmark_config(),
            {"not": "a P7-T2 decision manifest"},
            config,
            self.evidence(),
            frozen_evidence=[],
            source_commit="test-source-commit",
        )

        self.assertEqual("BASE_ONLY_APPROVED", decision["outcome"])

    def test_adapter_required_requires_approved_research_dataset(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        config = self.training_config()
        config["dataset"]["identity"] = "1" * 64

        decision = MODULE.decide_research_model(
            self.suite(),
            self.benchmark_config(),
            self.decisions(),
            config,
            evidence,
            frozen_evidence=[],
            source_commit="test-source-commit",
        )

        self.assertEqual("APPROVED_RESEARCH_DATASET_MISSING", decision["candidateBuild"]["reason"])

    def test_placeholder_dataset_identity_cannot_start_real_training(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        training_invoker = Mock(side_effect=AssertionError("placeholder must block training"))

        decision = self.decide(evidence=evidence, training_invoker=training_invoker)

        training_invoker.assert_not_called()
        self.assertEqual("PLACEHOLDER_DATASET_IDENTITY", decision["candidateBuild"]["reason"])

    def test_dataset_checksum_mismatch_fails_closed(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        with tempfile.TemporaryDirectory() as temporary_directory:
            manifest_path, manifest = self.write_approved_dataset(temporary_directory)
            config = self.configured_dataset(manifest)
            config["dataset"]["identity"] = "2" * 64
            training_invoker = Mock()

            decision = MODULE.decide_research_model(
                self.suite(),
                self.benchmark_config(),
                self.decisions(),
                config,
                evidence,
                frozen_evidence=[],
                dataset_manifest_path=manifest_path,
                training_invoker=training_invoker,
                source_commit="test-source-commit",
            )

            training_invoker.assert_not_called()
            self.assertEqual("DATASET_IDENTITY_MISMATCH", decision["candidateBuild"]["reason"])

    def test_dataset_approval_provenance_mismatch_fails_closed(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        with tempfile.TemporaryDirectory() as temporary_directory:
            manifest_path, manifest = self.write_approved_dataset(temporary_directory)
            manifest["provenance"]["type"] = "INTERNAL_SANITIZED"
            manifest["checksum"] = MODULE.P7T2.dataset_identity(manifest)
            manifest_path.write_text(json.dumps(manifest, sort_keys=True, indent=2) + "\n", encoding="utf-8")
            training_invoker = Mock()

            decision = MODULE.decide_research_model(
                self.suite(),
                self.benchmark_config(),
                self.decisions(),
                self.configured_dataset(manifest),
                evidence,
                frozen_evidence=[],
                dataset_manifest_path=manifest_path,
                training_output=Path(temporary_directory) / "training",
                training_invoker=training_invoker,
                source_commit="test-source-commit",
            )

            training_invoker.assert_not_called()
            self.assertEqual("DATASET_APPROVAL_INVALID", decision["candidateBuild"]["reason"])

    def test_research_only_scope_is_enforced(self):
        evidence = self.evidence()
        evidence["assistantKey"] = "LAB_ASSISTANT"

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "RESEARCH_ASSISTANT"):
            self.decide(evidence=evidence)

    def test_lab_or_admin_configuration_never_triggers_training(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        config = self.training_config()
        config["assistantKey"] = "LAB_ASSISTANT"
        training_invoker = Mock()

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "Research-only"):
            MODULE.decide_research_model(
                self.suite(),
                self.benchmark_config(),
                self.decisions(),
                config,
                evidence,
                frozen_evidence=[],
                training_invoker=training_invoker,
                source_commit="test-source-commit",
            )
        training_invoker.assert_not_called()

    def test_smoke_artifacts_cannot_be_accepted_as_real_candidate_evidence(self):
        metadata = self.candidate_metadata(self.training_config(), {"checksum": "0" * 64})
        metadata["backend"] = "SMOKE"
        metadata["qualityEvidence"] = "SMOKE_ONLY_NO_MODEL_QUALITY_EVIDENCE"

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "smoke"):
            MODULE.validate_real_candidate_metadata(metadata)

    def test_real_candidate_metadata_must_remain_candidate_only(self):
        metadata = self.candidate_metadata(self.training_config(), {"checksum": "0" * 64})
        MODULE.validate_real_candidate_metadata(metadata)
        metadata["adapterDisposition"] = "APPROVED"

        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "CANDIDATE_ONLY"):
            MODULE.validate_real_candidate_metadata(metadata)

    def test_identical_evidence_produces_identical_canonical_decision_identity(self):
        first = self.decide()
        second = self.decide(evidence=deepcopy(self.evidence()))

        self.assertEqual(first["decisionIdentity"], second["decisionIdentity"])
        self.assertEqual(first, second)

    def test_absolute_paths_timestamps_and_source_commit_do_not_enter_decision_identity(self):
        first = self.decide()
        second = MODULE.decide_research_model(
            self.suite(),
            self.benchmark_config(),
            self.decisions(),
            self.training_config(),
            self.evidence(),
            frozen_evidence=[],
            source_commit="different-source-commit",
        )
        self.assertEqual(first["decisionIdentity"], second["decisionIdentity"])
        self.assertNotIn(str(Path.cwd().resolve()), json.dumps(first))
        self.assertNotIn("timestamp", json.dumps(first).lower())

        evidence = self.evidence()
        evidence["evidenceReference"] = str(Path.cwd().resolve() / "evidence.json")
        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "absolute"):
            self.decide(evidence=evidence)

    def test_p7_t2_manifest_validation_is_reused_for_candidate_prerequisites(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        with tempfile.TemporaryDirectory() as temporary_directory:
            manifest_path, manifest = self.write_approved_dataset(temporary_directory)
            config = self.configured_dataset(manifest)
            with patch.object(
                MODULE.P7T2,
                "validate_dataset_manifest",
                wraps=MODULE.P7T2.validate_dataset_manifest,
            ) as validate_manifest:
                decision = MODULE.decide_research_model(
                    self.suite(),
                    self.benchmark_config(),
                    self.decisions(),
                    config,
                    evidence,
                    frozen_evidence=[],
                    dataset_manifest_path=manifest_path,
                    source_commit="test-source-commit",
                )

            validate_manifest.assert_called_once()
            self.assertEqual("TRAINING_RUNTIME_UNAVAILABLE", decision["candidateBuild"]["reason"])

    def test_p7_t2_missing_real_backend_is_not_misreported_as_smoke_evidence(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        with tempfile.TemporaryDirectory() as temporary_directory:
            manifest_path, manifest = self.write_approved_dataset(temporary_directory)
            decision = MODULE.decide_research_model(
                self.suite(),
                self.benchmark_config(),
                self.decisions(),
                self.configured_dataset(manifest),
                evidence,
                frozen_evidence=[],
                dataset_manifest_path=manifest_path,
                training_output=Path(temporary_directory) / "training-run",
                training_invoker=MODULE.P7T2.run_pipeline,
                source_commit="test-source-commit",
            )

            self.assertEqual("TRAINING_RUNTIME_UNAVAILABLE", decision["candidateBuild"]["reason"])
            self.assertEqual("BLOCKED", decision["training"]["status"])

    def test_valid_real_runner_result_is_exposed_only_as_candidate(self):
        evidence = self.evidence()
        evidence["caseResults"][0]["result"] = "FAIL"
        with tempfile.TemporaryDirectory() as temporary_directory:
            manifest_path, manifest = self.write_approved_dataset(temporary_directory)
            config = self.configured_dataset(manifest)
            metadata = self.candidate_metadata(config, manifest)
            runner = Mock(return_value=metadata)

            decision = MODULE.decide_research_model(
                self.suite(),
                self.benchmark_config(),
                self.decisions(),
                config,
                evidence,
                frozen_evidence=[],
                dataset_manifest_path=manifest_path,
                training_output=Path(temporary_directory) / "training-run",
                training_invoker=runner,
                source_commit="test-source-commit",
            )

            self.assertEqual("ADAPTER_REQUIRED+CANDIDATE_AVAILABLE", decision["outcome"])
            self.assertEqual("CANDIDATE_ONLY", decision["candidateBuild"]["metadata"]["adapterDisposition"])
            self.assertTrue(decision["training"]["invoked"])

    def test_frozen_suite_lock_requires_the_exact_suite_file_digest(self):
        suite_path = ROOT / "evals" / "p6-t4-evaluation-suites.yaml"
        suite = yaml.safe_load(suite_path.read_text(encoding="utf-8"))
        suite_lock = json.loads(
            (ROOT / "evals" / "p6-t4-evaluation-suite.lock.json").read_text(encoding="utf-8")
        )
        MODULE.validate_suite_lock(suite, suite_path, suite_lock, self.benchmark_config())

        suite_lock["suiteDigest"] = "0" * 64
        with self.assertRaisesRegex(MODULE.ResearchDecisionError, "digest"):
            MODULE.validate_suite_lock(suite, suite_path, suite_lock, self.benchmark_config())

    def test_evidence_file_identity_is_stable_across_checkout_line_endings(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            lf = Path(temporary_directory) / "lf.json"
            crlf = Path(temporary_directory) / "crlf.json"
            lf.write_bytes(b'{"evidence":true}\n')
            crlf.write_bytes(b'{"evidence":true}\r\n')

            self.assertEqual(MODULE.file_sha256(lf), MODULE.file_sha256(crlf))

    def test_checked_in_current_decision_is_valid_and_truthfully_blocked(self):
        decision_path = ROOT / "config" / "p7-t3-research-model-decision.json"
        self.assertTrue(decision_path.is_file())
        decision = json.loads(decision_path.read_text(encoding="utf-8"))

        MODULE.validate_research_decision_record(decision)
        for artifact in decision["frozenEvidence"]:
            artifact_path = ROOT / artifact["reference"]
            self.assertTrue(artifact_path.is_file())
            self.assertEqual(artifact["sha256"], MODULE.file_sha256(artifact_path))
        self.assertEqual("UNRESOLVED", decision["overallBaselineResult"])
        self.assertEqual("ADAPTER_REQUIRED+CANDIDATE_BUILD_BLOCKED", decision["outcome"])
        self.assertEqual("BASELINE_EVIDENCE_INCOMPLETE", decision["candidateBuild"]["reason"])
        self.assertFalse(decision["training"]["invoked"])
        self.assertEqual(
            {gate: "UNRESOLVED" for gate in MODULE.REQUIRED_GATES},
            {gate["gate"]: gate["result"] for gate in decision["requiredCapabilityGates"]},
        )


if __name__ == "__main__":
    unittest.main()
