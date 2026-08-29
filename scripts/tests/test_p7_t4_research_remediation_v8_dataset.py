import importlib.util
import json
import unittest
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-source-v8.py"
VALIDATOR_PATH = ROOT / "scripts" / "validate-p7-t4-research-remediation-v8-dataset.py"
EVALUATOR_PATH = ROOT / "scripts" / "validate-p7-t4-research-evaluation-v2.py"
TARGET_FIELDS = {
    "evalCaseId",
    "observedActionRisk",
    "observedBehavior",
    "referencedContextIds",
    "response",
    "structuredOutput",
    "toolRequest",
}


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


def load_jsonl(path: Path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]


class P7T4ResearchRemediationV8DatasetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("p7_t4_remediation_v8_source", BUILDER_PATH)
        cls.validator = load_module("p7_t4_remediation_v8_validator", VALIDATOR_PATH)
        cls.evaluator = load_module("p7_t4_evaluator_v2_for_v8_dataset", EVALUATOR_PATH)

    def test_checked_in_dataset_reproduces_byte_for_byte(self):
        for relative_path, expected in self.builder.build_artifacts().items():
            self.assertEqual(expected, (ROOT / relative_path).read_bytes())

    def test_all_v7_records_are_retained_exactly_in_their_original_splits(self):
        records = self.builder.build_records()
        for split in ("train", "validation", "evaluation"):
            retained_path = (
                ROOT
                / "datasets"
                / "p7-research-synthetic-training-dataset-v7"
                / f"{split}.jsonl"
            )
            retained = load_jsonl(retained_path)
            self.assertEqual(retained, records[split][: len(retained)])
            self.assertTrue(
                (
                    ROOT
                    / "datasets"
                    / "p7-research-synthetic-training-dataset-v8"
                    / f"{split}.jsonl"
                )
                .read_bytes()
                .startswith(retained_path.read_bytes())
            )

    def test_only_e_inject_001_quality_additions_extend_v7(self):
        records = self.builder.build_records()
        retained_counts = {"train": 384, "validation": 64, "evaluation": 64}
        expected_added = {"train": 48, "validation": 8, "evaluation": 8}

        self.assertEqual(
            {"train": 432, "validation": 72, "evaluation": 72},
            {split: len(values) for split, values in records.items()},
        )
        for split, values in records.items():
            added = values[retained_counts[split] :]
            self.assertEqual(expected_added[split], len(added))
            self.assertEqual(
                {"SYNTHETIC_PROMPT_INJECTION_CLOSED_ENVELOPE_REMEDIATION"},
                {record["semanticFamily"] for record in added},
            )
            self.assertEqual(
                {"E-INJECT-001"},
                {record["targetedEvaluationCaseId"] for record in added},
            )

    def test_every_new_target_is_a_complete_compact_safe_refusal_envelope(self):
        records = self.builder.build_records()
        retained_counts = {"train": 384, "validation": 64, "evaluation": 64}

        for split, values in records.items():
            for record in values[retained_counts[split] :]:
                prompt = record["trainingPrompt"]
                target = record["trainingTarget"]
                self.assertEqual(TARGET_FIELDS, set(target))
                self.assertIsNone(target["structuredOutput"])
                self.assertEqual("SAFE_REFUSAL", target["observedBehavior"])
                self.assertEqual("PROHIBITED", target["observedActionRisk"])
                self.assertEqual([], target["referencedContextIds"])
                self.assertEqual(prompt["rejectedTool"], target["toolRequest"])
                self.assertEqual("REJECTED", target["toolRequest"]["kind"])
                self.assertEqual("PROHIBITED", target["toolRequest"]["reason"])
                self.assertEqual("SAFE_REFUSAL", target["response"]["mode"])
                self.assertEqual(
                    ["NO_DISCLOSURE", "NO_EXECUTION"],
                    target["response"]["markers"],
                )
                self.assertLessEqual(len(target["response"]["text"]), 180)
                self.assertIn("EXACT_SEVEN_ROOT_FIELDS", prompt["input"])
                self.assertNotEqual("E-INJECT-001", target["evalCaseId"])

    def test_targeted_train_additions_are_balanced_independent_synthetic_cases(self):
        train = self.builder.build_records()["train"][384:]

        self.assertEqual(
            {
                "SYNTHETIC_NULL_CONTEXT_PROMPT_INJECTION": 12,
                "SYNTHETIC_SECRET_DISCLOSURE_REQUEST": 12,
                "SYNTHETIC_UNAUTHORIZED_EXECUTION_REQUEST": 12,
                "SYNTHETIC_UNTRUSTED_ROLE_OVERRIDE": 12,
            },
            Counter(record["scenarioFamily"] for record in train),
        )
        self.assertEqual(
            {"EN": 24, "VI": 24},
            Counter(
                record["trainingPrompt"]["responseContract"]["language"]
                for record in train
            ),
        )
        self.assertEqual(48, len({record["contentId"] for record in train}))

    def test_content_ids_are_disjoint_and_holdout_is_not_optimization_data(self):
        records = self.builder.build_records()
        content_ids = {
            split: {record["contentId"] for record in values}
            for split, values in records.items()
        }
        self.assertTrue(all(len(content_ids[s]) == len(records[s]) for s in records))
        self.assertFalse(content_ids["train"] & content_ids["validation"])
        self.assertFalse(content_ids["train"] & content_ids["evaluation"])
        self.assertFalse(content_ids["validation"] & content_ids["evaluation"])

        manifest = self.builder.build_documents()[
            "datasets/p7-research-synthetic-training-dataset-v8/manifest.json"
        ]
        self.assertEqual("evaluation", manifest["contractHoldout"]["split"])
        self.assertFalse(manifest["contractHoldout"]["usedForOptimization"])

    def test_every_new_target_passes_builder_validator_and_evaluator_v2(self):
        records = self.builder.build_records()
        retained_counts = {"train": 384, "validation": 64, "evaluation": 64}
        for split, values in records.items():
            for record in values[retained_counts[split] :]:
                prompt = record["trainingPrompt"]
                target = record["trainingTarget"]
                self.assertEqual([], self.validator.validate_record(record))
                findings = []
                findings.extend(self.evaluator.validate_tool(target["toolRequest"]))
                findings.extend(self.evaluator.validate_response(target["response"]))
                findings.extend(
                    self.evaluator.validate_output(
                        target["structuredOutput"], prompt["structuredOutputContract"]
                    )
                )
                self.assertEqual([], findings, msg=f"{split}:{record['contentId']}")

    def test_manifest_and_training_request_remain_fail_closed(self):
        documents = self.builder.build_documents()
        manifest = documents[
            "datasets/p7-research-synthetic-training-dataset-v8/manifest.json"
        ]
        request = documents[
            "config/p7-t4-research-remediation-governance-v8/"
            "training-approval-request.json"
        ]

        self.assertEqual("PENDING_TRAINING_APPROVAL", manifest["status"])
        self.assertFalse(manifest["trainingAuthorized"])
        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertFalse(request["trainingAuthorized"])
        self.assertFalse(request["externalTrainingAllowed"])
        self.assertEqual(manifest["datasetIdentity"], request["datasetIdentity"])
        self.assertEqual(
            "482525b6de1fa7dcea165d522bf8745d7e18b57aa12303fed46832969cc0d82c",
            request["preparationApprovalIdentity"],
        )
        self.assertEqual(
            self.builder.artifact_identity(request, "requestIdentity"),
            request["requestIdentity"],
        )


if __name__ == "__main__":
    unittest.main()
