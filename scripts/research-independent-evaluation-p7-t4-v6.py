#!/usr/bin/env python3
"""Run P7-T4 Research evaluation with the approved v6 prompt profile."""
from __future__ import annotations

import copy
import importlib.util
from pathlib import Path
import sys
from typing import Any


THIS_PATH = Path(__file__).resolve()
BASE_PATH = (
    THIS_PATH.with_name("research-independent-evaluation-p7-t4.py")
    if THIS_PATH.name == "research-independent-evaluation-p7-t4-v6.py"
    else THIS_PATH.with_name("research-independent-evaluation-p7-t4-base.py")
)
V6_PROMPT_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v6/"
    "research-prompt-profile-v3.approved.json"
)
V6_APPROVAL_REFERENCE = (
    "evidence/p7-t4-research-remediation-v6-external-evaluation-approval.json"
)
V5_APPROVAL_REFERENCE = (
    "evidence/p7-t4-research-remediation-v5-external-evaluation-approval.json"
)
EXPECTED_AUTHORIZATION = {
    "constrainedDecodingAllowed": False,
    "externalEvaluationExecutionAllowed": True,
    "productionPromptingAllowed": False,
    "promptProfileV3EvaluationUseAllowed": True,
    "promotionAllowed": False,
    "runtimeNormalizationAllowed": False,
}


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


BASE = _load_module("p7_t4_research_evaluation_base_for_v6", BASE_PATH)
BASE_VALIDATE_EVALUATION_CONFIG = BASE.validate_evaluation_config


def validate_evaluation_config(
    config: dict[str, Any], adapter_manifest: dict[str, Any]
) -> None:
    """Validate the v6 bindings without widening the frozen base runner."""
    normalized = copy.deepcopy(config)
    execution = normalized.get("execution")
    approval = normalized.get("executionApproval")
    if not isinstance(execution, dict) or not isinstance(approval, dict):
        raise BASE.P7T4Error("evaluation v6 execution bindings invalid")
    if (
        execution.get("promptProfileReference") != V6_PROMPT_REFERENCE
        or approval != {"approvalReference": V6_APPROVAL_REFERENCE, "required": True}
    ):
        raise BASE.P7T4Error("evaluation v6 prompt or approval binding invalid")
    execution["promptProfileReference"] = (
        "config/p6-t5-benchmark-kaggle-linux-cp312.yaml"
    )
    approval["approvalReference"] = V5_APPROVAL_REFERENCE
    BASE_VALIDATE_EVALUATION_CONFIG(normalized, adapter_manifest)


def load_v2_evaluation_contract(
    root: Path,
    config: dict[str, Any],
    base_lock: dict[str, Any],
    gap_suite: dict[str, Any],
) -> tuple[dict[str, Any], object, dict[str, Any]]:
    """Load evaluator/suite v2 plus the separately approved v6 prompt profile."""
    contract = config["evaluationContract"]
    evaluator_contract = BASE._load_json(root / contract["evaluatorReference"])
    suite = BASE._load_json(root / contract["suiteReference"])
    amendment_approval = BASE._load_json(root / contract["approvalReference"])
    execution_approval = BASE._load_json(root / V6_APPROVAL_REFERENCE)
    execution_request = BASE._load_json(root / execution_approval["requestReference"])
    prompt_profile = BASE._load_json(root / V6_PROMPT_REFERENCE)

    if (
        evaluator_contract.get("artifactIdentity")
        != BASE.artifact_identity(evaluator_contract)
        or evaluator_contract.get("artifactIdentity") != contract["evaluatorIdentity"]
        or evaluator_contract.get("evaluatorVersion") != "2.0.0"
        or evaluator_contract.get("status") != "APPROVED"
        or evaluator_contract.get("useAllowed") is not True
        or evaluator_contract.get("runtimeNormalizationAllowed") is not False
        or evaluator_contract.get("constrainedDecodingAllowed") is not False
    ):
        raise BASE.P7T4Error("approved evaluator v2 contract invalid")
    if (
        suite.get("suiteDigest") != BASE._suite_identity(suite)
        or suite.get("suiteDigest") != contract["suiteIdentity"]
        or suite.get("suiteVersion") != "2.0.0"
        or suite.get("status") != "APPROVED"
        or suite.get("activationAllowed") is not True
        or suite.get("externalExecutionAllowed") is not False
        or suite.get("EVALUATION_ONLY") is not True
        or suite.get("TRAINING_PROHIBITED") is not True
        or suite.get("sourceSuites", {}).get("base", {}).get("digest")
        != base_lock.get("suiteDigest")
        or suite.get("sourceSuites", {}).get("gap", {}).get("digest")
        != gap_suite.get("suiteDigest")
    ):
        raise BASE.P7T4Error("approved evaluation suite v2 invalid")

    approved_artifacts = amendment_approval.get("approvedArtifacts", {})
    amendment_authorization = amendment_approval.get("authorization", {})
    if (
        amendment_approval.get("artifactIdentity")
        != BASE.artifact_identity(amendment_approval)
        or amendment_approval.get("artifactIdentity") != contract["approvalIdentity"]
        or amendment_approval.get("status") != "APPROVED"
        or approved_artifacts.get("evaluatorIdentity") != contract["evaluatorIdentity"]
        or approved_artifacts.get("suiteIdentity") != contract["suiteIdentity"]
        or amendment_authorization.get("evaluatorV2UseAllowed") is not True
        or amendment_authorization.get("suiteV2UseAllowed") is not True
        or amendment_authorization.get("externalEvaluationExecutionAllowed") is not False
    ):
        raise BASE.P7T4Error("evaluator and suite amendment approval invalid")

    request_profile = execution_request.get("promptProfile")
    approved_profile = execution_approval.get("approvedPromptProfile")
    expected_profile = {
        "evaluationUseAllowed": True,
        "identity": prompt_profile.get("artifactIdentity"),
        "productionActivationAllowed": False,
        "reference": V6_PROMPT_REFERENCE,
        "version": "3.0.0",
    }
    if (
        execution_request.get("requestIdentity")
        != BASE.request_identity(execution_request)
        or execution_approval.get("artifactIdentity")
        != BASE.artifact_identity(execution_approval)
        or execution_approval.get("requestIdentity")
        != execution_request["requestIdentity"]
        or execution_approval.get("status") != "APPROVED"
        or execution_approval.get("approval", {}).get("decision") != "APPROVED"
        or execution_approval.get("approvedCandidate", {}).get("candidateId")
        != config["adapter"]["candidateId"]
        or execution_approval.get("approvedEvaluationContract", {}).get(
            "evaluatorIdentity"
        )
        != contract["evaluatorIdentity"]
        or execution_approval.get("approvedEvaluationContract", {}).get("suiteIdentity")
        != contract["suiteIdentity"]
        or execution_request.get("candidate", {}).get("candidateId")
        != config["adapter"]["candidateId"]
        or execution_request.get("requestedAuthorization") != EXPECTED_AUTHORIZATION
        or execution_approval.get("authorization") != EXPECTED_AUTHORIZATION
        or prompt_profile.get("artifactIdentity")
        != BASE.artifact_identity(prompt_profile)
        or prompt_profile.get("profileVersion") != "3.0.0"
        or prompt_profile.get("activationAllowed") is not False
        or not isinstance(request_profile, dict)
        or request_profile.get("identity") != prompt_profile["artifactIdentity"]
        or request_profile.get("reference") != V6_PROMPT_REFERENCE
        or request_profile.get("version") != "3.0.0"
        or request_profile.get("productionActivationRequested") is not False
        or approved_profile != expected_profile
    ):
        raise BASE.P7T4Error("external evaluation v6 execution approval invalid")

    evaluator = BASE.evaluator_for_suite(root, suite)
    if suite["evaluatorContract"]["identity"] != evaluator_contract["artifactIdentity"]:
        raise BASE.P7T4Error("suite and evaluator v2 binding mismatch")
    return suite, evaluator, execution_approval


BASE.validate_evaluation_config = validate_evaluation_config
BASE.load_v2_evaluation_contract = load_v2_evaluation_contract
for _name in dir(BASE):
    if not _name.startswith("__"):
        globals()[_name] = getattr(BASE, _name)


if __name__ == "__main__":
    raise SystemExit(BASE.main())
