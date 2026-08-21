import hashlib
import importlib.util
import json
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("p7t1", ROOT / "scripts" / "dataset-pipeline-p7-t1.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class P7T1DatasetPipelineTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        governance = yaml.safe_load(
            (ROOT / "docs" / "architecture" / "ai" / "data-governance.yml").read_text(encoding="utf-8")
        )["contract"]
        cls.card = governance["validation_fixtures"]["approved_domain_export"]["card"]
        cls.evaluation_manifest = governance["validation_fixtures"]["approved_frozen_evaluation_export"]["manifest"]
        cases = yaml.safe_load(
            (ROOT / "docs" / "architecture" / "ai" / "datasets" / "fixtures" / "p6-t3-cases.yaml").read_text(
                encoding="utf-8"
            )
        )["cases"]
        cls.base_record = next(case["record"] for case in cases if case["id"] == "POS-ADMIN-001")

    def config(self):
        return {
            "schemaVersion": "1.0.0",
            "split": {
                "strategy": "SHA256_CONTENT_BUCKET",
                "seed": "lab-portal-p7-t1-v1",
                "trainWeight": 80,
                "validationWeight": 10,
                "evaluationWeight": 10,
            },
            "cardReference": "approved-admin-system-status-card-v1",
        }

    def records(self, count=36):
        records = []
        for index in range(count):
            record = deepcopy(self.base_record)
            record["recordId"] = f"record-admin-system-{index}"
            record["payload"]["resourceRef"]["resourceId"] = f"system-status-{index}"
            record["expectedOutput"]["summary"] = f"Synthetic safe result {index}."
            records.append(record)
        return records

    def source(self, records):
        return {
            "exportSchemaVersion": "1.0.0",
            "source": {
                "identity": "spring-admin-system-status-export-v1",
                "authorizationBoundary": "SPRING_AUTHORIZED_CONTEXT",
                "sourceDataOwner": self.card["source_data_owner"],
                "sourcePermissionReference": self.card["source_permission_references"][0],
                "approvalReference": self.card["approval_references"][0],
            },
            "records": records,
        }

    def build(self, root, name, records):
        output = Path(root) / name
        manifest = MODULE.build_dataset(
            self.source(records),
            deepcopy(self.card),
            self.config(),
            output,
            deepcopy(self.evaluation_manifest),
        )
        return output, manifest

    @staticmethod
    def artifact_bytes(output):
        return {path.name: path.read_bytes() for path in sorted(output.iterdir()) if path.is_file()}

    def test_identical_input_and_config_reproduce_every_artifact_and_checksum(self):
        records = self.records()
        with tempfile.TemporaryDirectory() as temporary_directory:
            first, first_manifest = self.build(temporary_directory, "first", records)
            second, second_manifest = self.build(temporary_directory, "second", deepcopy(records))

            self.assertEqual(self.artifact_bytes(first), self.artifact_bytes(second))
            self.assertEqual(first_manifest["checksum"], second_manifest["checksum"])
            self.assertEqual(first_manifest, second_manifest)

    def test_input_order_does_not_change_canonical_output_or_split_assignment(self):
        records = self.records()
        with tempfile.TemporaryDirectory() as temporary_directory:
            first, _ = self.build(temporary_directory, "ordered", records)
            second, _ = self.build(temporary_directory, "reversed", list(reversed(records)))

            self.assertEqual(self.artifact_bytes(first), self.artifact_bytes(second))

    def test_duplicates_are_removed_by_training_content_not_source_record_id(self):
        records = self.records(8)
        duplicate = deepcopy(records[0])
        duplicate["recordId"] = "record-duplicate-source-id"
        with tempfile.TemporaryDirectory() as temporary_directory:
            output, manifest = self.build(temporary_directory, "deduplicated", records + [duplicate])

            artifact_records = MODULE.read_artifact_records(output)
            self.assertEqual(manifest["counts"]["sourceRecords"], 9)
            self.assertEqual(manifest["counts"]["duplicatesRemoved"], 1)
            self.assertEqual(len(artifact_records), 8)
            self.assertEqual(len({record["contentId"] for record in artifact_records}), 8)

    def test_invalid_records_are_reported_deterministically_without_silent_mutation(self):
        invalid = self.records(1)[0]
        del invalid["expectedOutput"]
        with tempfile.TemporaryDirectory() as temporary_directory:
            first, first_manifest = self.build(temporary_directory, "first-invalid", [invalid])
            second, second_manifest = self.build(temporary_directory, "second-invalid", [deepcopy(invalid)])

            first_rejections = (first / "rejections.jsonl").read_bytes()
            self.assertEqual(first_rejections, (second / "rejections.jsonl").read_bytes())
            self.assertEqual(first_manifest["counts"]["rejectedRecords"], 1)
            self.assertEqual(first_manifest, second_manifest)
            diagnostic = json.loads(first_rejections.decode("utf-8"))
            self.assertTrue(any("expectedOutput" in item for item in diagnostic["diagnostics"]))

    def test_sanitizer_allowlist_excludes_sensitive_and_unknown_source_fields(self):
        record = self.records(1)[0]
        record["passwordHash"] = "must-never-be-exported"
        with tempfile.TemporaryDirectory() as temporary_directory:
            output, manifest = self.build(temporary_directory, "sanitized", [record])

            combined = b"".join(self.artifact_bytes(output).values())
            self.assertNotIn(b"passwordHash", combined)
            self.assertNotIn(b"must-never-be-exported", combined)
            self.assertEqual(manifest["counts"]["acceptedRecords"], 1)

    def test_split_membership_is_reproducible_disjoint_and_manifest_counts_match(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            output, manifest = self.build(temporary_directory, "splits", self.records())

            split_ids = {}
            for name in ("train", "validation", "evaluation"):
                records = MODULE.read_jsonl(output / f"{name}.jsonl")
                split_ids[name] = {record["contentId"] for record in records}
                self.assertEqual(manifest["counts"]["splits"][name], len(records))
            self.assertTrue(split_ids["train"].isdisjoint(split_ids["validation"]))
            self.assertTrue(split_ids["train"].isdisjoint(split_ids["evaluation"]))
            self.assertTrue(split_ids["validation"].isdisjoint(split_ids["evaluation"]))
            self.assertEqual(sum(map(len, split_ids.values())), manifest["counts"]["acceptedRecords"])

    def test_manifest_hashes_match_final_utf8_lf_artifact_bytes_and_dataset_identity(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            output, manifest = self.build(temporary_directory, "checksums", self.records())

            for artifact in manifest["artifacts"]:
                artifact_bytes = (output / artifact["filename"]).read_bytes()
                self.assertNotIn(b"\r\n", artifact_bytes)
                artifact_bytes.decode("utf-8")
                self.assertEqual(artifact["sha256"], hashlib.sha256(artifact_bytes).hexdigest())
            self.assertEqual(manifest["checksum"], MODULE.dataset_identity(manifest))

    def test_controlled_export_rejects_missing_spring_boundary_or_permission_evidence(self):
        source = self.source(self.records(1))
        del source["source"]["authorizationBoundary"]
        with tempfile.TemporaryDirectory() as temporary_directory:
            with self.assertRaisesRegex(MODULE.DatasetPipelineError, "authorizationBoundary"):
                MODULE.build_dataset(
                    source,
                    deepcopy(self.card),
                    self.config(),
                    Path(temporary_directory) / "denied",
                    deepcopy(self.evaluation_manifest),
                )

    def test_governance_gate_rejects_malformed_evidence_and_shared_assistant_tokens(self):
        malformed_card = deepcopy(self.card)
        malformed_card["source_permission_references"] = "source-permission-reference"
        with self.assertRaisesRegex(MODULE.DatasetPipelineError, "source_permission_references"):
            MODULE.validate_card(malformed_card, deepcopy(self.evaluation_manifest))

        governance = yaml.safe_load(
            (ROOT / "docs" / "architecture" / "ai" / "data-governance.yml").read_text(encoding="utf-8")
        )["contract"]
        shared_card = deepcopy(governance["validation_fixtures"]["approved_shared_export"]["card"])
        shared_card["contributing_source_data_owners"] = ["ADMIN_ASSISTANT"]
        with self.assertRaisesRegex(MODULE.DatasetPipelineError, "contributing_source_data_owners"):
            MODULE.validate_card(shared_card, deepcopy(self.evaluation_manifest))

    def test_governance_gate_rejects_local_paths_in_canonical_references(self):
        card = deepcopy(self.card)
        card["lineage"]["source_references"] = [r"C:\\private\\raw-export.json"]
        with self.assertRaisesRegex(MODULE.DatasetPipelineError, "local absolute paths"):
            MODULE.validate_card(card, deepcopy(self.evaluation_manifest))

        source = self.source(self.records(1))
        source["source"]["sourcePermissionReference"] = "unapproved-permission"
        with tempfile.TemporaryDirectory() as temporary_directory:
            with self.assertRaisesRegex(MODULE.DatasetPipelineError, "sourcePermissionReference"):
                MODULE.build_dataset(
                    source,
                    deepcopy(self.card),
                    self.config(),
                    Path(temporary_directory) / "denied",
                    deepcopy(self.evaluation_manifest),
                )


if __name__ == "__main__":
    unittest.main()
