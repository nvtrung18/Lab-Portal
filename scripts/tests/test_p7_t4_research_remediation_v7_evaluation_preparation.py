import hashlib
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVIDENCE_ROOT = ROOT / "evidence/p7-t2-real-training/remediation-v7"
REQUEST_PATH = (
    ROOT
    / "config/p7-t4-research-remediation-governance-v7/"
    "external-evaluation-approval-request.json"
)
APPROVAL_PATH = (
    ROOT / "evidence/p7-t4-research-remediation-v7-external-evaluation-approval.json"
)
CONFIG_PATH = ROOT / "config/p7-t4-research-independent-evaluation-remediation-v7.json"
CANDIDATE_ID = "b2e61ccba5e79dde268d5cb96e1426fbd0d42136a0b8cdfbd1f4a414e523a9e0"
TRAINING_RUN_IDENTITY = (
    "89484f250ddff555617b7440fc55e471c4590eeb57629c470f23ca731b0f3b42"
)
ARCHIVE_SHA256 = "a9add325e062e8dbbf871575dfe6a4ca309488ebc493c3ef3c7816629754f586"
DATASET_IDENTITY = "b973bf0a387b35b84140a2db1d2bbf7c5c0d7f054102a968c10bd1df6faeff23"
SOURCE_COMMIT = "942386e9600b742d45b2c0a8174930cafedbdf66"


def canonical_bytes(value):
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode("utf-8")


def identity(value, field):
    return hashlib.sha256(
        canonical_bytes({key: item for key, item in value.items() if key != field})
    ).hexdigest()


class P7T4ResearchRemediationV7EvaluationPreparationTests(unittest.TestCase):
    def test_imported_candidate_is_exact_validated_candidate_only_output(self):
        manifest = json.loads(
            (EVIDENCE_ROOT / "adapter-manifest.json").read_text(encoding="utf-8")
        )
        evidence = json.loads(
            (EVIDENCE_ROOT / "real-training-evidence.json").read_text(encoding="utf-8")
        )
        metadata = json.loads(
            (EVIDENCE_ROOT / "training-metadata.json").read_text(encoding="utf-8")
        )
        sidecar = (
            EVIDENCE_ROOT / "p7-t2-research-remediation-v7-output.zip.sha256"
        ).read_text(encoding="utf-8")

        self.assertEqual(CANDIDATE_ID, manifest["candidateId"])
        self.assertEqual(CANDIDATE_ID, evidence["candidateId"])
        self.assertEqual(CANDIDATE_ID, metadata["candidateId"])
        self.assertEqual(TRAINING_RUN_IDENTITY, manifest["trainingRunIdentity"])
        self.assertEqual(TRAINING_RUN_IDENTITY, evidence["trainingRunIdentity"])
        self.assertEqual(TRAINING_RUN_IDENTITY, metadata["trainingRunIdentity"])
        self.assertEqual(DATASET_IDENTITY, metadata["datasetIdentity"])
        self.assertEqual(SOURCE_COMMIT, metadata["sourceCommit"])
        self.assertEqual("CANDIDATE_ONLY", manifest["adapterDisposition"])
        self.assertEqual("CANDIDATE_ONLY", metadata["adapterDisposition"])
        self.assertEqual(evidence["artifactIdentity"], identity(evidence, "artifactIdentity"))
        self.assertEqual(384, metadata["actualTraining"]["trainRecords"])
        self.assertEqual(64, metadata["actualTraining"]["validationRecords"])
        self.assertEqual(64, metadata["actualTraining"]["contractHoldoutRecords"])
        self.assertFalse(
            metadata["actualTraining"]["contractHoldoutUsedForOptimization"]
        )
        self.assertEqual(
            f"{ARCHIVE_SHA256}  p7-t2-research-remediation-v7-output.zip\n",
            sidecar,
        )

    def test_external_evaluation_request_remains_pending_and_fail_closed(self):
        request = json.loads(REQUEST_PATH.read_text(encoding="utf-8"))

        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertEqual(CANDIDATE_ID, request["candidate"]["candidateId"])
        self.assertEqual(
            TRAINING_RUN_IDENTITY, request["candidate"]["trainingRunIdentity"]
        )
        self.assertEqual(ARCHIVE_SHA256, request["candidate"]["archiveSha256"])
        self.assertTrue(
            request["requestedAuthorization"]["externalEvaluationExecutionAllowed"]
        )
        self.assertFalse(request["requestedAuthorization"]["promotionAllowed"])
        self.assertFalse(
            request["requestedAuthorization"]["productionPromptingAllowed"]
        )
        self.assertFalse(
            request["requestedAuthorization"]["runtimeNormalizationAllowed"]
        )
        self.assertFalse(
            request["requestedAuthorization"]["constrainedDecodingAllowed"]
        )
        self.assertEqual(
            request["requestIdentity"], identity(request, "requestIdentity")
        )

    def test_exact_user_approval_is_materialized_without_promotion_authority(self):
        request = json.loads(REQUEST_PATH.read_text(encoding="utf-8"))
        approval = json.loads(APPROVAL_PATH.read_text(encoding="utf-8"))

        self.assertEqual("APPROVED", approval["status"])
        self.assertEqual("APPROVED", approval["approval"]["decision"])
        self.assertEqual(request["requestIdentity"], approval["requestIdentity"])
        self.assertEqual(CANDIDATE_ID, approval["approvedCandidate"]["candidateId"])
        self.assertEqual(
            request["requestedAuthorization"], approval["authorization"]
        )
        self.assertFalse(approval["authorization"]["promotionAllowed"])
        self.assertEqual(
            approval["artifactIdentity"], identity(approval, "artifactIdentity")
        )

    def test_evaluation_config_binds_only_the_approved_v7_candidate(self):
        config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))

        self.assertEqual(CANDIDATE_ID, config["adapter"]["candidateId"])
        self.assertEqual(
            "evidence/p7-t4-research-remediation-v7-external-evaluation-approval.json",
            config["executionApproval"]["approvalReference"],
        )
        self.assertFalse(config["runtimeControls"]["constrainedDecodingAllowed"])
        self.assertFalse(config["runtimeControls"]["runtimeNormalizationAllowed"])


if __name__ == "__main__":
    unittest.main()
