import copy
import hashlib
import importlib.util
import json
import shutil
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
VALIDATOR_PATH = ROOT / "scripts" / "validate-p7-t4-research-remediation.py"
CONFIG_PATH = ROOT / "config" / "p7-t4-research-remediation.json"


class P7T4ResearchRemediationTests(unittest.TestCase):
    def validator(self):
        self.assertTrue(VALIDATOR_PATH.is_file())
        specification = importlib.util.spec_from_file_location(
            "p7_t4_research_remediation_validator", VALIDATOR_PATH
        )
        module = importlib.util.module_from_spec(specification)
        assert specification.loader is not None
        specification.loader.exec_module(module)
        return module

    def config(self):
        self.assertTrue(CONFIG_PATH.is_file())
        return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))

    def copied_validation_root(self, validator, destination: Path) -> Path:
        copied_root = destination / "repo"
        file_references = {
            *validator.EVALUATION_FREEZE_REFERENCES,
            validator.CURRENT_TRAINING_CONFIG_REFERENCE,
            validator.REMEDIATION_EVALUATION_CONFIG_REFERENCE,
            validator.REMEDIATION_REAL_TRAINING_REFERENCE,
            validator.REMEDIATION_TRAINING_PIPELINE_REFERENCE,
            validator.SERVING_PROFILE_REFERENCE,
            validator.SERVING_SCHEMA_REFERENCE,
        }
        for reference in sorted(file_references):
            source = ROOT / reference
            target = copied_root / reference
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
        for reference in (
            "datasets/p7-research-synthetic-training-dataset-v1",
            "datasets/p7-research-synthetic-training-dataset-v2",
            "datasets/p7-research-synthetic-training-dataset-v3",
            "datasets/p7-t4-research-remediation-source-v2",
            "datasets/p7-t4-research-remediation-source-v3",
            "config/p7-t1c-research-remediation-governance-v2",
            "config/p7-t1c-research-remediation-governance-v3",
            "config/p7-t4-research-remediation-governance-v2",
            "config/p7-t4-research-remediation-governance-v3",
            "evidence/p7-t2-real-training",
            "evidence/p7-t4-research-independent-evaluation/automatic-fail",
            "evidence/p7-t4-research-independent-evaluation/automatic-fail-remediation-v2",
        ):
            shutil.copytree(ROOT / reference, copied_root / reference, dirs_exist_ok=True)
        for reference in (
            "config/p7-t2-training-pipeline-t4-remediation.json",
            "config/p7-t2-training-pipeline-t4-remediation-v3.json",
            "evidence/p7-t1c-research-remediation-training-governance-approval.json",
            "evidence/p7-t1c-research-remediation-v3-training-governance-approval.json",
            "scripts/training-pipeline-p7-t2-remediation-v3.py",
        ):
            source = ROOT / reference
            target = copied_root / reference
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
        return copied_root

    def test_checked_in_remediation_records_v3_ready_after_second_failure(self):
        result = self.validator().validate_remediation(ROOT, CONFIG_PATH)

        self.assertEqual(
            "REMEDIATION_V3_READY_FOR_EXTERNAL_REAL_TRAINING",
            result["state"],
        )
        self.assertEqual(
            "COMMIT_AND_BUILD_P7_T2_REMEDIATION_V3_BUNDLE",
            result["nextAction"],
        )
        self.assertTrue(result["trainingAllowed"])
        self.assertFalse(result["promotionAllowed"])
        self.assertEqual(
            self.config()["replacementV3"]["governanceRequestIdentity"],
            result["governanceRequestIdentity"],
        )

    def test_v3_readiness_binds_approved_materialized_dataset_and_training_config(self):
        config = self.config()
        replacement = config["replacementV3"]

        self.assertEqual("READY_FOR_EXTERNAL_REAL_TRAINING", replacement["state"])
        self.assertEqual(
            "6b98270d32015aaf1f8f04aa43089a18128baf5fd55a785f675f0d56698851d1",
            replacement["governanceRequestIdentity"],
        )
        self.assertEqual(
            "a1f92aec9caca9b053daf780c1bfde951abdb88e2fe3e92f4f2545c676d45015",
            replacement["trainingApprovalIdentity"],
        )
        self.assertEqual(
            "430390b22936bdea27c7e5b4022795ef483b55ac21f84e3e52cc663b9aaf9d10",
            replacement["datasetIdentity"],
        )
        self.assertEqual(270, replacement["sourceRecordCount"])
        self.assertIsNone(replacement["candidateId"])

    def test_completed_training_binds_a_new_failed_candidate_without_changing_first_failure(self):
        config = self.config()

        self.assertEqual(
            "REAL_TRAINING_COMPLETE",
            config["replacementTraining"]["state"],
        )
        self.assertEqual(
            "445a2c33e7cf7a7b9dc8b69c3ebe01ab0d7cf2565463ffb3d30920d9509baf61",
            config["replacementTraining"]["candidateId"],
        )
        self.assertNotEqual(
            config["failedCandidate"]["candidateId"],
            config["replacementTraining"]["candidateId"],
        )
        self.assertEqual(
            "AUTOMATIC_FAIL",
            config["reevaluation"]["state"],
        )
        self.assertEqual(
            config["replacementTraining"]["candidateId"],
            config["reevaluationResult"]["candidateId"],
        )
        self.assertEqual(
            "aecbab9a601b20716821bd1ec8454924ff23b6088a35e7e1e21e4e7d654982f2",
            config["reevaluationResult"]["comparisonIdentity"],
        )
        self.assertEqual(
            "CANDIDATE_NOT_PROMOTABLE_AUTOMATIC_FAIL",
            config["reevaluationResult"]["disposition"],
        )

    def test_remediation_identity_and_failure_diagnostics_are_reproducible(self):
        validator = self.validator()
        config = self.config()

        self.assertEqual(config["artifactIdentity"], validator.artifact_identity(config))
        diagnostics = validator.diagnose_current_failure(
            ROOT, config["evaluationFreeze"]
        )
        self.assertEqual(39, diagnostics["supervisedTargets"]["recordCount"])
        self.assertEqual(0, diagnostics["supervisedTargets"]["p7T4CompatibleCount"])
        self.assertEqual(
            {
                "RAW_OUTPUT_CASE_ID_MISMATCH": 10,
                "RAW_OUTPUT_ENVELOPE_INVALID": 6,
                "RAW_OUTPUT_PARSE_FAILURE": 2,
            },
            diagnostics["adapterParseErrorsPerRepetition"]["R01"],
        )
        self.assertEqual(1000, diagnostics["trainingSchedule"]["globalSteps"])
        self.assertGreater(diagnostics["trainingSchedule"]["epochs"], 300)
        self.assertGreater(
            diagnostics["trainingSchedule"]["validationLoss"],
            diagnostics["trainingSchedule"]["trainLoss"],
        )

    def test_replacement_design_targets_current_gate_without_losing_prepared_runtime_contract(self):
        design = self.config()["replacementDataset"]["requiredContractDesign"]

        self.assertEqual("PROMPT_SCOPED_DUAL_CONTRACT", design["design"])
        self.assertFalse(design["frozenEvaluationDerivedRecordsAllowed"])
        self.assertEqual(
            [
                "evalCaseId",
                "observedActionRisk",
                "observedBehavior",
                "referencedContextIds",
                "response",
                "structuredOutput",
                "toolRequest",
            ],
            design["benchmarkCompatibility"]["closedKeys"],
        )
        self.assertEqual(
            "PREPARED_NOT_ACTIVE",
            design["preparedRuntimeContract"]["activationState"],
        )
        self.assertEqual(
            "research-assistant-output-v1",
            design["preparedRuntimeContract"]["schemaBundle"],
        )

    def test_remediation_cannot_claim_pass_or_reuse_frozen_evaluation_for_training(self):
        validator = self.validator()
        config = self.config()

        false_pass = copy.deepcopy(config)
        false_pass["state"] = "PASS"
        with self.assertRaisesRegex(validator.RemediationValidationError, "state"):
            validator.validate_document(ROOT, false_pass)

        leaked_training = copy.deepcopy(config)
        leaked_training["remediationPolicy"]["frozenEvaluationTrainingUseAllowed"] = True
        with self.assertRaisesRegex(
            validator.RemediationValidationError, "frozen evaluation"
        ):
            validator.validate_document(ROOT, leaked_training)

    def test_remediation_is_bound_to_the_failed_candidate_and_requires_fresh_approval(self):
        validator = self.validator()
        config = self.config()

        wrong_candidate = copy.deepcopy(config)
        wrong_candidate["failedCandidate"]["candidateId"] = "0" * 64
        with self.assertRaisesRegex(validator.RemediationValidationError, "candidate"):
            validator.validate_document(ROOT, wrong_candidate)

        fake_approval = copy.deepcopy(config)
        fake_approval["replacementDataset"]["trainingApprovalIdentity"] = "0" * 64
        with self.assertRaisesRegex(
            validator.RemediationValidationError, "replacementDataset"
        ):
            validator.validate_document(ROOT, fake_approval)

    def test_validator_rejects_tampered_dataset_and_run_evidence(self):
        validator = self.validator()
        config = self.config()

        with tempfile.TemporaryDirectory() as directory:
            copied_root = self.copied_validation_root(validator, Path(directory))
            train = (
                copied_root
                / "datasets/p7-research-synthetic-training-dataset-v1/train.jsonl"
            )
            train.write_text(train.read_text(encoding="utf-8") + "\n", encoding="utf-8")
            with self.assertRaisesRegex(
                validator.RemediationValidationError, "training dataset"
            ):
                validator.validate_document(copied_root, config)

        with tempfile.TemporaryDirectory() as directory:
            copied_root = self.copied_validation_root(validator, Path(directory))
            run_path = (
                copied_root
                / "evidence/p7-t4-research-independent-evaluation/automatic-fail/runs/RESEARCH_ADAPTER/R01.json"
            )
            run = json.loads(run_path.read_text(encoding="utf-8"))
            run["rawOutputs"][0]["rawText"] += " "
            run_path.write_text(
                json.dumps(run, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(
                validator.RemediationValidationError, "run evidence"
            ):
                validator.validate_document(copied_root, config)

    def test_validator_rejects_evaluator_source_drift(self):
        validator = self.validator()
        config = self.config()

        with tempfile.TemporaryDirectory() as directory:
            copied_root = self.copied_validation_root(validator, Path(directory))
            prompt = copied_root / "config/p6-t5-benchmark-kaggle-linux-cp312.yaml"
            prompt.write_text(
                prompt.read_text(encoding="utf-8") + "\n# unauthorized drift\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(
                validator.RemediationValidationError, "source inventory drift"
            ):
                validator.validate_document(copied_root, config)

    def test_validator_rejects_tampered_remediation_reevaluation_run(self):
        validator = self.validator()
        config = self.config()

        with tempfile.TemporaryDirectory() as directory:
            copied_root = self.copied_validation_root(validator, Path(directory))
            run_path = (
                copied_root
                / "evidence/p7-t4-research-independent-evaluation"
                / "automatic-fail-remediation-v2/runs/RESEARCH_ADAPTER/R01.json"
            )
            run = json.loads(run_path.read_text(encoding="utf-8"))
            run["rawOutputs"][0]["rawText"] += " "
            run_path.write_text(
                json.dumps(run, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                validator.RemediationValidationError,
                "reevaluationResult",
            ):
                validator.validate_document(copied_root, config)

    def test_validator_rejects_tampered_v3_training_dataset(self):
        validator = self.validator()
        config = self.config()

        with tempfile.TemporaryDirectory() as directory:
            copied_root = self.copied_validation_root(validator, Path(directory))
            train_path = (
                copied_root
                / "datasets/p7-research-synthetic-training-dataset-v3/train.jsonl"
            )
            train_path.write_bytes(train_path.read_bytes() + b"\n")

            with self.assertRaisesRegex(
                validator.RemediationValidationError,
                "replacementV3",
            ):
                validator.validate_document(copied_root, config)

    def test_validator_rejects_coordinated_training_metadata_rewrite(self):
        validator = self.validator()
        config = self.config()

        with tempfile.TemporaryDirectory() as directory:
            copied_root = self.copied_validation_root(validator, Path(directory))
            metadata_path = (
                copied_root / "evidence/p7-t2-real-training/training-metadata.json"
            )
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
            metadata["metrics"]["trainLoss"] = 0.0001
            metadata_path.write_text(
                json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )
            rewritten = copy.deepcopy(config)
            rewritten["failedCandidate"]["trainingMetadataSha256"] = hashlib.sha256(
                metadata_path.read_bytes()
            ).hexdigest()
            rewritten["rootCause"]["trainingSchedule"]["trainLoss"] = 0.0001
            rewritten["artifactIdentity"] = validator.artifact_identity(rewritten)

            with self.assertRaisesRegex(
                validator.RemediationValidationError, "training metadata mismatch"
            ):
                validator.validate_document(copied_root, rewritten)

    def test_validator_rejects_tampered_completed_remediation_evidence(self):
        validator = self.validator()
        config = self.config()

        with tempfile.TemporaryDirectory() as directory:
            copied_root = self.copied_validation_root(validator, Path(directory))
            metadata_path = copied_root / validator.REMEDIATION_TRAINING_METADATA_REFERENCE
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
            metadata["metrics"]["validationLoss"] = 0.0
            metadata_path.write_text(
                json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                validator.RemediationValidationError,
                "replacementTraining",
            ):
                validator.validate_document(copied_root, config)


if __name__ == "__main__":
    unittest.main()
