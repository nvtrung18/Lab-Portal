#!/usr/bin/env python3
"""Run P7-T4 Research evaluation for the approved remediation-v9 candidate."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys


THIS_PATH = Path(__file__).resolve()
V8_PATH = THIS_PATH.with_name("research-independent-evaluation-p7-t4-v8.py")
V9_APPROVAL_REFERENCE = (
    "evidence/p7-t4-research-remediation-v9-external-evaluation-approval.json"
)


def _load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise RuntimeError(f"cannot load {path.name}")
    module = importlib.util.module_from_spec(specification)
    previous = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        specification.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


V8 = _load_module("p7_t4_research_evaluation_v8_for_v9", V8_PATH)
V8.V7.V6.V6_APPROVAL_REFERENCE = V9_APPROVAL_REFERENCE

for _name in dir(V8):
    if not _name.startswith("__"):
        globals()[_name] = getattr(V8, _name)


if __name__ == "__main__":
    raise SystemExit(V8.V7.V6.BASE.main())
