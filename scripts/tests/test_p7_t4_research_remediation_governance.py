import copy
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-source.py"
PACKET_BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-governance-packet.py"


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T4ResearchRemediationSourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = load_module("p7_t4_remediation_source", SOURCE_BUILDER_PATH)

    def test_checked_in_source_reproduces_byte_for_byte(self):
        artifacts = self.source.build_artifacts()

        self.assertEqual(
            {"training-contract.json", "source-export.json", "provenance.json"},
            set(artifacts),
        )
        for filename, expected in artifacts.items():
            self.assertEqual(
                expected,
                (self.source.CANONICAL_OUTPUT_DIRECTORY / filename).read_bytes(),
            )

    def test_source_is_contract_aligned_and_excludes_frozen_evaluation_material(self):
        documents = self.source.build_documents()
        contract = documents["training-contract.json"]
        export = documents["source-export.json"]
        provenance = documents["provenance.json"]
        records = export["records"]

        self.assertEqual("PROPOSED_FOR_GOVERNANCE", contract["state"])
        self.assertEqual(45, len(records))
        self.assertEqual(
            ["RESEARCH_UC_003", "RESEARCH_UC_004", "RESEARCH_UC_005"],
            sorted({record["useCaseId"] for record in records}),
        )
        self.assertEqual(
            {"evalCaseId", "response", "observedBehavior", "observedActionRisk", "toolRequest", "structuredOutput", "referencedContextIds"},
            set(contract["outputContract"]["closedKeys"]),
        )
        self.assertFalse(provenance["syntheticData"]["evaluationMaterialCopied"])
        self.assertEqual([], provenance["lineage"]["evaluationTrainingSources"])
        self.assertEqual(
            "TRAINING_PROHIBITED_BY_CURRENT_GOVERNANCE",
            provenance["coverage"]["excludedUseCases"]["RESEARCH_UC_006"],
        )

        for record in records:
            prompt = record["trainingPrompt"]
            target = record["trainingTarget"]
            self.assertEqual(prompt["evalCaseId"], target["evalCaseId"])
            self.assertEqual(set(contract["outputContract"]["closedKeys"]), set(target))
            self.assertEqual("RESEARCH_ASSISTANT", prompt["assistantKey"])
            self.assertFalse(record["metadata"]["evaluationDerived"])

    def test_positive_drafts_match_prepared_runtime_schema_without_execution(self):
        records = self.source.build_documents()["source-export.json"]["records"]
        positive_drafts = [
            record
            for record in records
            if record["useCaseId"] in {"RESEARCH_UC_004", "RESEARCH_UC_005"}
            and record["legacySemanticReference"]["behavior"] == "DRAFT_ONLY"
        ]

        self.assertEqual(20, len(positive_drafts))
        for record in positive_drafts:
            target = record["trainingTarget"]
            self.assertEqual("SUCCESS", target["observedBehavior"])
            self.assertEqual("DRAFT_ONLY", target["observedActionRisk"])
            self.assertTrue(target["structuredOutput"]["requiresHumanReview"])
            self.assertEqual("REQUEST", target["toolRequest"]["kind"])
            self.assertIn("NO_EXECUTION", target["response"]["markers"])
            self.assertIn("HUMAN_REVIEW_NEEDED", target["response"]["markers"])

    def test_source_write_is_deterministic_and_append_safe(self):
        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "first"
            second = Path(directory) / "second"
            self.source.write_source(first)
            self.source.write_source(second)
            self.assertEqual(
                {path.name: path.read_bytes() for path in first.iterdir()},
                {path.name: path.read_bytes() for path in second.iterdir()},
            )
            with self.assertRaisesRegex(self.source.SourceBuildError, "must not already exist"):
                self.source.write_source(first)


class P7T4ResearchRemediationGovernancePacketTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.packet = load_module("p7_t4_remediation_packet", PACKET_BUILDER_PATH)

    def test_checked_in_packet_reproduces_byte_for_byte(self):
        artifacts = self.packet.build_artifacts()
        self.assertEqual(
            {"training-approval-request.json", "training-dataset-card.pending.json"},
            set(artifacts),
        )
        for filename, expected in artifacts.items():
            self.assertEqual(
                expected,
                (self.packet.CANONICAL_OUTPUT_DIRECTORY / filename).read_bytes(),
            )

    def test_request_is_source_and_contract_bound_but_not_approved(self):
        documents = self.packet.build_documents()
        request = documents["training-approval-request.json"]
        card = documents["training-dataset-card.pending.json"]

        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertIsNone(request["approval"])
        self.assertIsNone(request["approvedBy"])
        self.assertIsNone(request["approvedAt"])
        self.assertFalse(request["currentState"]["trainingAuthorized"])
        self.assertEqual(["TRAINING"], request["requestedScope"]["permittedPurposes"])
        self.assertEqual("2.0.0", request["candidateCard"]["datasetVersion"])
        self.assertEqual(self.packet.request_identity(request), request["requestIdentity"])
        self.assertEqual("PENDING", card["approval_status"])
        self.assertEqual([], card["approval_references"])
        self.assertEqual([], card["source_permission_references"])
        self.assertEqual([], card["approved_purposes"])
        self.assertEqual(["DEVELOPMENT_TEST"], card["permitted_purposes"])

    def test_request_repository_base_commit_is_a_real_commit(self):
        request = self.packet.build_documents()["training-approval-request.json"]

        self.assertEqual(
            self.packet.SOURCE_BASE_COMMIT,
            request["repositoryBaseCommit"],
        )
        result = subprocess.run(
            ["git", "cat-file", "-e", f"{request['repositoryBaseCommit']}^{{commit}}"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_packet_keeps_frozen_evaluation_out_of_training_and_report_review_deferred(self):
        documents = self.packet.build_documents()
        request = documents["training-approval-request.json"]
        rendered = self.packet.canonical_bytes(documents)

        self.assertFalse(request["requestedScope"]["frozenEvaluationTrainingUseAllowed"])
        self.assertEqual(
            ["RESEARCH_UC_003", "RESEARCH_UC_004", "RESEARCH_UC_005"],
            request["requestedScope"]["includedUseCases"],
        )
        self.assertEqual(
            {"RESEARCH_UC_006": "TRAINING_PROHIBITED_BY_CURRENT_GOVERNANCE"},
            request["requestedScope"]["excludedUseCases"],
        )
        self.assertNotIn(b"evals/p7-t3-research-gap-evaluation-suite", rendered)
        self.assertNotIn(b"evidence/p7-t3-research-report-eval-governance-approval", rendered)

    def test_recomputed_identity_cannot_hide_source_or_scope_tampering(self):
        documents = self.packet.build_documents()

        source_tamper = copy.deepcopy(documents)
        request = source_tamper["training-approval-request.json"]
        request["source"]["sourceSha256"] = "0" * 64
        request["requestIdentity"] = self.packet.request_identity(request)
        with self.assertRaisesRegex(self.packet.PacketBuildError, "source-bound"):
            self.packet.validate_documents(source_tamper)

        scope_tamper = copy.deepcopy(documents)
        request = scope_tamper["training-approval-request.json"]
        request["requestedScope"]["includedUseCases"].append("RESEARCH_UC_006")
        request["requestIdentity"] = self.packet.request_identity(request)
        with self.assertRaisesRegex(self.packet.PacketBuildError, "source-bound"):
            self.packet.validate_documents(scope_tamper)


if __name__ == "__main__":
    unittest.main()
