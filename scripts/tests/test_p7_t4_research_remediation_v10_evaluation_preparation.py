import hashlib
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVIDENCE_ROOT = ROOT / "evidence/p7-t2-real-training/remediation-v10"
REQUEST_PATH = ROOT / "config/p7-t4-research-remediation-governance-v10/external-evaluation-approval-request.json"
CONFIG_PATH = ROOT / "config/p7-t4-research-independent-evaluation-remediation-v10.json"
APPROVAL_PATH = ROOT / "evidence/p7-t4-research-remediation-v10-external-evaluation-approval.json"
CANDIDATE_ID = "3cd324ad97530ce535073be761e1713cb627e15d55cb9395d55729cd93595de4"
TRAINING_RUN_IDENTITY = "8af8cfc47469457e6f22c554ed28f297ef6d97c2b9bddeabbf62a1607ecd8616"
ARCHIVE_SHA256 = "11363c390b960e9f391cf6ca54c49b4b466fb2add54aafc708d7022b5d597abe"
DATASET_IDENTITY = "abce232c1721788bae5a1686f9d017f295a6892555193140ae74c5a044e0a409"
SOURCE_COMMIT = "e8ab77e51285b7bcec491effcd8ff08c099c83e9"
ADAPTER_IDENTITY = "79d267453862e78659bf249cf571760030388725362f3c0ab9a5565324acf6e2"
EVIDENCE_IDENTITY = "bfce986c0b3bf575d7a4a75ce3de8f0b873ef70df8a3918dfa9c67f1f144c992"
TRAINING_CONFIG_IDENTITY = "2e2cf99620cf7cd6ad467c6067b12ce8a7ad8b948dcb19d5edb92ea8df812df3"
TRAINING_APPROVAL_IDENTITY = "fc9fd2b0d53ae50ce7568abdb2894f7d70c81b6e7e46d7ea65c5e333c480c553"
PARENT_ADAPTER_IDENTITY = "ceab7b262b050cf9195001e4d7e5f40aa7ef67011e0e7271e62d250ffe96c717"


def canonical_bytes(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")


def identity(value, field):
    return hashlib.sha256(canonical_bytes({key: item for key, item in value.items() if key != field})).hexdigest()


class P7T4ResearchRemediationV10EvaluationPreparationTests(unittest.TestCase):
    def test_imported_output_is_exact_candidate_only_evidence(self):
        manifest = json.loads((EVIDENCE_ROOT / "adapter-manifest.json").read_text(encoding="utf-8"))
        evidence = json.loads((EVIDENCE_ROOT / "real-training-evidence.json").read_text(encoding="utf-8"))
        metadata = json.loads((EVIDENCE_ROOT / "training-metadata.json").read_text(encoding="utf-8"))
        sidecar = (EVIDENCE_ROOT / "p7-t2-research-remediation-v10-output.zip.sha256").read_text(encoding="utf-8")

        for document in (manifest, evidence, metadata):
            self.assertEqual(CANDIDATE_ID, document["candidateId"])
            self.assertEqual(TRAINING_RUN_IDENTITY, document["trainingRunIdentity"])
        self.assertEqual(DATASET_IDENTITY, metadata["datasetIdentity"])
        self.assertEqual(SOURCE_COMMIT, metadata["sourceCommit"])
        self.assertEqual(TRAINING_CONFIG_IDENTITY, metadata["trainingConfigIdentity"])
        self.assertEqual(ADAPTER_IDENTITY, manifest["adapterIdentity"])
        self.assertEqual(EVIDENCE_IDENTITY, evidence["artifactIdentity"])
        self.assertEqual(EVIDENCE_IDENTITY, identity(evidence, "artifactIdentity"))
        self.assertEqual("CANDIDATE_ONLY", manifest["adapterDisposition"])
        self.assertEqual("CANDIDATE_ONLY", metadata["adapterDisposition"])
        self.assertFalse(metadata["actualTraining"]["contractHoldoutUsedForOptimization"])
        self.assertEqual(f"{ARCHIVE_SHA256}  p7-t2-research-remediation-v10-output.zip\n", sidecar)

    def test_external_evaluation_request_is_pending_and_fail_closed(self):
        request = json.loads(REQUEST_PATH.read_text(encoding="utf-8"))
        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertEqual(CANDIDATE_ID, request["candidate"]["candidateId"])
        self.assertEqual(ADAPTER_IDENTITY, request["candidate"]["adapterIdentity"])
        self.assertEqual(PARENT_ADAPTER_IDENTITY, request["candidate"]["parentAdapterIdentity"])
        self.assertEqual(TRAINING_APPROVAL_IDENTITY, request["priorApprovals"]["trainingApprovalIdentity"])
        self.assertTrue(request["requestedAuthorization"]["externalEvaluationExecutionAllowed"])
        self.assertFalse(request["requestedAuthorization"]["promotionAllowed"])
        self.assertFalse(request["requestedAuthorization"]["productionPromptingAllowed"])
        self.assertFalse(request["requestedAuthorization"]["runtimeNormalizationAllowed"])
        self.assertFalse(request["requestedAuthorization"]["constrainedDecodingAllowed"])
        self.assertEqual(request["requestIdentity"], identity(request, "requestIdentity"))

    def test_evaluation_config_binds_v10_and_requires_exact_approval(self):
        config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
        self.assertEqual(CANDIDATE_ID, config["adapter"]["candidateId"])
        self.assertEqual(ADAPTER_IDENTITY, config["adapter"]["adapterIdentity"])
        self.assertEqual("CANDIDATE_ONLY", config["adapter"]["disposition"])
        self.assertTrue(config["executionApproval"]["required"])
        self.assertEqual(
            "evidence/p7-t4-research-remediation-v10-external-evaluation-approval.json",
            config["executionApproval"]["approvalReference"],
        )
        self.assertEqual(["R01", "R02", "R03"], config["execution"]["repetitions"])
        self.assertFalse(config["runtimeControls"]["constrainedDecodingAllowed"])
        self.assertFalse(config["runtimeControls"]["runtimeNormalizationAllowed"])

    def test_exact_user_approval_is_materialized_without_promotion_authority(self):
        request = json.loads(REQUEST_PATH.read_text(encoding="utf-8"))
        approval = json.loads(APPROVAL_PATH.read_text(encoding="utf-8"))

        self.assertEqual("APPROVED", approval["status"])
        self.assertEqual(request["requestIdentity"], approval["requestIdentity"])
        self.assertEqual(CANDIDATE_ID, approval["approvedCandidate"]["candidateId"])
        self.assertEqual(ADAPTER_IDENTITY, approval["approvedCandidate"]["adapterIdentity"])
        self.assertEqual(PARENT_ADAPTER_IDENTITY, approval["approvedCandidate"]["parentAdapterIdentity"])
        self.assertEqual(EVIDENCE_IDENTITY, approval["approvedCandidate"]["evidenceIdentity"])
        self.assertEqual(request["requestedAuthorization"], approval["authorization"])
        self.assertFalse(approval["authorization"]["promotionAllowed"])
        self.assertEqual(approval["artifactIdentity"], identity(approval, "artifactIdentity"))


if __name__ == "__main__":
    unittest.main()
