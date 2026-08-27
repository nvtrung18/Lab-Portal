import hashlib
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVIDENCE_ROOT = ROOT / "evidence/p7-t2-real-training/remediation-v6"
REQUEST_PATH = (
    ROOT
    / "config/p7-t4-research-remediation-governance-v6/"
    "external-evaluation-approval-request.json"
)
APPROVAL_PATH = (
    ROOT / "evidence/p7-t4-research-remediation-v6-external-evaluation-approval.json"
)
CONFIG_PATH = ROOT / "config/p7-t4-research-independent-evaluation-remediation-v6.json"
CANDIDATE_ID = "1813b08c81e4ab2cb987367346941605fe98f9e5ff42ff877e3376b8e462f630"
TRAINING_RUN_IDENTITY = (
    "3a49fc0f9836eb4b687c55a466a852158cc77dcb3d5c69a5e16969ed89a7683f"
)
ARCHIVE_SHA256 = "4eef13a46695c54039de73e04d037925adbf25f1ff911a275cfe74fbc4be7d3d"
PROMPT_PROFILE_IDENTITY = (
    "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
)


def canonical_bytes(value):
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode("utf-8")


def identity(value, field):
    return hashlib.sha256(
        canonical_bytes({key: item for key, item in value.items() if key != field})
    ).hexdigest()


class P7T4ResearchRemediationV6EvaluationPreparationTests(unittest.TestCase):
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
            EVIDENCE_ROOT / "p7-t2-research-remediation-v6-output.zip.sha256"
        ).read_text(encoding="utf-8")

        self.assertEqual(CANDIDATE_ID, manifest["candidateId"])
        self.assertEqual(CANDIDATE_ID, evidence["candidateId"])
        self.assertEqual(CANDIDATE_ID, metadata["candidateId"])
        self.assertEqual(TRAINING_RUN_IDENTITY, manifest["trainingRunIdentity"])
        self.assertEqual(TRAINING_RUN_IDENTITY, evidence["trainingRunIdentity"])
        self.assertEqual("CANDIDATE_ONLY", manifest["adapterDisposition"])
        self.assertEqual("CANDIDATE_ONLY", metadata["adapterDisposition"])
        self.assertEqual(
            PROMPT_PROFILE_IDENTITY,
            metadata["contractGates"]["preparedRuntimeContract"][
                "promptProfileIdentity"
            ],
        )
        self.assertEqual(evidence["artifactIdentity"], identity(evidence, "artifactIdentity"))
        self.assertEqual(
            f"{ARCHIVE_SHA256}  p7-t2-research-remediation-v6-output.zip\n",
            sidecar,
        )

    def test_external_evaluation_request_is_approved_without_broadening_scope(self):
        request = json.loads(REQUEST_PATH.read_text(encoding="utf-8"))
        approval = json.loads(APPROVAL_PATH.read_text(encoding="utf-8"))

        self.assertEqual("PENDING_USER_APPROVAL", request["status"])
        self.assertEqual(CANDIDATE_ID, request["candidate"]["candidateId"])
        self.assertEqual(
            TRAINING_RUN_IDENTITY, request["candidate"]["trainingRunIdentity"]
        )
        self.assertEqual(
            PROMPT_PROFILE_IDENTITY, request["promptProfile"]["identity"]
        )
        self.assertTrue(
            request["requestedAuthorization"][
                "externalEvaluationExecutionAllowed"
            ]
        )
        self.assertTrue(
            request["requestedAuthorization"][
                "promptProfileV3EvaluationUseAllowed"
            ]
        )
        self.assertFalse(
            request["requestedAuthorization"]["productionPromptingAllowed"]
        )
        self.assertFalse(request["requestedAuthorization"]["promotionAllowed"])
        self.assertFalse(
            request["requestedAuthorization"]["runtimeNormalizationAllowed"]
        )
        self.assertFalse(
            request["requestedAuthorization"]["constrainedDecodingAllowed"]
        )
        self.assertEqual(
            request["requestIdentity"], identity(request, "requestIdentity")
        )
        self.assertEqual("APPROVED", approval["status"])
        self.assertEqual("APPROVED", approval["approval"]["decision"])
        self.assertEqual(request["requestIdentity"], approval["requestIdentity"])
        self.assertEqual(CANDIDATE_ID, approval["approvedCandidate"]["candidateId"])
        self.assertEqual(
            PROMPT_PROFILE_IDENTITY, approval["approvedPromptProfile"]["identity"]
        )
        self.assertEqual(
            request["requestedAuthorization"], approval["authorization"]
        )
        self.assertEqual(
            approval["artifactIdentity"], identity(approval, "artifactIdentity")
        )

    def test_evaluation_config_binds_v6_candidate_and_prompt_profile(self):
        config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))

        self.assertEqual(CANDIDATE_ID, config["adapter"]["candidateId"])
        self.assertEqual(
            "config/p7-t4-research-remediation-governance-v6/"
            "research-prompt-profile-v3.approved.json",
            config["execution"]["promptProfileReference"],
        )
        self.assertEqual(
            "evidence/p7-t4-research-remediation-v6-external-evaluation-approval.json",
            config["executionApproval"]["approvalReference"],
        )
        self.assertFalse(config["runtimeControls"]["constrainedDecodingAllowed"])
        self.assertFalse(config["runtimeControls"]["runtimeNormalizationAllowed"])


if __name__ == "__main__":
    unittest.main()
