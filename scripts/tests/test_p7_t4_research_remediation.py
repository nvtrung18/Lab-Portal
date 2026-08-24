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
            "datasets/p7-t4-research-remediation-source-v2",
            "config/p7-t1c-research-remediation-governance-v2",
            "config/p7-t4-research-remediation-governance-v2",
            "evidence/p7-t2-real-training",
            "evidence/p7-t4-research-independent-evaluation/automatic-fail",
        ):
            shutil.copytree(ROOT / reference, copied_root / reference, dirs_exist_ok=True)
        for reference in (
            "config/p7-t2-training-pipeline-t4-remediation.json",
            "evidence/p7-t1c-research-remediation-training-governance-approval.json",
        ):
            source = ROOT / reference
            target = copied_root / reference
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
        return copied_root

    def test_checked_in_remediation_is_approved_materialized_and_allows_retraining(self):
        result = self.validator().validate_remediation(ROOT, CONFIG_PATH)

        self.assertEqual("RETRAINING_CONFIGURED", result["state"])
        self.assertEqual(
            "COMMIT_AND_BUILD_P7_T2_REMEDIATION_BUNDLE",
            result["nextAction"],
        )
        self.assertTrue(result["trainingAllowed"])
        self.assertFalse(result["promotionAllowed"])
        self.assertEqual(
            self.config()["replacementDataset"]["governanceRequestIdentity"],
            result["governanceRequestIdentity"],
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


if __name__ == "__main__":
    unittest.main()
