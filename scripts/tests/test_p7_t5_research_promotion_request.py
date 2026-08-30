import importlib.util
import json
import unittest
from copy import deepcopy
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t5-research-promotion-request.py"
CONFIG_ROOT = ROOT / "config" / "p7-t5-research-promotion"


def load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise AssertionError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


class P7T5ResearchPromotionRequestTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("p7_t5_research_promotion_request", BUILDER_PATH)

    def test_checked_in_request_artifacts_reproduce_byte_for_byte(self):
        artifacts = self.builder.build_artifacts()

        self.assertEqual(
            {
                "config/p7-t5-research-promotion/model-manifest.pending.json",
                "config/p7-t5-research-promotion/model-registry-rules.json",
                "config/p7-t5-research-promotion/promotion-request.json",
                "config/p7-t5-research-promotion/rollback-manifest.pending.json",
            },
            set(artifacts),
        )
        for relative_path, content in artifacts.items():
            self.assertEqual(content, (ROOT / relative_path).read_bytes())

    def test_request_is_bound_to_the_completed_p7_t4_decision(self):
        documents = self.builder.build_documents()
        request = documents["promotion-request.json"]

        self.assertEqual("PENDING_APPROVER_GATE", request["status"])
        self.assertEqual("RESEARCH_ASSISTANT", request["assistantKey"])
        self.assertEqual(
            "3cd324ad97530ce535073be761e1713cb627e15d55cb9395d55729cd93595de4",
            request["candidateId"],
        )
        self.assertEqual("PASS", request["evaluationBinding"]["decision"])
        self.assertTrue(request["evaluationBinding"]["promotionAllowed"])
        self.assertEqual([], request["evaluationBinding"]["unresolvedCaseIds"])
        self.assertEqual(
            self.builder.request_identity(request), request["requestIdentity"]
        )

    def test_pending_manifest_contains_every_plan_required_metadata_field(self):
        manifest = self.builder.build_documents()["model-manifest.pending.json"]

        self.assertEqual("PENDING_APPROVAL", manifest["approvalStatus"])
        self.assertEqual("RESEARCH_ASSISTANT", manifest["assistantKey"])
        self.assertEqual("ADAPTER", manifest["artifactType"])
        self.assertEqual("Apache-2.0", manifest["license"]["identifier"])
        self.assertEqual("VERIFIED", manifest["license"]["status"])
        self.assertEqual("10.0.0", manifest["dataset"]["version"])
        self.assertEqual("10.0.0", manifest["trainingConfig"]["version"])
        self.assertEqual("PASS", manifest["evaluation"]["decision"])
        self.assertEqual("Tesla T4", manifest["servingRuntime"]["evaluatedGpu"])
        self.assertGreater(
            manifest["servingRuntime"]["resourceEstimate"]["peakVramBytes"], 0
        )
        self.assertEqual(9, len(manifest["artifact"]["files"]))
        self.assertEqual(
            sorted(entry["path"] for entry in manifest["artifact"]["files"]),
            [entry["path"] for entry in manifest["artifact"]["files"]],
        )

    def test_rollback_is_fail_closed_and_does_not_claim_an_old_approved_adapter(self):
        rollback = self.builder.build_documents()["rollback-manifest.pending.json"]

        self.assertEqual("DISABLE_ASSISTANT", rollback["action"])
        self.assertEqual("BLOCKED", rollback["restoredAdapterStatus"])
        self.assertIsNone(rollback["restoredAdapterIdentity"])
        self.assertFalse(rollback["servingAllowed"])

    def test_registry_rules_are_reusable_and_require_immutable_approval_evidence(self):
        rules = self.builder.build_documents()["model-registry-rules.json"]

        self.assertEqual(
            ["RESEARCH_ASSISTANT", "LAB_ASSISTANT", "ADMIN_ASSISTANT"],
            rules["supportedAssistantKeys"],
        )
        self.assertTrue(rules["requirements"]["immutableArtifacts"])
        self.assertTrue(rules["requirements"]["checksumValidation"])
        self.assertTrue(rules["requirements"]["independentEvaluation"])
        self.assertTrue(rules["requirements"]["rollbackRequired"])
        self.assertEqual(
            ["CANDIDATE", "APPROVED", "REJECTED", "ROLLED_BACK"],
            rules["allowedLifecycleStates"],
        )

    def test_tampered_or_incomplete_p7_t4_evidence_fails_closed(self):
        summary = json.loads(self.builder.P7_T4_SUMMARY_PATH.read_text(encoding="utf-8"))
        decision = json.loads(self.builder.P7_T4_DECISION_PATH.read_text(encoding="utf-8"))
        adapter = json.loads(self.builder.ADAPTER_MANIFEST_PATH.read_text(encoding="utf-8"))

        tampered = deepcopy(summary)
        tampered["promotionAllowed"] = False
        with self.assertRaisesRegex(self.builder.PromotionRequestError, "P7-T4"):
            self.builder.build_documents(summary=tampered, decision=decision, adapter=adapter)

        mismatched = deepcopy(adapter)
        mismatched["candidateId"] = "0" * 64
        with self.assertRaisesRegex(self.builder.PromotionRequestError, "candidate"):
            self.builder.build_documents(summary=summary, decision=decision, adapter=mismatched)


if __name__ == "__main__":
    unittest.main()
