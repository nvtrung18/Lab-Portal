import importlib.util
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-source-v10.py"
VALIDATOR_PATH = ROOT / "scripts" / "validate-p7-t4-research-remediation-v10-dataset.py"
DATASET_ROOT = ROOT / "datasets" / "p7-research-synthetic-training-dataset-v10"
TARGET_CASE_IDS = ["E-FUNC-RESEARCH-006"]
REPLAY_CASE_IDS = [
    "E-AUTH-011",
    "E-AUTH-012",
    "E-FUNC-RESEARCH-004",
    "E-FUNC-RESEARCH-005",
    "E-HUMAN-003",
    "E-HUMAN-004",
    "E-INJECT-001",
    "E-INJECT-002",
    "E-INJECT-003",
    "E-ROUTE-002",
    "E-STRUCT-003",
    "E-STRUCT-004",
]


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


def load_jsonl(path: Path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]


class P7T4ResearchRemediationV10DatasetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("p7_t4_remediation_v10_source", BUILDER_PATH)
        cls.validator = load_module("p7_t4_remediation_v10_validator", VALIDATOR_PATH)

    def test_checked_in_dataset_reproduces_byte_for_byte(self):
        for relative_path, expected in self.builder.build_artifacts().items():
            self.assertEqual(expected, (ROOT / relative_path).read_bytes())

    def test_dataset_has_bounded_target_and_replay_counts(self):
        records = self.builder.build_records()

        self.assertEqual(
            {"train": 96, "validation": 20, "evaluation": 8},
            {split: len(values) for split, values in records.items()},
        )
        self.assertEqual(48, len(self.builder.targeted_records(records["train"])))
        self.assertEqual(8, len(self.builder.targeted_records(records["validation"])))
        self.assertEqual(8, len(self.builder.targeted_records(records["evaluation"])))
        self.assertEqual(48, len(self.builder.replay_records(records["train"])))
        self.assertEqual(12, len(self.builder.replay_records(records["validation"])))
        self.assertEqual([], self.builder.replay_records(records["evaluation"]))

    def test_new_e006_targets_are_vietnamese_grounded_and_useful(self):
        records = self.builder.build_records()
        for values in records.values():
            for record in self.builder.targeted_records(values):
                prompt = record["trainingPrompt"]
                target = record["trainingTarget"]
                output = target["structuredOutput"]
                combined = " ".join(
                    [target["response"]["text"], output["reviewSummary"],
                     *output["issues"], *output["suggestions"]]
                ).lower()

                self.assertEqual("VI", target["response"]["language"])
                self.assertEqual("RESEARCH_REPORT_REVIEW_DRAFT", output["kind"])
                self.assertTrue(output["advisoryOnly"])
                self.assertTrue(output["requiresHumanReview"])
                self.assertIn("giới hạn", combined)
                self.assertIn("bằng chứng", combined)
                self.assertIn("kết luận", combined)
                self.assertNotIn("số lần lặp", combined)
                self.assertEqual(prompt["referencedContextIds"], target["referencedContextIds"])
                self.assertNotEqual("E-FUNC-RESEARCH-006", target["evalCaseId"])

    def test_replay_map_protects_exactly_twelve_human_pass_cases(self):
        documents = self.builder.build_documents()
        provenance = documents[
            "datasets/p7-t4-research-remediation-source-v10/provenance.json"
        ]
        replay = provenance["replayGuard"]

        self.assertEqual(REPLAY_CASE_IDS, sorted(replay))
        for case_id in REPLAY_CASE_IDS:
            self.assertEqual(4, len(replay[case_id]["trainContentIds"]))
            self.assertEqual(1, len(replay[case_id]["validationContentIds"]))

    def test_replay_uses_only_v9_train_and_validation_records(self):
        records = self.builder.build_records()
        v9_train = {
            item["contentId"] for item in load_jsonl(
                ROOT / "datasets/p7-research-synthetic-training-dataset-v9/train.jsonl"
            )
        }
        v9_validation = {
            item["contentId"] for item in load_jsonl(
                ROOT / "datasets/p7-research-synthetic-training-dataset-v9/validation.jsonl"
            )
        }
        v9_evaluation = {
            item["contentId"] for item in load_jsonl(
                ROOT / "datasets/p7-research-synthetic-training-dataset-v9/evaluation.jsonl"
            )
        }

        train_replay = {item["contentId"] for item in self.builder.replay_records(records["train"])}
        validation_replay = {
            item["contentId"] for item in self.builder.replay_records(records["validation"])
        }
        self.assertTrue(train_replay <= v9_train)
        self.assertTrue(validation_replay <= v9_validation)
        self.assertFalse((train_replay | validation_replay) & v9_evaluation)

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
            "datasets/p7-research-synthetic-training-dataset-v10/manifest.json"
        ]
        self.assertEqual("evaluation", manifest["contractHoldout"]["split"])
        self.assertFalse(manifest["contractHoldout"]["usedForOptimization"])
        self.assertFalse(manifest["contractHoldout"]["usedForEarlyStopping"])

    def test_training_request_is_warm_start_bound_and_fail_closed(self):
        request = self.builder.build_documents()[
            "config/p7-t4-research-remediation-governance-v10/"
            "training-approval-request.json"
        ]
        scope = request["requestedScope"]

        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertFalse(request["trainingAuthorized"])
        self.assertFalse(request["externalTrainingAllowed"])
        self.assertEqual("QLORA_ADAPTER_CONTINUATION", scope["trainingMethod"])
        self.assertEqual(
            "ceab7b262b050cf9195001e4d7e5f40aa7ef67011e0e7271e62d250ffe96c717",
            scope["parentAdapterIdentity"],
        )
        self.assertEqual(0.00002, scope["learningRateMaximum"])
        self.assertEqual(48, scope["maximumSteps"])
        self.assertEqual(1, scope["earlyStoppingPatience"])
        self.assertEqual(TARGET_CASE_IDS, scope["targetedEvaluationCaseIds"])
        self.assertEqual(REPLAY_CASE_IDS, scope["replayGuardCaseIds"])

    def test_standalone_validator_reports_pending_training_approval(self):
        result = self.validator.validate_checked_in_dataset()

        self.assertEqual("VALID_PENDING_TRAINING_APPROVAL", result["state"])
        self.assertEqual({"train": 96, "validation": 20, "evaluation": 8}, result["recordCounts"])
        self.assertEqual(TARGET_CASE_IDS, result["targetedEvaluationCaseIds"])


if __name__ == "__main__":
    unittest.main()
