#!/usr/bin/env python3
from __future__ import annotations
import hashlib, importlib.util, tempfile, unittest
from pathlib import Path

_SCRIPT = Path(__file__).resolve().parent / "benchmark-p6-t5.py"
_spec = importlib.util.spec_from_file_location("benchmark_p6_t5", _SCRIPT)
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
model_payload_inventory = _mod.model_payload_inventory
verify_local_snapshot = _mod.verify_local_snapshot
BenchmarkError = _mod.BenchmarkError
digest = _mod.digest

def _sha256(b): return __import__("hashlib").sha256(b).hexdigest()

def _snap(root):
    (root / "LICENSE").write_bytes(b"Apache-2.0 license text")
    (root / "config.json").write_bytes(b"{\"model_type\": \"qwen2\"}")
    (root / "model.safetensors").write_bytes(b"\x00" * 64)
    sub = root / "tokenizer"; sub.mkdir()
    (sub / "tokenizer.json").write_bytes(b"{\"version\": \"1.0\"}")

def _cand(root):
    inv = model_payload_inventory(root)
    ls = _sha256((root / "LICENSE").read_bytes())
    return {"provenanceState": "VERIFIED", "repository": "T/M",
            "revision": "abc", "tokenizerRevision": "abc",
            "license": "Apache-2.0", "licenseSha256": ls,
            "fileInventorySha256": digest(inv)}

class TestInventory(unittest.TestCase):
    def dig(self, r): return digest(model_payload_inventory(r))

    def test_A_hf_metadata_same_digest(self):
        with tempfile.TemporaryDirectory() as t:
            r = Path(t); _snap(r); b = self.dig(r)
            dl = r / ".cache" / "huggingface" / "download"; dl.mkdir(parents=True)
            m = dl / "a.metadata"; m.write_bytes(b"ts=2024")
            self.assertEqual(b, self.dig(r))
            m.write_bytes(b"ts=2026"); self.assertEqual(b, self.dig(r))

    def test_B_hf_lock_same_digest(self):
        with tempfile.TemporaryDirectory() as t:
            r = Path(t); _snap(r); b = self.dig(r)
            dl = r / ".cache" / "huggingface" / "download"; dl.mkdir(parents=True)
            lk = dl / "a.lock"; lk.write_bytes(b"LOCK")
            self.assertEqual(b, self.dig(r))
            lk.unlink(); self.assertEqual(b, self.dig(r))

    def test_C_payload_mod_fails(self):
        with tempfile.TemporaryDirectory() as t:
            r = Path(t); _snap(r); c = _cand(r)
            (r / "model.safetensors").write_bytes(b"\xff" * 64)
            with self.assertRaises(BenchmarkError) as x: verify_local_snapshot(c, r)
            self.assertIn("model-file inventory", str(x.exception))

    def test_D_payload_add_fails(self):
        with tempfile.TemporaryDirectory() as t:
            r = Path(t); _snap(r); c = _cand(r)
            (r / "extra.bin").write_bytes(b"extra")
            with self.assertRaises(BenchmarkError) as x: verify_local_snapshot(c, r)
            self.assertIn("model-file inventory", str(x.exception))

    def test_E_payload_del_fails(self):
        with tempfile.TemporaryDirectory() as t:
            r = Path(t); _snap(r); c = _cand(r)
            (r / "config.json").unlink()
            with self.assertRaises(BenchmarkError) as x: verify_local_snapshot(c, r)
            self.assertIn("model-file inventory", str(x.exception))

    def test_F_license_mod_fails(self):
        with tempfile.TemporaryDirectory() as t:
            r = Path(t); _snap(r); c = _cand(r)
            (r / "LICENSE").write_bytes(b"MIT-WRONG")
            with self.assertRaises(BenchmarkError) as x: verify_local_snapshot(c, r)
            self.assertIn("LICENSE digest", str(x.exception))

    def test_G_deterministic_order(self):
        with tempfile.TemporaryDirectory() as t:
            r = Path(t); _snap(r)
            i1 = model_payload_inventory(r); i2 = model_payload_inventory(r)
            self.assertEqual(i1, i2)
            paths = [x["path"] for x in i1]
            self.assertEqual(paths, sorted(paths))

    def test_hf_nested_excluded(self):
        with tempfile.TemporaryDirectory() as t:
            r = Path(t); _snap(r); b = self.dig(r)
            hf = r / ".cache" / "huggingface"
            (hf / "download" / "sub").mkdir(parents=True)
            (hf / "download" / "sub" / "deep.meta").write_bytes(b"d")
            (hf / "x.lock").write_bytes(b"l")
            self.assertEqual(b, self.dig(r))

    def test_valid_passes(self):
        with tempfile.TemporaryDirectory() as t:
            r = Path(t); _snap(r); verify_local_snapshot(_cand(r), r)

    def test_hf_present_still_passes(self):
        with tempfile.TemporaryDirectory() as t:
            r = Path(t); _snap(r); c = _cand(r)
            dl = r / ".cache" / "huggingface" / "download"; dl.mkdir(parents=True)
            (dl / "m.metadata").write_bytes(b"ts=2026")
            (dl / "m.lock").write_bytes(b"L")
            verify_local_snapshot(c, r)

    def test_posix_paths(self):
        with tempfile.TemporaryDirectory() as t:
            r = Path(t); _snap(r)
            for rec in model_payload_inventory(r):
                self.assertNotIn("\\", rec["path"])

    def test_missing_license_fails(self):
        with tempfile.TemporaryDirectory() as t:
            r = Path(t); _snap(r); c = _cand(r)
            (r / "LICENSE").unlink()
            with self.assertRaises(BenchmarkError) as x: verify_local_snapshot(c, r)
            self.assertIn("LICENSE digest", str(x.exception))

class TestConfigHashes(unittest.TestCase):
    def cfg(self):
        import yaml
        p = Path(__file__).resolve().parents[1] / "config" / "p6-t5-benchmark-kaggle-linux-cp312.yaml"
        return yaml.safe_load(p.read_text(encoding="utf-8"))
    def cand(self, cid):
        for c in self.cfg().get("candidates", []):
            if c.get("id") == cid: return c
        raise AssertionError(cid)

    def test_qwen25_1_5b_verified(self): self.assertEqual(self.cand("qwen25_1_5b")["provenanceState"], "VERIFIED")
    def test_qwen25_1_5b_inv_sha(self): self.assertEqual(self.cand("qwen25_1_5b")["fileInventorySha256"], "503823a81fca3c0f6a18b7c443c31a36ef9be4deb84ae432a569f4358e69eff9")
    def test_qwen25_1_5b_lic_sha(self): self.assertEqual(self.cand("qwen25_1_5b")["licenseSha256"], "832dd9e00a68dd83b3c3fb9f5588dad7dcf337a0db50f7d9483f310cd292e92e")
    def test_qwen3_1_7b_verified(self): self.assertEqual(self.cand("qwen3_1_7b")["provenanceState"], "VERIFIED")
    def test_qwen3_1_7b_inv_sha(self): self.assertEqual(self.cand("qwen3_1_7b")["fileInventorySha256"], "347a7ae9619340a2888343a08d7852149ecaab10545b830c6be3ddb6cf2bfe55")
    def test_qwen3_1_7b_lic_sha(self): self.assertEqual(self.cand("qwen3_1_7b")["licenseSha256"], "832dd9e00a68dd83b3c3fb9f5588dad7dcf337a0db50f7d9483f310cd292e92e")
    def test_qwen3_4b_still_pending(self):
        c = self.cand("qwen3_4b")
        self.assertEqual(c["provenanceState"], "PENDING_AUTHORITATIVE_EVIDENCE")
        self.assertNotIn("fileInventorySha256", c)
    def test_revisions_unchanged(self):
        q25 = self.cand("qwen25_1_5b")
        self.assertEqual(q25["repository"], "Qwen/Qwen2.5-1.5B-Instruct")
        self.assertEqual(q25["revision"], "989aa7980e4cf806f80c7fef2b1adb7bc71aa306")
        q31 = self.cand("qwen3_1_7b")
        self.assertEqual(q31["repository"], "Qwen/Qwen3-1.7B")
        self.assertEqual(q31["revision"], "70d244cc86ccca08cf5af4e1e306ecf908b1ad5e")

if __name__ == "__main__": unittest.main(verbosity=2)
