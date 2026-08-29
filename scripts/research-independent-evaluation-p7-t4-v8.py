#!/usr/bin/env python3
"""Run P7-T4 Research evaluation for the approved remediation-v8 candidate."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys


THIS_PATH = Path(__file__).resolve()
V7_PATH = THIS_PATH.with_name("research-independent-evaluation-p7-t4-v7.py")
V8_APPROVAL_REFERENCE = (
    "evidence/p7-t4-research-remediation-v8-external-evaluation-approval.json"
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


V7 = _load_module("p7_t4_research_evaluation_v7_for_v8", V7_PATH)
V7.V6.V6_APPROVAL_REFERENCE = V8_APPROVAL_REFERENCE

for _name in dir(V7):
    if not _name.startswith("__"):
        globals()[_name] = getattr(V7, _name)


if __name__ == "__main__":
    raise SystemExit(V7.V6.BASE.main())
