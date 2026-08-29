import hashlib
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVIDENCE_ROOT = ROOT / "evidence/p7-t2-real-training/remediation-v8"
REQUEST_PATH = (
    ROOT
    / "config/p7-t4-research-remediation-governance-v8/"
    "external-evaluation-approval-request.json"
)
CONFIG_PATH = ROOT / "config/p7-t4-research-independent-evaluation-remediation-v8.json"
APPROVAL_PATH = (
    ROOT / "evidence/p7-t4-research-remediation-v8-external-evaluation-approval.json"
)
CANDIDATE_ID = "cb8c3e4addd20d6de84ed2c135a41baa2841666e631629617dceb3445db04403"
TRAINING_RUN_IDENTITY = (
    "d5cacf3fdd4aceafcd1825a2aa0b6021a4516ee9d2bccdef55388ccc7cd9d5d8"
)
ARCHIVE_SHA256 = "9903ae132ecc300f5cfd4a926a46cb2adacb19797df9ab5392a999eeef8c8ded"
DATASET_IDENTITY = "44fb5a05d9a13c4f6e8d39fe7b8fe5a9189dc077b9850da700a17ea76e609b3d"
SOURCE_COMMIT = "356d82646fdc05ddf7a732165a928ee5693d0a08"


def canonical_bytes(value):
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode("utf-8")


def identity(value, field):
    return hashlib.sha256(
        canonical_bytes({key: item for key, item in value.items() if key != field})
    ).hexdigest()


class P7T4ResearchRemediationV8EvaluationPreparationTests(unittest.TestCase):
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
            EVIDENCE_ROOT / "p7-t2-research-remediation-v8-output.zip.sha256"
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
        self.assertEqual(432, metadata["actualTraining"]["trainRecords"])
        self.assertEqual(72, metadata["actualTraining"]["validationRecords"])
        self.assertEqual(72, metadata["actualTraining"]["contractHoldoutRecords"])
        self.assertFalse(
            metadata["actualTraining"]["contractHoldoutUsedForOptimization"]
        )
        self.assertEqual(
            f"{ARCHIVE_SHA256}  p7-t2-research-remediation-v8-output.zip\n",
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

    def test_evaluation_config_binds_only_v8_and_requires_separate_approval(self):
        config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))

        self.assertEqual(CANDIDATE_ID, config["adapter"]["candidateId"])
        self.assertEqual(
            "37f8add41ad20c6f40586d714cad77824d10cab887dd5e2f237999b1015aa232",
            config["adapter"]["adapterIdentity"],
        )
        self.assertEqual("CANDIDATE_ONLY", config["adapter"]["disposition"])
        self.assertEqual(
            "evidence/p7-t4-research-remediation-v8-external-evaluation-approval.json",
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
        self.assertEqual(request["requestIdentity"], approval["requestIdentity"])
        self.assertEqual(CANDIDATE_ID, approval["approvedCandidate"]["candidateId"])
        self.assertEqual(
            request["requestedAuthorization"], approval["authorization"]
        )
        self.assertFalse(approval["authorization"]["promotionAllowed"])
        self.assertEqual(
            approval["artifactIdentity"], identity(approval, "artifactIdentity")
        )


if __name__ == "__main__":
    unittest.main()
