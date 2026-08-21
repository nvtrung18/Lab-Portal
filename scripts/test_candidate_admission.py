#!/usr/bin/env python3
from __future__ import annotations
import importlib.util, unittest
from pathlib import Path

_SCRIPT = Path(__file__).resolve().parent / "benchmark-p6-t5.py"
_spec = importlib.util.spec_from_file_location("bm", _SCRIPT)
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
admit_candidates = _mod.admit_candidates
compare_candidates = _mod.compare_candidates
BenchmarkError = _mod.BenchmarkError


def _verified(cid):
    return {"id": cid, "repository": f"Org/{cid}", "revision": "abc123",
            "tokenizerRevision": "abc123", "license": "Apache-2.0",
            "licenseSha256": "a" * 64, "fileInventorySha256": "b" * 64,
            "provenanceState": "VERIFIED"}


def _pending(cid):
    return {"id": cid, "repository": f"Org/{cid}", "revision": "abc123",
            "provenanceState": "PENDING_AUTHORITATIVE_EVIDENCE"}


class TestAdmitCandidates(unittest.TestCase):

    def test_A_two_verified_one_pending_selects_only_verified(self):
        candidates = [_verified("q25"), _verified("q31"), _pending("q34")]
        runnable, non_admitted = admit_candidates(candidates)
        self.assertEqual([c["id"] for c in runnable], ["q25", "q31"])
        self.assertEqual(len(non_admitted), 1)
        self.assertEqual(non_admitted[0]["candidateId"], "q34")

    def test_B_pending_candidate_is_reported_as_not_admitted(self):
        candidates = [_verified("q25"), _verified("q31"), _pending("q34")]
        _, non_admitted = admit_candidates(candidates)
        rec = non_admitted[0]
        self.assertEqual(rec["admissionState"], "NOT_ADMITTED")
        self.assertEqual(rec["reason"], "PROVENANCE_NOT_VERIFIED")
        self.assertEqual(rec["provenanceState"], "PENDING_AUTHORITATIVE_EVIDENCE")
        self.assertEqual(rec["candidateId"], "q34")

    def test_C_verified_missing_field_fails_closed(self):
        bad = _verified("bad")
        del bad["fileInventorySha256"]
        candidates = [bad, _verified("q31")]
        with self.assertRaises(BenchmarkError) as ctx:
            admit_candidates(candidates)
        self.assertIn("PROVENANCE_UNVERIFIED", str(ctx.exception))

    def test_D_only_one_verified_means_insufficient(self):
        candidates = [_verified("q25"), _pending("q34")]
        runnable, _ = admit_candidates(candidates)
        self.assertEqual(len(runnable), 1)
        # The INSUFFICIENT check is enforced by main() not admit_candidates itself
        # but we can verify the count is correct
        self.assertLess(len(runnable), 2)

    def test_E_all_three_verified_all_runnable(self):
        candidates = [_verified("q25"), _verified("q31"), _verified("q34")]
        runnable, non_admitted = admit_candidates(candidates)
        self.assertEqual(len(runnable), 3)
        self.assertEqual(non_admitted, [])

    def test_B2_pending_record_has_repository_and_revision(self):
        candidates = [_verified("q25"), _verified("q31"), _pending("q34")]
        _, non_admitted = admit_candidates(candidates)
        rec = non_admitted[0]
        self.assertEqual(rec["repository"], "Org/q34")
        self.assertEqual(rec["revision"], "abc123")

    def test_all_pending_produces_zero_runnable(self):
        candidates = [_pending("a"), _pending("b")]
        runnable, non_admitted = admit_candidates(candidates)
        self.assertEqual(runnable, [])
        self.assertEqual(len(non_admitted), 2)

    def test_empty_produces_empty(self):
        runnable, non_admitted = admit_candidates([])
        self.assertEqual(runnable, [])
        self.assertEqual(non_admitted, [])

    def test_verified_with_wrong_license_fails_closed(self):
        bad = _verified("bad")
        bad["license"] = "MIT"
        with self.assertRaises(BenchmarkError) as ctx:
            admit_candidates([bad])
        self.assertIn("PROVENANCE_UNVERIFIED", str(ctx.exception))


class TestCompareCandidatesNonAdmitted(unittest.TestCase):

    def _make_run_result(self, cid):
        return {"candidateId": cid, "candidateMetadata": _verified(cid),
                "runs": [], "human": {}}

    def test_F_recommendation_requires_two_eligible_completed(self):
        results = [self._make_run_result("q25")]
        comparison = compare_candidates(results)
        # With only one candidate, recommend() returns NOT_READY_FOR_PR
        self.assertIn(comparison["recommendation"]["state"],
                      ["NOT_READY_FOR_PR", "NO_RECOMMENDATION_TIE", "RECOMMENDED_SHARED_BASE_CANDIDATE"])
        # Specifically with 1 eligible candidate: NOT_READY_FOR_PR
        self.assertEqual(comparison["recommendation"]["state"], "NOT_READY_FOR_PR")

    def test_non_admitted_list_present_in_comparison_output(self):
        results = [self._make_run_result("q25"), self._make_run_result("q31")]
        na = [{"candidateId": "q34", "admissionState": "NOT_ADMITTED",
               "reason": "PROVENANCE_NOT_VERIFIED", "provenanceState": "PENDING_AUTHORITATIVE_EVIDENCE",
               "repository": "Org/q34", "revision": "abc"}]
        comparison = compare_candidates(results, non_admitted=na)
        self.assertEqual(comparison["nonAdmittedCandidates"], na)

    def test_non_admitted_default_is_empty_list(self):
        results = [self._make_run_result("q25"), self._make_run_result("q31")]
        comparison = compare_candidates(results)
        self.assertEqual(comparison["nonAdmittedCandidates"], [])

    def test_qwen3_4b_from_config_is_non_admitted(self):
        import yaml
        p = Path(__file__).resolve().parents[1] / "config" / "p6-t5-benchmark-kaggle-linux-cp312.yaml"
        config = yaml.safe_load(p.read_text(encoding="utf-8"))
        candidates = config.get("candidates", [])
        runnable, non_admitted = admit_candidates(candidates)
        runnable_ids = [c["id"] for c in runnable]
        non_admitted_ids = [r["candidateId"] for r in non_admitted]
        self.assertIn("qwen25_1_5b", runnable_ids)
        self.assertIn("qwen3_1_7b", runnable_ids)
        self.assertIn("qwen3_4b", non_admitted_ids)
        self.assertNotIn("qwen3_4b", runnable_ids)
        q34_rec = next(r for r in non_admitted if r["candidateId"] == "qwen3_4b")
        self.assertEqual(q34_rec["admissionState"], "NOT_ADMITTED")
        self.assertEqual(q34_rec["reason"], "PROVENANCE_NOT_VERIFIED")


if __name__ == "__main__":
    unittest.main(verbosity=2)
