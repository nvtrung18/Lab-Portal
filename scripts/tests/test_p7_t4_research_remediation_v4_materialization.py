import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import yaml


ROOT = Path(__file__).resolve().parents[2]
FINALIZER_PATH = (
    ROOT / "scripts" / "finalize-p7-t4-research-remediation-v4-governance.py"
)
CONFIG_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v4"
APPROVAL_PATH = (
    ROOT / "evidence" / "p7-t4-research-remediation-v4-governance-approval.json"
)
SOURCE_BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-source-v4.py"
SOURCE_DIRECTORY = ROOT / "datasets" / "p7-t4-research-remediation-source-v4"
DATASET_DIRECTORY = ROOT / "datasets" / "p7-research-synthetic-training-dataset-v4"
VALIDATOR_PATH = ROOT / "scripts" / "validate-p7-t4-research-remediation-v4-dataset.py"


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T4ResearchRemediationV4GovernanceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.finalizer = load_module("p7_t4_v4_governance", FINALIZER_PATH)

    def test_checked_in_governance_artifacts_reproduce_byte_for_byte(self):
        artifacts = self.finalizer.build_artifacts()

        self.assertEqual(
            {
                "config/p7-t4-research-remediation-governance-v4/data-governance-v2.approved.yml",
                "config/p7-t4-research-remediation-governance-v4/structured-output-schema.approved.json",
                "evidence/p7-t4-research-remediation-v4-governance-approval.json",
            },
            set(artifacts),
        )
        for relative_path, expected in artifacts.items():
            self.assertEqual(expected, (ROOT / relative_path).read_bytes())

    def test_approval_is_exactly_scoped_to_dataset_preparation(self):
        approval = json.loads(APPROVAL_PATH.read_text(encoding="utf-8"))

        self.assertEqual(
            "acb5c41075b2808c84b98f3179c68cc3519e261c6a8cb9bf06ee8669d2c74ac7",
            approval["requestIdentity"],
        )
        self.assertEqual("APPROVED", approval["approval"]["decision"])
        self.assertEqual("2026-08-26", approval["approval"]["approvedAt"])
        self.assertTrue(approval["authorization"]["datasetPreparationAllowed"])
        self.assertFalse(approval["authorization"]["externalTrainingAllowed"])
        self.assertFalse(approval["authorization"]["evaluationAllowed"])
        self.assertFalse(approval["authorization"]["promotionAllowed"])
        self.assertFalse(approval["authorization"]["runtimeSchemaActivationAllowed"])

    def test_governance_v2_preserves_real_reports_and_adds_only_synthetic_review(self):
        document = yaml.safe_load(
            (CONFIG_DIRECTORY / "data-governance-v2.approved.yml").read_text(
                encoding="utf-8"
            )
        )
        contract = document["contract"]
        additions = {
            item["category_id"]: item
            for item in contract["data_governance_matrix_additions"]
        }

        self.assertEqual("2.0.0", contract["contract_version"])
        preserved = contract["preserved_category_assertions"][0]
        self.assertEqual("CAT_RESEARCH_REPORT_METADATA", preserved["category_id"])
        self.assertEqual("DEFERRED", preserved["use_decision"])
        self.assertIn(
            "TRAINING",
            preserved["prohibited_purposes"],
        )
        synthetic = additions["CAT_RESEARCH_REPORT_REVIEW_SYNTHETIC"]
        self.assertEqual("SYNTHETIC_ONLY", synthetic["use_decision"])
        self.assertEqual(["TRAINING", "DEVELOPMENT_TEST"], synthetic["permitted_purposes"])
        self.assertEqual(["RESEARCH_UC_006"], synthetic["use_case_ids"])

    def test_schema_is_approved_for_preparation_but_not_runtime_activation(self):
        document = json.loads(
            (CONFIG_DIRECTORY / "structured-output-schema.approved.json").read_text(
                encoding="utf-8"
            )
        )
        variants = document["schemas"][0]["schema"]["oneOf"]
        kinds = {
            item["properties"]["kind"]["const"]
            for item in variants
        }

        self.assertEqual("APPROVED_FOR_DATASET_PREPARATION", document["status"])
        self.assertFalse(document["runtimeActivationAllowed"])
        self.assertIn("RESEARCH_REPORT_REVIEW_DRAFT", kinds)


class P7T4ResearchRemediationV4DatasetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("p7_t4_v4_source", SOURCE_BUILDER_PATH)
        cls.source = json.loads(
            (SOURCE_DIRECTORY / "source-export.json").read_text(encoding="utf-8")
        )

    def test_checked_in_source_and_dataset_reproduce_byte_for_byte(self):
        for relative_path, expected in self.builder.build_artifacts().items():
            self.assertEqual(expected, (ROOT / relative_path).read_bytes())

    def test_builder_rejects_authority_tampering_that_preserves_old_identity(self):
        authority_paths = (
            self.builder.APPROVAL_REFERENCE,
            self.builder.SCHEMA_REFERENCE,
            self.builder.QUALITY_REFERENCE,
            self.builder.GOVERNANCE_REFERENCE,
        )
        for tampered_reference in (
            self.builder.APPROVAL_REFERENCE,
            self.builder.SCHEMA_REFERENCE,
        ):
            with self.subTest(tampered_reference=tampered_reference), tempfile.TemporaryDirectory() as name:
                artifact_root = Path(name)
                for relative_path in authority_paths:
                    target = artifact_root / relative_path
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes((ROOT / relative_path).read_bytes())
                tampered_path = artifact_root / tampered_reference
                tampered = json.loads(tampered_path.read_text(encoding="utf-8"))
                tampered["tampered"] = True
                tampered_path.write_text(
                    json.dumps(tampered, ensure_ascii=False, sort_keys=True, indent=2) + "\n",
                    encoding="utf-8",
                    newline="\n",
                )

                with mock.patch.object(self.builder, "ROOT", artifact_root), self.assertRaises(
                    self.builder.SourceBuildError
                ):
                    self.builder._authorities()

    def test_source_has_exact_quality_matrix_and_language_balance(self):
        records = self.source["records"]
        by_use_case = {}
        by_language = {}
        scenarios = {}
        for record in records:
            use_case = record["useCaseId"]
            language = record["metadata"]["language"]
            scenario = record["metadata"]["scenario"]
            by_use_case[use_case] = by_use_case.get(use_case, 0) + 1
            by_language[language] = by_language.get(language, 0) + 1
            scenarios.setdefault(use_case, {})[scenario] = (
                scenarios.setdefault(use_case, {}).get(scenario, 0) + 1
            )

        self.assertEqual(144, len(records))
        self.assertEqual(
            {
                "RESEARCH_UC_003": 36,
                "RESEARCH_UC_004": 36,
                "RESEARCH_UC_005": 36,
                "RESEARCH_UC_006": 36,
            },
            by_use_case,
        )
        self.assertEqual({"EN": 72, "VI": 72}, by_language)
        expected_scenarios = {
            "authorizedNoTool": 8,
            "authorizedDeclaredTool": 8,
            "authorizationRejected": 6,
            "authorizationNoTool": 2,
            "promptInjectionRejected": 4,
            "unsupportedRouteRejected": 4,
            "nullContext": 4,
        }
        self.assertTrue(all(value == expected_scenarios for value in scenarios.values()))

    def test_report_review_uses_only_synthetic_category_and_closed_schema(self):
        records = [
            record
            for record in self.source["records"]
            if record["useCaseId"] == "RESEARCH_UC_006"
        ]
        successful = [
            record
            for record in records
            if record["metadata"]["scenario"]
            in {"authorizedNoTool", "authorizedDeclaredTool"}
        ]

        self.assertTrue(records)
        for record in records:
            self.assertEqual(
                ["CAT_RESEARCH_REPORT_REVIEW_SYNTHETIC"],
                record["governance"]["categoryIds"],
            )
            self.assertNotIn("CAT_RESEARCH_REPORT_METADATA", record["governance"]["categoryIds"])
        for record in successful:
            structured = record["trainingTarget"]["structuredOutput"]
            self.assertEqual("RESEARCH_REPORT_REVIEW_DRAFT", structured["kind"])
            self.assertEqual(
                {
                    "kind",
                    "reportRef",
                    "reviewSummary",
                    "issues",
                    "suggestions",
                    "requiresHumanReview",
                    "advisoryOnly",
                },
                set(structured),
            )

    def test_dataset_splits_are_disjoint_and_training_remains_pending(self):
        split_records = {}
        for split in ("train", "validation", "evaluation"):
            split_records[split] = [
                json.loads(line)
                for line in (DATASET_DIRECTORY / f"{split}.jsonl")
                .read_text(encoding="utf-8")
                .splitlines()
                if line
            ]
        content_ids = {
            split: {record["contentId"] for record in records}
            for split, records in split_records.items()
        }
        manifest = json.loads(
            (DATASET_DIRECTORY / "manifest.json").read_text(encoding="utf-8")
        )

        self.assertEqual({"train": 112, "validation": 16, "evaluation": 16}, {
            split: len(records) for split, records in split_records.items()
        })
        self.assertFalse(content_ids["train"] & content_ids["validation"])
        self.assertFalse(content_ids["train"] & content_ids["evaluation"])
        self.assertFalse(content_ids["validation"] & content_ids["evaluation"])
        self.assertEqual("PENDING_TRAINING_APPROVAL", manifest["lifecycle_status"])
        self.assertFalse(manifest["trainingAuthorized"])
        self.assertEqual([], manifest["approval_references"])

    def test_no_frozen_evaluation_identifiers_are_used_as_training_content(self):
        rendered = json.dumps(self.source, ensure_ascii=False)
        self.assertNotIn("E-FUNC-RESEARCH-", rendered)
        self.assertNotIn("E-AUTH-", rendered)
        self.assertNotIn("E-HUMAN-", rendered)
        self.assertTrue(all(not record["metadata"]["evaluationDerived"] for record in self.source["records"]))

    def test_validator_reports_approved_dataset_and_rejects_pending_data_tampering(self):
        result = subprocess.run(
            [sys.executable, str(VALIDATOR_PATH), "--artifact-root", str(ROOT)],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        state = json.loads(result.stdout)
        self.assertEqual("VALID_TRAINING_APPROVED", state["state"])
        self.assertTrue(state["trainingAllowed"])
        self.assertTrue(state["externalTrainingAllowed"])

        with tempfile.TemporaryDirectory() as name:
            artifact_root = Path(name)
            for relative_path, content in self.builder.build_artifacts().items():
                target = artifact_root / relative_path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(content)
            tampered = artifact_root / "datasets/p7-research-synthetic-training-dataset-v4/train.jsonl"
            lines = tampered.read_text(encoding="utf-8").splitlines()
            record = json.loads(lines[0])
            record["trainingTarget"]["response"]["text"] += " tampered"
            lines[0] = json.dumps(record, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
            tampered.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")

            rejected = subprocess.run(
                [
                    sys.executable,
                    str(VALIDATOR_PATH),
                    "--artifact-root",
                    str(artifact_root),
                ],
                cwd=ROOT,
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(2, rejected.returncode)
            self.assertEqual("INVALID", json.loads(rejected.stderr)["state"])


if __name__ == "__main__":
    unittest.main()
