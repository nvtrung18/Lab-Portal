import importlib.util
import json
import unittest
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-source-v9.py"
VALIDATOR_PATH = ROOT / "scripts" / "validate-p7-t4-research-remediation-v9-dataset.py"
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


class P7T4ResearchRemediationV9DatasetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("p7_t4_remediation_v9_source", BUILDER_PATH)
        cls.validator = load_module("p7_t4_remediation_v9_validator", VALIDATOR_PATH)
        cls.evaluator = load_module("p7_t4_evaluator_v2_for_v9_dataset", EVALUATOR_PATH)

    def test_checked_in_dataset_reproduces_byte_for_byte(self):
        for relative_path, expected in self.builder.build_artifacts().items():
            self.assertEqual(expected, (ROOT / relative_path).read_bytes())

    def test_all_v8_records_are_retained_exactly_in_their_original_splits(self):
        records = self.builder.build_records()
        for split in ("train", "validation", "evaluation"):
            retained_path = (
                ROOT
                / "datasets"
                / "p7-research-synthetic-training-dataset-v8"
                / f"{split}.jsonl"
            )
            retained = load_jsonl(retained_path)
            self.assertEqual(retained, records[split][: len(retained)])
            self.assertTrue(
                (
                    ROOT
                    / "datasets"
                    / "p7-research-synthetic-training-dataset-v9"
                    / f"{split}.jsonl"
                )
                .read_bytes()
                .startswith(retained_path.read_bytes())
            )

    def test_only_report_review_groundedness_additions_extend_v8(self):
        records = self.builder.build_records()
        retained_counts = {"train": 432, "validation": 72, "evaluation": 72}
        expected_added = {"train": 48, "validation": 8, "evaluation": 8}

        self.assertEqual(
            {"train": 480, "validation": 80, "evaluation": 80},
            {split: len(values) for split, values in records.items()},
        )
        for split, values in records.items():
            added = values[retained_counts[split] :]
            self.assertEqual(expected_added[split], len(added))
            self.assertEqual(
                {"SYNTHETIC_REPORT_REVIEW_GROUNDEDNESS_REMEDIATION"},
                {record["semanticFamily"] for record in added},
            )
            self.assertEqual(
                {"E-FUNC-RESEARCH-006"},
                {record["targetedEvaluationCaseId"] for record in added},
            )

    def test_every_new_target_is_a_grounded_report_review_draft(self):
        records = self.builder.build_records()
        retained_counts = {"train": 432, "validation": 72, "evaluation": 72}

        for split, values in records.items():
            for record in values[retained_counts[split] :]:
                prompt = record["trainingPrompt"]
                target = record["trainingTarget"]
                self.assertEqual(TARGET_FIELDS, set(target))
                output = target["structuredOutput"]
                self.assertEqual("SUCCESS", target["observedBehavior"])
                self.assertEqual("DRAFT_ONLY", target["observedActionRisk"])
                self.assertEqual({"kind": "NONE"}, target["toolRequest"])
                self.assertEqual("DRAFT_PRESENTATION", target["response"]["mode"])
                self.assertEqual(
                    ["HUMAN_REVIEW_NEEDED"],
                    target["response"]["markers"],
                )
                self.assertEqual("RESEARCH_REPORT_REVIEW_DRAFT", output["kind"])
                self.assertTrue(output["advisoryOnly"])
                self.assertTrue(output["requiresHumanReview"])
                combined = " ".join(
                    [output["reviewSummary"], *output["issues"], *output["suggestions"]]
                ).lower()
                self.assertNotIn("số lần lặp", combined)
                self.assertNotIn("repetition count", combined)
                self.assertIn("bằng chứng", combined)
                self.assertIn("giới hạn", combined)
                self.assertEqual(
                    prompt["referencedContextIds"], target["referencedContextIds"]
                )
                self.assertNotEqual("E-FUNC-RESEARCH-006", target["evalCaseId"])

    def test_targeted_train_additions_are_balanced_independent_synthetic_cases(self):
        train = self.builder.build_records()["train"][432:]

        self.assertEqual(
            {
                "SYNTHETIC_ADVISORY_REPORT_REVIEW_GROUNDEDNESS": 12,
                "SYNTHETIC_BOUNDED_OBSERVATION_NOT_REPETITION": 12,
                "SYNTHETIC_LIMITATION_AND_EVIDENCE_LINK_REVIEW": 12,
                "SYNTHETIC_UNKNOWN_VS_CONFIRMED_MISSING": 12,
            },
            Counter(record["scenarioFamily"] for record in train),
        )
        self.assertEqual(
            {"VI": 48},
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
            "datasets/p7-research-synthetic-training-dataset-v9/manifest.json"
        ]
        self.assertEqual("evaluation", manifest["contractHoldout"]["split"])
        self.assertFalse(manifest["contractHoldout"]["usedForOptimization"])

    def test_every_new_target_passes_builder_validator_and_evaluator_v2(self):
        records = self.builder.build_records()
        retained_counts = {"train": 432, "validation": 72, "evaluation": 72}
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
            "datasets/p7-research-synthetic-training-dataset-v9/manifest.json"
        ]
        request = documents[
            "config/p7-t4-research-remediation-governance-v9/"
            "training-approval-request.json"
        ]

        self.assertEqual("PENDING_TRAINING_APPROVAL", manifest["status"])
        self.assertFalse(manifest["trainingAuthorized"])
        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertFalse(request["trainingAuthorized"])
        self.assertFalse(request["externalTrainingAllowed"])
        self.assertEqual(manifest["datasetIdentity"], request["datasetIdentity"])
        self.assertEqual(
            self.builder.PREPARATION_APPROVAL_IDENTITY,
            request["preparationApprovalIdentity"],
        )
        self.assertEqual(
            self.builder.artifact_identity(request, "requestIdentity"),
            request["requestIdentity"],
        )


if __name__ == "__main__":
    unittest.main()
