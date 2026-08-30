import copy
import importlib.util
import subprocess
import tempfile
import unittest
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-source-v3.py"
PACKET_BUILDER_PATH = (
    ROOT / "scripts" / "build-p7-t4-research-remediation-governance-packet-v3.py"
)


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T4ResearchRemediationV3SourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = load_module("p7_t4_remediation_source_v3", SOURCE_BUILDER_PATH)

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

    def test_source_covers_the_public_runtime_shape_without_frozen_cases(self):
        documents = self.source.build_documents()
        contract = documents["training-contract.json"]
        records = documents["source-export.json"]["records"]
        provenance = documents["provenance.json"]

        self.assertEqual("PROPOSED_FOR_GOVERNANCE", contract["state"])
        self.assertEqual(270, len(records))
        self.assertEqual(
            {
                "evalCaseId",
                "assistantKey",
                "caseState",
                "suiteTags",
                "useCaseId",
                "input",
                "authorizedContext",
                "p6t3Root",
                "allowedTool",
                "rejectedTool",
                "structuredOutputContract",
                "responseContract",
                "referencedContextIds",
            },
            set(contract["inputContract"]["closedKeys"]),
        )
        self.assertEqual(
            {"NONE", "REQUEST", "REJECTED"},
            {record["trainingTarget"]["toolRequest"]["kind"] for record in records},
        )
        self.assertEqual(
            {"EN": 135, "VI": 135},
            dict(Counter(record["trainingTarget"]["response"]["language"] for record in records)),
        )
        self.assertFalse(provenance["syntheticData"]["evaluationMaterialCopied"])
        self.assertEqual([], provenance["lineage"]["evaluationTrainingSources"])
        for record in records:
            self.assertEqual(
                record["trainingPrompt"]["evalCaseId"],
                record["trainingTarget"]["evalCaseId"],
            )
            self.assertFalse(record["metadata"]["evaluationDerived"])
            self.assertNotRegex(record["trainingPrompt"]["evalCaseId"], r"^E-")

    def test_declared_tool_and_response_contracts_are_reflected_exactly(self):
        records = self.source.build_documents()["source-export.json"]["records"]

        for record in records:
            prompt = record["trainingPrompt"]
            target = record["trainingTarget"]
            declared_tool = (
                prompt["allowedTool"]
                or prompt["rejectedTool"]
                or {"kind": "NONE"}
            )
            self.assertEqual(declared_tool, target["toolRequest"])
            self.assertEqual(prompt["responseContract"], {
                key: target["response"][key]
                for key in ("mode", "language", "markers")
            })
            self.assertEqual(
                prompt["referencedContextIds"],
                target["referencedContextIds"],
            )

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


class P7T4ResearchRemediationV3GovernancePacketTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.packet = load_module("p7_t4_remediation_packet_v3", PACKET_BUILDER_PATH)

    def test_checked_in_packet_reproduces_byte_for_byte(self):
        artifacts = self.packet.build_artifacts()
        for filename, expected in artifacts.items():
            self.assertEqual(
                expected,
                (self.packet.CANONICAL_OUTPUT_DIRECTORY / filename).read_bytes(),
            )

    def test_request_is_pending_and_bound_to_the_second_automatic_failure(self):
        documents = self.packet.build_documents()
        request = documents["training-approval-request.json"]
        card = documents["training-dataset-card.pending.json"]

        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertFalse(request["currentState"]["trainingAuthorized"])
        self.assertEqual("3.0.0", request["candidateCard"]["datasetVersion"])
        self.assertEqual(
            "445a2c33e7cf7a7b9dc8b69c3ebe01ab0d7cf2565463ffb3d30920d9509baf61",
            request["remediationBinding"]["failedCandidateId"],
        )
        self.assertEqual(
            "aecbab9a601b20716821bd1ec8454924ff23b6088a35e7e1e21e4e7d654982f2",
            request["remediationBinding"]["failedComparisonIdentity"],
        )
        self.assertEqual(self.packet.request_identity(request), request["requestIdentity"])
        self.assertEqual("PENDING", card["approval_status"])
        self.assertEqual([], card["approval_references"])

    def test_request_repository_base_commit_is_real_and_training_leakage_is_forbidden(self):
        documents = self.packet.build_documents()
        request = documents["training-approval-request.json"]
        rendered = self.packet.canonical_bytes(documents)

        result = subprocess.run(
            ["git", "cat-file", "-e", f"{request['repositoryBaseCommit']}^{{commit}}"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse(request["requestedScope"]["frozenEvaluationTrainingUseAllowed"])
        self.assertNotIn(b"evals/p7-t3-research-gap-evaluation-suite", rendered)
        self.assertNotIn(
            b"evidence/p7-t3-research-report-eval-governance-approval",
            rendered,
        )

    def test_recomputed_identity_cannot_hide_source_tampering(self):
        documents = self.packet.build_documents()
        tampered = copy.deepcopy(documents)
        request = tampered["training-approval-request.json"]
        request["source"]["sourceSha256"] = "0" * 64
        request["requestIdentity"] = self.packet.request_identity(request)

        with self.assertRaisesRegex(self.packet.PacketBuildError, "source-bound"):
            self.packet.validate_documents(tampered)


if __name__ == "__main__":
    unittest.main()
