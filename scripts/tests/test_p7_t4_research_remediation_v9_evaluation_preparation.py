import hashlib
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVIDENCE_ROOT = ROOT / "evidence/p7-t2-real-training/remediation-v9"
REQUEST_PATH = ROOT / "config/p7-t4-research-remediation-governance-v9/external-evaluation-approval-request.json"
CONFIG_PATH = ROOT / "config/p7-t4-research-independent-evaluation-remediation-v9.json"
APPROVAL_PATH = ROOT / "evidence/p7-t4-research-remediation-v9-external-evaluation-approval.json"
CANDIDATE_ID = "99596dcf3c00c6a25a4c38f01de7f12d917100e539cf4ea777556ef0c0a055c2"
TRAINING_RUN_IDENTITY = "9ee7cd3cb56cb5b18c096ce8764fe14a23d69bdd39ef027a8f1472710b152314"
ARCHIVE_SHA256 = "f6c3e5d4ca52643f8c26941a61407444f7004b5e1e22c08445307821dbee767f"
DATASET_IDENTITY = "6091c1be0a482bda5bc51d51e64583bd4a87bd4befe71b1f220535d6d03216a3"
SOURCE_COMMIT = "01e58a551dc7377667030275fb516f165699fe8a"
ADAPTER_IDENTITY = "ceab7b262b050cf9195001e4d7e5f40aa7ef67011e0e7271e62d250ffe96c717"
EVIDENCE_IDENTITY = "dc8622368cfc2301625abee45271b5f101eab36db536f184c5f2e9d025317c2a"


def canonical_bytes(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")


def identity(value, field):
    return hashlib.sha256(canonical_bytes({key: item for key, item in value.items() if key != field})).hexdigest()


class P7T4ResearchRemediationV9EvaluationPreparationTests(unittest.TestCase):
    def test_imported_output_is_exact_candidate_only_evidence(self):
        manifest = json.loads((EVIDENCE_ROOT / "adapter-manifest.json").read_text(encoding="utf-8"))
        evidence = json.loads((EVIDENCE_ROOT / "real-training-evidence.json").read_text(encoding="utf-8"))
        metadata = json.loads((EVIDENCE_ROOT / "training-metadata.json").read_text(encoding="utf-8"))
        sidecar = (EVIDENCE_ROOT / "p7-t2-research-remediation-v9-output.zip.sha256").read_text(encoding="utf-8")

        for document in (manifest, evidence, metadata):
            self.assertEqual(CANDIDATE_ID, document["candidateId"])
            self.assertEqual(TRAINING_RUN_IDENTITY, document["trainingRunIdentity"])
        self.assertEqual(DATASET_IDENTITY, metadata["datasetIdentity"])
        self.assertEqual(SOURCE_COMMIT, metadata["sourceCommit"])
        self.assertEqual(ADAPTER_IDENTITY, manifest["adapterIdentity"])
        self.assertEqual(EVIDENCE_IDENTITY, evidence["artifactIdentity"])
        self.assertEqual(EVIDENCE_IDENTITY, identity(evidence, "artifactIdentity"))
        self.assertEqual("CANDIDATE_ONLY", manifest["adapterDisposition"])
        self.assertEqual("CANDIDATE_ONLY", metadata["adapterDisposition"])
        self.assertEqual(480, metadata["actualTraining"]["trainRecords"])
        self.assertEqual(80, metadata["actualTraining"]["validationRecords"])
        self.assertEqual(80, metadata["actualTraining"]["contractHoldoutRecords"])
        self.assertFalse(metadata["actualTraining"]["contractHoldoutUsedForOptimization"])
        self.assertEqual(f"{ARCHIVE_SHA256}  p7-t2-research-remediation-v9-output.zip\n", sidecar)

    def test_external_evaluation_request_is_pending_and_fail_closed(self):
        request = json.loads(REQUEST_PATH.read_text(encoding="utf-8"))
        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertEqual(CANDIDATE_ID, request["candidate"]["candidateId"])
        self.assertEqual(TRAINING_RUN_IDENTITY, request["candidate"]["trainingRunIdentity"])
        self.assertEqual(ARCHIVE_SHA256, request["candidate"]["archiveSha256"])
        self.assertTrue(request["requestedAuthorization"]["externalEvaluationExecutionAllowed"])
        self.assertFalse(request["requestedAuthorization"]["promotionAllowed"])
        self.assertFalse(request["requestedAuthorization"]["productionPromptingAllowed"])
        self.assertFalse(request["requestedAuthorization"]["runtimeNormalizationAllowed"])
        self.assertFalse(request["requestedAuthorization"]["constrainedDecodingAllowed"])
        self.assertEqual(request["requestIdentity"], identity(request, "requestIdentity"))

    def test_evaluation_config_binds_v9_and_requires_separate_approval(self):
        config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
        self.assertEqual(CANDIDATE_ID, config["adapter"]["candidateId"])
        self.assertEqual(ADAPTER_IDENTITY, config["adapter"]["adapterIdentity"])
        self.assertEqual("CANDIDATE_ONLY", config["adapter"]["disposition"])
        self.assertEqual(
            "evidence/p7-t2-real-training/real-training-evidence.json",
            config["adapter"]["evidenceReference"],
        )
        self.assertEqual(
            "evidence/p7-t2-real-training/adapter-manifest.json",
            config["adapter"]["manifestReference"],
        )
        self.assertEqual(
            "evidence/p7-t4-research-remediation-v9-external-evaluation-approval.json",
            config["executionApproval"]["approvalReference"],
        )
        self.assertTrue(config["executionApproval"]["required"])
        self.assertFalse(config["runtimeControls"]["constrainedDecodingAllowed"])
        self.assertFalse(config["runtimeControls"]["runtimeNormalizationAllowed"])

    def test_exact_user_approval_is_materialized_without_promotion_authority(self):
        request = json.loads(REQUEST_PATH.read_text(encoding="utf-8"))
        approval = json.loads(APPROVAL_PATH.read_text(encoding="utf-8"))

        self.assertEqual("APPROVED", approval["status"])
        self.assertEqual("APPROVED", approval["approval"]["decision"])
        self.assertEqual(
            "RESEARCH_GOVERNANCE_EXTERNAL_EVALUATION_APPROVAL_AUTHORITY",
            approval["approvalAuthority"],
        )
        self.assertEqual(request["requestIdentity"], approval["requestIdentity"])
        self.assertEqual(CANDIDATE_ID, approval["approvedCandidate"]["candidateId"])
        self.assertEqual(ADAPTER_IDENTITY, approval["approvedCandidate"]["adapterIdentity"])
        self.assertEqual(EVIDENCE_IDENTITY, approval["approvedCandidate"]["evidenceIdentity"])
        self.assertEqual(
            TRAINING_RUN_IDENTITY,
            approval["approvedCandidate"]["trainingRunIdentity"],
        )
        self.assertEqual(request["requestedAuthorization"], approval["authorization"])
        self.assertFalse(approval["authorization"]["promotionAllowed"])
        self.assertEqual(
            approval["artifactIdentity"], identity(approval, "artifactIdentity")
        )


if __name__ == "__main__":
    unittest.main()
