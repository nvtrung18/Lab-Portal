import importlib.util
import json
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[2]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-v4-preparation.py"
OUTPUT_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v4"
EVIDENCE_DIRECTORY = (
    ROOT
    / "evidence"
    / "p7-t4-research-independent-evaluation"
    / "automatic-fail-remediation-v3-stability"
)


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T4ResearchRemediationV4EvidenceTests(unittest.TestCase):
    def test_latest_automatic_failure_is_materialized_without_review_artifacts(self):
        expected = {
            "comparison.json",
            "p7-t4-backup-automatic-fail.zip.sha256",
            "preflight.json",
            "runs/RESEARCH_ADAPTER/R01.json",
            "runs/RESEARCH_ADAPTER/R02.json",
            "runs/RESEARCH_ADAPTER/R03.json",
            "runs/SHARED_BASE/R01.json",
            "runs/SHARED_BASE/R02.json",
            "runs/SHARED_BASE/R03.json",
        }
        actual = {
            path.relative_to(EVIDENCE_DIRECTORY).as_posix()
            for path in EVIDENCE_DIRECTORY.rglob("*")
            if path.is_file()
        }
        self.assertEqual(expected, actual)

        comparison = json.loads(
            (EVIDENCE_DIRECTORY / "comparison.json").read_text(encoding="utf-8")
        )
        self.assertEqual("AUTOMATIC_FAIL", comparison["automaticDecision"])
        self.assertFalse(comparison["promotionAllowed"])
        self.assertEqual(14, len(comparison["adapterFailedCaseIds"]))
        self.assertEqual(4, len(comparison["improvedCaseIds"]))
        self.assertEqual([], comparison["regressions"]["all"])

        repetitions = []
        for repetition in ("R01", "R02", "R03"):
            run = json.loads(
                (
                    EVIDENCE_DIRECTORY
                    / "runs"
                    / "RESEARCH_ADAPTER"
                    / f"{repetition}.json"
                ).read_text(encoding="utf-8")
            )
            repetitions.append(
                {
                    item["evalCaseId"]
                    for item in run["automatic"]["automaticReport"]
                    if item["automaticState"] == "FAIL"
                }
            )
            self.assertEqual(
                "34e3d50b8bf91d27569305fff47247feaf0de487f9b4e78fd94f7ed64dbc62bd",
                run["candidateRun"]["modelMetadata"]["candidateId"],
            )
        self.assertEqual([set(comparison["adapterFailedCaseIds"])] * 3, repetitions)


class P7T4ResearchRemediationV4PreparationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("p7_t4_remediation_v4_preparation", BUILDER_PATH)

    def test_checked_in_packet_reproduces_byte_for_byte(self):
        artifacts = self.builder.build_artifacts()

        self.assertEqual(
            {
                "data-governance-amendment-request.json",
                "structured-output-schema.pending.json",
                "training-data-quality-spec.json",
            },
            set(artifacts),
        )
        for filename, expected in artifacts.items():
            self.assertEqual(expected, (OUTPUT_DIRECTORY / filename).read_bytes())

    def test_evidence_binding_rejects_tampered_comparison_or_run(self):
        for relative_path in (
            Path("comparison.json"),
            Path("runs/RESEARCH_ADAPTER/R01.json"),
        ):
            with self.subTest(relative_path=relative_path), tempfile.TemporaryDirectory() as name:
                copied_evidence = Path(name) / "evidence"
                shutil.copytree(EVIDENCE_DIRECTORY, copied_evidence)
                artifact_path = copied_evidence / relative_path
                artifact = json.loads(artifact_path.read_text(encoding="utf-8"))
                artifact["tampered"] = True
                artifact_path.write_text(
                    json.dumps(artifact, ensure_ascii=False, sort_keys=True, indent=2) + "\n",
                    encoding="utf-8",
                )

                with mock.patch.object(
                    self.builder, "EVIDENCE_DIRECTORY", copied_evidence
                ), self.assertRaises(self.builder.PreparationError):
                    self.builder._evidence_binding()

    def test_request_preserves_the_deferred_report_category_and_requests_a_new_synthetic_category(self):
        documents = self.builder.build_documents()
        request = documents["data-governance-amendment-request.json"]

        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertFalse(request["currentState"]["trainingAuthorized"])
        self.assertEqual(
            "CAT_RESEARCH_REPORT_METADATA",
            request["preservedCategory"]["categoryId"],
        )
        self.assertEqual("DEFERRED", request["preservedCategory"]["useDecision"])
        self.assertIn("TRAINING", request["preservedCategory"]["prohibitedPurposes"])
        self.assertEqual(
            {
                "categoryId": "CAT_RESEARCH_REPORT_REVIEW_SYNTHETIC",
                "useCaseIds": ["RESEARCH_UC_006"],
                "useDecision": "SYNTHETIC_ONLY",
                "permittedPurposes": ["TRAINING", "DEVELOPMENT_TEST"],
                "prohibitedPurposes": [],
                "sanitizationDisposition": "SYNTHETIC_GENERATION_ONLY",
            },
            request["proposedCategory"],
        )
        self.assertEqual(
            "489ee4e22d5402ecfed280036e19bdf5dbe23bd41ea6854d1d0a4dc7639d4be4",
            request["remediationBinding"]["failedComparisonIdentity"],
        )
        self.assertEqual(
            "34e3d50b8bf91d27569305fff47247feaf0de487f9b4e78fd94f7ed64dbc62bd",
            request["remediationBinding"]["failedCandidateId"],
        )
        self.assertFalse(request["requestedScope"]["frozenEvaluationTrainingUseAllowed"])
        self.assertEqual(self.builder.request_identity(request), request["requestIdentity"])

    def test_pending_schema_adds_only_the_missing_report_review_variant(self):
        documents = self.builder.build_documents()
        pending = documents["structured-output-schema.pending.json"]
        research = next(
            item
            for item in pending["schemas"]
            if item["schemaId"] == "research-assistant-output-v2"
        )
        variants = {
            item["properties"]["kind"]["const"]: set(item["required"])
            for item in research["schema"]["oneOf"]
        }

        self.assertEqual("PENDING_GOVERNANCE_APPROVAL", pending["status"])
        self.assertEqual(
            {
                "RESEARCH_TASK_PROPOSAL_DRAFT",
                "RESEARCH_TASK_SUGGESTION_DRAFT",
                "RESEARCH_REPORT_REVIEW_DRAFT",
            },
            set(variants),
        )
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
            variants["RESEARCH_REPORT_REVIEW_DRAFT"],
        )

    def test_quality_spec_targets_the_reproducible_failures_without_evaluation_leakage(self):
        documents = self.builder.build_documents()
        specification = documents["training-data-quality-spec.json"]

        self.assertEqual("QUALITY_SPEC_READY_AWAITING_GOVERNANCE_APPROVAL", specification["state"])
        self.assertEqual(14, len(specification["failureCoverage"]["caseIds"]))
        self.assertEqual(
            {
                "AUTHORIZATION_CONTRAST",
                "CLOSED_JSON_ENVELOPE",
                "EXACT_TOOL_ROUTING",
                "DRAFT_RESPONSE_MODE",
                "REPORT_REVIEW_SCHEMA",
            },
            set(specification["qualityDimensions"]),
        )
        self.assertEqual(144, specification["plannedDataset"]["recordCount"])
        self.assertEqual(
            {
                "RESEARCH_UC_003": 36,
                "RESEARCH_UC_004": 36,
                "RESEARCH_UC_005": 36,
                "RESEARCH_UC_006": 36,
            },
            specification["plannedDataset"]["byUseCase"],
        )
        self.assertEqual(0, specification["acceptanceCriteria"]["developmentFailedCases"])
        self.assertEqual(3, specification["acceptanceCriteria"]["deterministicRepetitions"])
        self.assertFalse(specification["antiLeakage"]["frozenEvaluationDerivedRecordsAllowed"])
        self.assertEqual([], specification["antiLeakage"]["evaluationTrainingSources"])


if __name__ == "__main__":
    unittest.main()
