import importlib.util
import json
import re
import unittest
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-source-v6.py"
VALIDATOR_PATH = ROOT / "scripts" / "validate-p7-t4-research-remediation-v6-dataset.py"
EVALUATOR_PATH = ROOT / "scripts" / "validate-p7-t4-research-evaluation-v2.py"
DATASET_DIRECTORY = ROOT / "datasets" / "p7-research-synthetic-training-dataset-v6"
CONFIG_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v6"


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T4ResearchRemediationV6DatasetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("p7_t4_remediation_v6_source", BUILDER_PATH)
        cls.validator = load_module("p7_t4_remediation_v6_validator", VALIDATOR_PATH)
        cls.evaluator = load_module("p7_t4_evaluator_v2_for_v6_dataset", EVALUATOR_PATH)

    def test_checked_in_dataset_reproduces_byte_for_byte(self):
        artifacts = self.builder.build_artifacts()

        for relative_path, expected in artifacts.items():
            self.assertEqual(expected, (ROOT / relative_path).read_bytes())

    def test_dataset_has_exact_disjoint_split_and_curriculum_counts(self):
        records = self.builder.build_records()

        self.assertEqual(
            {"evaluation": 48, "train": 288, "validation": 48},
            {split: len(values) for split, values in records.items()},
        )
        content_ids = {
            split: {record["contentId"] for record in values}
            for split, values in records.items()
        }
        self.assertFalse(content_ids["train"] & content_ids["validation"])
        self.assertFalse(content_ids["train"] & content_ids["evaluation"])
        self.assertFalse(content_ids["validation"] & content_ids["evaluation"])
        self.assertEqual(
            {
                "CANONICAL_CLOSURE_AND_EOS": 24,
                "COMPOSITIONAL_HARD_NEGATIVE": 48,
                "HISTORICAL_PASS_RETENTION": 72,
                "PERSISTENT_FAILURE_REMEDIATION": 144,
            },
            Counter(record["curriculumSegment"] for record in records["train"]),
        )
        self.assertEqual(
            {f"RESEARCH_UC_{index:03d}": 48 for index in range(1, 7)},
            Counter(record["useCaseId"] for record in records["train"]),
        )
        self.assertEqual(
            {"EN": 144, "VI": 144},
            Counter(
                record["trainingPrompt"]["responseContract"]["language"]
                for record in records["train"]
            ),
        )

    def test_train_targets_cover_tools_and_all_closed_structured_drafts(self):
        train = self.builder.build_records()["train"]

        tool_counts = Counter(record["trainingTarget"]["toolRequest"]["kind"] for record in train)
        self.assertEqual({"NONE": 96, "REJECTED": 96, "REQUEST": 96}, tool_counts)
        structured_counts = Counter(
            record["trainingTarget"]["structuredOutput"]["kind"]
            for record in train
            if record["trainingTarget"]["structuredOutput"] is not None
        )
        self.assertEqual(
            {
                "RESEARCH_REPORT_REVIEW_DRAFT": 32,
                "RESEARCH_TASK_PROPOSAL_DRAFT": 32,
                "RESEARCH_TASK_SUGGESTION_DRAFT": 32,
            },
            structured_counts,
        )
        for record in train:
            self.assertEqual(
                [], self.validator.validate_record(record), msg=record["contentId"]
            )

    def test_every_target_passes_the_independent_approved_evaluator_v2_contracts(self):
        for split, records in self.builder.build_records().items():
            for record in records:
                prompt = record["trainingPrompt"]
                target = record["trainingTarget"]
                findings = []
                findings.extend(self.evaluator.validate_tool(target["toolRequest"]))
                findings.extend(self.evaluator.validate_response(target["response"]))
                findings.extend(
                    self.evaluator.validate_output(
                        target["structuredOutput"],
                        prompt["structuredOutputContract"],
                    )
                )
                self.assertEqual(
                    [], findings, msg=f"{split}:{record['contentId']}"
                )

    def test_dataset_uses_independent_synthetic_semantics_without_frozen_case_copying(self):
        records = self.builder.build_records()
        serialized = "\n".join(
            json.dumps(record, ensure_ascii=False, sort_keys=True)
            for values in records.values()
            for record in values
        )

        for forbidden in (
            "E-AUTH-007",
            "E-FUNC-RESEARCH-002",
            "E-INJECT-001",
            "POS-RESEARCH-005",
            "project-4",
            "assigned-task-5",
        ):
            self.assertNotIn(forbidden, serialized)
        self.assertTrue(
            all(record["semanticFamily"].startswith("SYNTHETIC_") for record in records["train"])
        )

    def test_curriculum_segments_change_visible_semantics_not_only_metadata(self):
        train = self.builder.build_records()["train"]
        normalized_inputs = {
            re.sub(
                r"tra-\d{3}-\d{2}-(?:en|vi)",
                "<SAMPLE>",
                record["trainingPrompt"]["input"],
                flags=re.IGNORECASE,
            )
            for record in train
        }

        self.assertGreaterEqual(len(normalized_inputs), 48)
        for segment in (
            "HISTORICAL_PASS_RETENTION",
            "PERSISTENT_FAILURE_REMEDIATION",
            "COMPOSITIONAL_HARD_NEGATIVE",
            "CANONICAL_CLOSURE_AND_EOS",
        ):
            visible_inputs = {
                record["trainingPrompt"]["input"]
                for record in train
                if record["curriculumSegment"] == segment
            }
            self.assertTrue(visible_inputs)
            self.assertTrue(
                all(self.builder.SEGMENT_VISIBLE_MARKERS[segment] in value for value in visible_inputs)
            )

    def test_compositional_cases_contain_multiple_visible_decision_signals(self):
        compositional = [
            record
            for record in self.builder.build_records()["train"]
            if record["curriculumSegment"] == "COMPOSITIONAL_HARD_NEGATIVE"
        ]

        self.assertEqual(48, len(compositional))
        for record in compositional:
            prompt = record["trainingPrompt"]
            self.assertIn("UNTRUSTED_EMBEDDED_INSTRUCTION", prompt["input"])
            self.assertIn("VISIBLE_AUTHORIZATION_STATE", prompt["input"])
            self.assertIn("DECLARED_REFERENCE_POLICY", prompt["input"])

    def test_manifest_and_training_request_are_fail_closed_and_identity_bound(self):
        documents = self.builder.build_documents()
        manifest = documents[
            "datasets/p7-research-synthetic-training-dataset-v6/manifest.json"
        ]
        request = documents[
            "config/p7-t4-research-remediation-governance-v6/training-approval-request.json"
        ]

        self.assertEqual("PENDING_TRAINING_APPROVAL", manifest["status"])
        self.assertFalse(manifest["trainingAuthorized"])
        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertFalse(request["trainingAuthorized"])
        self.assertFalse(request["externalTrainingAllowed"])
        self.assertEqual(manifest["datasetIdentity"], request["datasetIdentity"])
        self.assertEqual(
            "5522f5f68b4f7d85c15a0a139625dc79d4b80a0bb60665480797e3485b78e91c",
            request["preparationApprovalIdentity"],
        )
        self.assertEqual(
            self.builder.artifact_identity(request, "requestIdentity"),
            request["requestIdentity"],
        )


if __name__ == "__main__":
    unittest.main()
