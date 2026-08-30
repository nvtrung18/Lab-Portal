import hashlib
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVIDENCE_ROOT = ROOT / "evidence/p7-t2-real-training/remediation-v5"
CONFIG_PATH = ROOT / "config/p7-t4-research-independent-evaluation-remediation-v5.json"
REQUEST_PATH = (
    ROOT
    / "config/p7-t4-research-remediation-governance-v5/external-evaluation-approval-request.json"
)
APPROVAL_PATH = (
    ROOT / "evidence/p7-t4-research-remediation-v5-external-evaluation-approval.json"
)
CANDIDATE_ID = "714270ddeb336a0be24caffcd8fe609c464e90d232d5e96fb9dc5fa9a5e8e114"
TRAINING_RUN_IDENTITY = "06633cfd985acca95c8057c3ac128e2274759f8b0f1f08679e3564c3dcfab429"
ARCHIVE_SHA256 = "373c7f162afcdda903eada55fd0837a11cc5771a98145986ba4f94263e577e0d"
EVALUATOR_IDENTITY = "99230c674b9064f1e06247dedd014f6e3da0714ca679017c07b3d877f1e285d3"
SUITE_IDENTITY = "65c87149ec97bf34a04257a80af0cba1114b48fe9702f6b3cacb253b573931a8"


def canonical_bytes(value):
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode("utf-8")


def identity(value, field):
    return hashlib.sha256(
        canonical_bytes({key: item for key, item in value.items() if key != field})
    ).hexdigest()


class P7T4ResearchRemediationV5EvaluationPreparationTests(unittest.TestCase):
    def test_imported_candidate_evidence_is_exact_and_candidate_only(self):
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
            EVIDENCE_ROOT / "p7-t2-research-remediation-v5-output.zip.sha256"
        ).read_text(encoding="utf-8")

        self.assertEqual(CANDIDATE_ID, manifest["candidateId"])
        self.assertEqual(CANDIDATE_ID, evidence["candidateId"])
        self.assertEqual(CANDIDATE_ID, metadata["candidateId"])
        self.assertEqual(TRAINING_RUN_IDENTITY, manifest["trainingRunIdentity"])
        self.assertEqual(TRAINING_RUN_IDENTITY, evidence["trainingRunIdentity"])
        self.assertEqual("CANDIDATE_ONLY", manifest["adapterDisposition"])
        self.assertEqual("CANDIDATE_ONLY", metadata["adapterDisposition"])
        self.assertEqual(evidence["artifactIdentity"], identity(evidence, "artifactIdentity"))
        self.assertEqual(
            f"{ARCHIVE_SHA256}  p7-t2-research-remediation-v5-output.zip\n",
            sidecar,
        )

    def test_evaluation_config_binds_candidate_and_approved_v2_contracts(self):
        config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))

        self.assertEqual(CANDIDATE_ID, config["adapter"]["candidateId"])
        self.assertEqual("2.0.0", config["evaluationContract"]["evaluatorVersion"])
        self.assertEqual(EVALUATOR_IDENTITY, config["evaluationContract"]["evaluatorIdentity"])
        self.assertEqual("2.0.0", config["evaluationContract"]["suiteVersion"])
        self.assertEqual(SUITE_IDENTITY, config["evaluationContract"]["suiteIdentity"])
        self.assertFalse(config["runtimeControls"]["constrainedDecodingAllowed"])
        self.assertFalse(config["runtimeControls"]["runtimeNormalizationAllowed"])

    def test_external_evaluation_request_is_bound_and_does_not_allow_promotion(self):
        request = json.loads(REQUEST_PATH.read_text(encoding="utf-8"))

        self.assertEqual(CANDIDATE_ID, request["candidate"]["candidateId"])
        self.assertEqual(TRAINING_RUN_IDENTITY, request["candidate"]["trainingRunIdentity"])
        self.assertEqual(EVALUATOR_IDENTITY, request["evaluationContract"]["evaluatorIdentity"])
        self.assertEqual(SUITE_IDENTITY, request["evaluationContract"]["suiteIdentity"])
        self.assertTrue(request["requestedAuthorization"]["externalEvaluationExecutionAllowed"])
        self.assertFalse(request["requestedAuthorization"]["promotionAllowed"])
        self.assertFalse(request["requestedAuthorization"]["runtimeNormalizationAllowed"])
        self.assertFalse(request["requestedAuthorization"]["constrainedDecodingAllowed"])
        self.assertEqual(request["requestIdentity"], identity(request, "requestIdentity"))

    def test_external_evaluation_approval_matches_the_exact_request(self):
        request = json.loads(REQUEST_PATH.read_text(encoding="utf-8"))
        approval = json.loads(APPROVAL_PATH.read_text(encoding="utf-8"))

        self.assertEqual("APPROVED", approval["status"])
        self.assertEqual("APPROVED", approval["approval"]["decision"])
        self.assertEqual(request["requestIdentity"], approval["requestIdentity"])
        self.assertEqual(CANDIDATE_ID, approval["approvedCandidate"]["candidateId"])
        self.assertEqual(
            request["requestedAuthorization"], approval["authorization"]
        )
        self.assertTrue(approval["authorization"]["externalEvaluationExecutionAllowed"])
        self.assertFalse(approval["authorization"]["promotionAllowed"])
        self.assertEqual(
            approval["artifactIdentity"], identity(approval, "artifactIdentity")
        )


if __name__ == "__main__":
    unittest.main()
