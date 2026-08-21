import importlib.util
import json
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "p7t1a_research_source",
    ROOT / "scripts" / "build-p7-t1a-research-synthetic-source.py",
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class P7T1AResearchSyntheticSourceTests(unittest.TestCase):
    def test_checked_in_source_reproduces_byte_for_byte(self):
        artifacts, _, _ = MODULE.build_artifacts()

        self.assertEqual(
            set(artifacts),
            {"source-export.json", "provenance.json"},
        )
        for filename, expected in artifacts.items():
            self.assertEqual(
                expected,
                (MODULE.CANONICAL_OUTPUT_DIRECTORY / filename).read_bytes(),
            )

    def test_source_is_pending_synthetic_and_not_training_authorized(self):
        _, export, provenance = MODULE.build_artifacts()

        self.assertIsNone(export["source"]["sourcePermissionReference"])
        self.assertIsNone(export["source"]["approvalReference"])
        self.assertEqual("PENDING", provenance["governance"]["approvalStatus"])
        self.assertEqual("PENDING_APPROVAL", provenance["governance"]["lifecycleStatus"])
        self.assertFalse(provenance["governance"]["materializationAuthorized"])
        self.assertFalse(provenance["governance"]["trainingAuthorized"])
        self.assertEqual(["DEVELOPMENT_TEST"], provenance["governance"]["currentPermittedPurposes"])
        self.assertEqual(["TRAINING"], provenance["governance"]["proposedPurposes"])
        self.assertEqual(
            {
                "assistantKey": "RESEARCH_ASSISTANT",
                "purpose": "TRAINING",
                "trainingApproaches": ["LoRA", "QLoRA"],
                "subjectToExplicitGovernanceApproval": True,
            },
            provenance["intendedFutureUse"],
        )
        self.assertTrue(provenance["syntheticData"]["fullySynthetic"])
        self.assertTrue(provenance["ownership"]["projectOwned"])

    def test_records_are_unique_schema_valid_and_research_only(self):
        _, export, provenance = MODULE.build_artifacts()
        records = export["records"]

        self.assertGreaterEqual(len(records), 30)
        self.assertEqual(len(records), len({record["recordId"] for record in records}))
        self.assertEqual(len(records), provenance["inventory"]["recordCount"])
        self.assertEqual(
            {"RESEARCH_UC_003", "RESEARCH_UC_004", "RESEARCH_UC_005"},
            {record["useCaseId"] for record in records},
        )
        for record in records:
            self.assertEqual([], MODULE.P7T1.validate_record(record))
            self.assertEqual("RESEARCH", record["domain"])
            self.assertTrue(record["metadata"]["synthetic"])

    def test_source_and_provenance_identities_bind_exact_bytes(self):
        artifacts, export, provenance = MODULE.build_artifacts()

        self.assertEqual(
            MODULE.sha256_bytes(artifacts["source-export.json"]),
            provenance["artifacts"][0]["sha256"],
        )
        self.assertEqual(
            MODULE.sha256_bytes(MODULE.canonical_bytes(export["records"])),
            provenance["contentIdentity"],
        )
        self.assertEqual(MODULE._provenance_identity(provenance), provenance["provenanceIdentity"])
        self.assertEqual(0, provenance["antiLeakage"]["exactTrainingContentDuplicates"])
        self.assertEqual(0, provenance["antiLeakage"]["exactCanonicalNodeDuplicates"])
        self.assertEqual(0, provenance["antiLeakage"]["exactSubstantialStringDuplicates"])

    def test_p7_t1_materialization_remains_fail_closed_without_approval(self):
        _, export, _ = MODULE.build_artifacts()
        pending_card = {
            "source_data_owner": MODULE.SOURCE_OWNER,
            "source_permission_references": ["pending-source-permission"],
            "approval_references": ["pending-governance-approval"],
        }

        with self.assertRaisesRegex(MODULE.P7T1.DatasetPipelineError, "approvalReference"):
            MODULE.P7T1.validate_controlled_export(export, pending_card)

    def test_fixture_record_is_rejected_as_leakage(self):
        fixtures = MODULE.load_document(MODULE.P6_FIXTURE_PATH)
        copied = deepcopy(
            next(case["record"] for case in fixtures["cases"] if case["id"] == "POS-RESEARCH-003")
        )
        copied["recordId"] = "record-p7t1a-copied-fixture"

        with self.assertRaisesRegex(MODULE.SourceBuildError, "leakage"):
            MODULE.validate_records([copied])

    def test_writes_are_deterministic_and_append_safe(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            first = root / "first"
            second = root / "second"
            MODULE.write_source(first)
            MODULE.write_source(second)

            self.assertEqual(
                {path.name: path.read_bytes() for path in first.iterdir()},
                {path.name: path.read_bytes() for path in second.iterdir()},
            )
            with self.assertRaisesRegex(MODULE.SourceBuildError, "must not already exist"):
                MODULE.write_source(first)


if __name__ == "__main__":
    unittest.main()
