import importlib.util
import json
import unittest
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-source-v7.py"
VALIDATOR_PATH = ROOT / "scripts" / "validate-p7-t4-research-remediation-v7-dataset.py"
EVALUATOR_PATH = ROOT / "scripts" / "validate-p7-t4-research-evaluation-v2.py"


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


def load_jsonl(path: Path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]


class P7T4ResearchRemediationV7DatasetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("p7_t4_remediation_v7_source", BUILDER_PATH)
        cls.validator = load_module("p7_t4_remediation_v7_validator", VALIDATOR_PATH)
        cls.evaluator = load_module("p7_t4_evaluator_v2_for_v7_dataset", EVALUATOR_PATH)

    def test_checked_in_dataset_reproduces_byte_for_byte(self):
        for relative_path, expected in self.builder.build_artifacts().items():
            self.assertEqual(expected, (ROOT / relative_path).read_bytes())

    def test_v6_records_are_retained_exactly_in_their_original_splits(self):
        records = self.builder.build_records()
        for split in ("train", "validation", "evaluation"):
            retained = load_jsonl(
                ROOT / "datasets" / "p7-research-synthetic-training-dataset-v6" / f"{split}.jsonl"
            )
            self.assertEqual(retained, records[split][: len(retained)])
            self.assertEqual(
                [record["contentId"] for record in retained],
                [record["contentId"] for record in records[split][: len(retained)]],
            )

    def test_only_targeted_additions_extend_the_retained_dataset(self):
        records = self.builder.build_records()
        retained_counts = {"train": 288, "validation": 48, "evaluation": 48}
        expected_added = {"train": 96, "validation": 16, "evaluation": 16}

        self.assertEqual(
            {"train": 384, "validation": 64, "evaluation": 64},
            {split: len(values) for split, values in records.items()},
        )
        for split, values in records.items():
            added = values[retained_counts[split] :]
            self.assertEqual(expected_added[split], len(added))
            self.assertEqual(
                {"RESEARCH_UC_004", "RESEARCH_UC_005"},
                {record["useCaseId"] for record in added},
            )
            self.assertEqual(
                {"TARGETED_OBJECT_REFERENCE_EXTRACTION"},
                {record["semanticFamily"] for record in added},
            )

    def test_targeted_context_objects_map_to_scalar_resource_ids(self):
        records = self.builder.build_records()
        retained_counts = {"train": 288, "validation": 48, "evaluation": 48}
        for split, values in records.items():
            for record in values[retained_counts[split] :]:
                prompt = record["trainingPrompt"]
                target = record["trainingTarget"]
                context_input = prompt["authorizedContext"]["input"]
                output = target["structuredOutput"]

                if record["useCaseId"] == "RESEARCH_UC_004":
                    self.assertIsInstance(context_input["projectRef"], dict)
                    self.assertIsInstance(context_input["groupRef"], dict)
                    self.assertEqual(
                        context_input["projectRef"]["resourceId"], output["projectRef"]
                    )
                    self.assertEqual(
                        context_input["groupRef"]["resourceId"], output["groupRef"]
                    )
                    self.assertEqual(
                        {
                            "groupRef",
                            "kind",
                            "projectRef",
                            "requiresHumanReview",
                            "taskTitle",
                        },
                        set(output),
                    )
                    self.assertTrue(output["taskTitle"].strip())
                else:
                    self.assertIsInstance(context_input["taskRef"], dict)
                    self.assertEqual(
                        context_input["taskRef"]["resourceId"], output["taskRef"]
                    )
                    self.assertEqual(
                        {"kind", "requiresHumanReview", "suggestion", "taskRef"},
                        set(output),
                    )
                    self.assertTrue(output["suggestion"].strip())

                for key, value in output.items():
                    if key.endswith("Ref"):
                        self.assertIsInstance(value, str)

    def test_targeted_train_additions_are_balanced_without_changing_safety_families(self):
        train = self.builder.build_records()["train"][288:]

        self.assertEqual(
            {"RESEARCH_UC_004": 48, "RESEARCH_UC_005": 48},
            Counter(record["useCaseId"] for record in train),
        )
        self.assertEqual(
            {"EN": 48, "VI": 48},
            Counter(
                record["trainingPrompt"]["responseContract"]["language"]
                for record in train
            ),
        )
        self.assertEqual(
            {"NONE": 48, "REQUEST": 48},
            Counter(record["trainingTarget"]["toolRequest"]["kind"] for record in train),
        )
        self.assertTrue(
            all(
                "OBJECT_REFERENCE_INPUT" in record["trainingPrompt"]["input"]
                for record in train
            )
        )

    def test_content_ids_are_disjoint_and_holdout_is_not_optimization_data(self):
        records = self.builder.build_records()
        content_ids = {
            split: {record["contentId"] for record in values}
            for split, values in records.items()
        }
        self.assertEqual(len(records["train"]), len(content_ids["train"]))
        self.assertEqual(len(records["validation"]), len(content_ids["validation"]))
        self.assertEqual(len(records["evaluation"]), len(content_ids["evaluation"]))
        self.assertFalse(content_ids["train"] & content_ids["validation"])
        self.assertFalse(content_ids["train"] & content_ids["evaluation"])
        self.assertFalse(content_ids["validation"] & content_ids["evaluation"])

        manifest = self.builder.build_documents()[
            "datasets/p7-research-synthetic-training-dataset-v7/manifest.json"
        ]
        self.assertEqual("evaluation", manifest["contractHoldout"]["split"])
        self.assertFalse(manifest["contractHoldout"]["usedForOptimization"])

    def test_every_new_target_passes_builder_validator_and_evaluator_v2(self):
        records = self.builder.build_records()
        retained_counts = {"train": 288, "validation": 48, "evaluation": 48}
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
            "datasets/p7-research-synthetic-training-dataset-v7/manifest.json"
        ]
        request = documents[
            "config/p7-t4-research-remediation-governance-v7/training-approval-request.json"
        ]

        self.assertEqual("PENDING_TRAINING_APPROVAL", manifest["status"])
        self.assertFalse(manifest["trainingAuthorized"])
        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertFalse(request["trainingAuthorized"])
        self.assertFalse(request["externalTrainingAllowed"])
        self.assertEqual(manifest["datasetIdentity"], request["datasetIdentity"])
        self.assertEqual(
            "5bd1863605ea5b929c832864bcff168afae91eee3643a44516fe8301e68e54b5",
            request["preparationApprovalIdentity"],
        )
        self.assertEqual(
            self.builder.artifact_identity(request, "requestIdentity"),
            request["requestIdentity"],
        )


if __name__ == "__main__":
    unittest.main()
