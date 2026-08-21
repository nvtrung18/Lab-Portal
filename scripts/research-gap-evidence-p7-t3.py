#!/usr/bin/env python3
"""Execute only P7-T3 Research gap cases and stop at human approval.

The runner never retrieves a model or installs packages. A pre-provisioned
snapshot at the exact frozen revision is mandatory. The run artifact is not
frozen evidence until a complete user review is validated by ``--freeze``.
"""
from __future__ import annotations

import argparse
import importlib.metadata
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[1]
BASE_SUITE_PATH = ROOT / "evals" / "p6-t4-evaluation-suites.yaml"
GAP_SUITE_PATH = ROOT / "evals" / "p7-t3-research-gap-evaluation-suite.json"
GAP_LOCK_PATH = ROOT / "evals" / "p7-t3-research-gap-evaluation-suite.lock.json"
BENCHMARK_CONFIG_PATH = ROOT / "config" / "p6-t5-benchmark.yaml"
BASELINE_EVIDENCE_PATH = ROOT / "evidence" / "p7-t3-research-baseline-evidence.json"
RUBRIC_PATH = ROOT / "evals" / "human-eval-rubric.yaml"
RUN_ID = "qwen3_4b-R01-P7T3-GAP01"
RUN_ARTIFACT_TYPE = "P7-T3-RESEARCH-GAP-CANDIDATE-RUN"
REVIEW_INPUT_ARTIFACT_TYPE = "P7-T3-RESEARCH-GAP-HUMAN-REVIEW-INPUT"


def _load_module(name: str, path: Path) -> Any:
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"module unavailable: {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


P7T3 = _load_module("p7t3_gap_contract", ROOT / "scripts" / "research-model-decision-p7-t3.py")
BENCHMARK = _load_module("p7t3_gap_benchmark", ROOT / "scripts" / "benchmark-p6-t5.py")
EVALUATOR = BENCHMARK.load_evaluator()


def _load(path: Path) -> dict[str, Any]:
    return P7T3._load_document(path, path.name)


def _write_append_only(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        raise P7T3.ResearchDecisionError(f"output {path}: append-only artifact already exists")
    rendered = json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n"
    temporary_name: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            "w",
            encoding="utf-8",
            newline="\n",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as temporary:
            temporary.write(rendered)
            temporary.flush()
            os.fsync(temporary.fileno())
            temporary_name = temporary.name
        os.link(temporary_name, path)
        Path(temporary_name).unlink()
        temporary_name = None
    except (OSError, TypeError, ValueError) as error:
        if temporary_name is not None:
            Path(temporary_name).unlink(missing_ok=True)
        raise P7T3.ResearchDecisionError(f"output {path}: cannot write: {error}") from error


def preflight_environment(
    model_path: Path | None,
    gap_suite: dict[str, Any],
    gap_lock: dict[str, Any],
    base_suite: dict[str, Any],
    benchmark_config: dict[str, Any],
) -> dict[str, Any]:
    """Report environment facts without model retrieval or package installation."""
    P7T3.validate_gap_suite_lock(
        gap_suite,
        GAP_SUITE_PATH,
        gap_lock,
        base_suite,
        benchmark_config,
    )
    diagnostics: list[str] = []
    revision = P7T3.BASE_MODEL["revision"]
    if model_path is None:
        diagnostics.append("MODEL_SNAPSHOT_REQUIRED")
        model_reference = None
    else:
        resolved = model_path.resolve()
        model_reference = str(resolved)
        if not resolved.is_dir():
            diagnostics.append("MODEL_SNAPSHOT_UNAVAILABLE")
        elif revision not in resolved.parts:
            diagnostics.append("MODEL_REVISION_PATH_MISMATCH")
        elif not (resolved / "config.json").is_file():
            diagnostics.append("MODEL_CONFIG_UNAVAILABLE")

    required_versions = {
        "torch": str(benchmark_config.get("runtime", {}).get("artifacts", {}).get("torch", {}).get("version")),
        "transformers": str(
            benchmark_config.get("runtime", {}).get("artifacts", {}).get("transformers", {}).get("version")
        ),
    }
    observed_versions: dict[str, str | None] = {}
    for package, required in required_versions.items():
        try:
            observed = importlib.metadata.version(package)
        except importlib.metadata.PackageNotFoundError:
            observed = None
            diagnostics.append(f"{package.upper()}_RUNTIME_UNAVAILABLE")
        observed_versions[package] = observed
        if observed is not None and observed != required:
            diagnostics.append(f"{package.upper()}_VERSION_MISMATCH")

    return {
        "status": "READY_FOR_TARGETED_EXECUTION" if not diagnostics else "EVIDENCE_EXECUTION_ENVIRONMENT_REQUIRED",
        "diagnostics": sorted(set(diagnostics)),
        "candidateId": gap_suite["executionPolicy"]["candidateId"],
        "sourceRunId": gap_suite["executionPolicy"]["sourceRunId"],
        "model": gap_suite["executionPolicy"]["model"],
        "modelPath": model_reference,
        "caseIds": gap_suite["executionPolicy"]["caseIds"],
        "suite": {
            "id": gap_suite["suiteId"],
            "version": gap_suite["suiteVersion"],
            "digest": gap_suite["suiteDigest"],
        },
        "runtimeVersions": observed_versions,
        "networkAccessAllowed": False,
    }


def build_run_artifact(
    gap_suite: dict[str, Any],
    candidate: dict[str, Any],
    *,
    raw_outputs: list[dict[str, Any]],
) -> dict[str, Any]:
    findings, automatic = EVALUATOR.score_candidate(gap_suite, candidate)
    candidate_digest = P7T3.sha256_bytes(P7T3.canonical_bytes(candidate))
    expected_case_ids = sorted(case["evalCaseId"] for case in gap_suite["caseInventory"])
    raw_case_ids = sorted(
        output.get("evalCaseId") for output in raw_outputs if isinstance(output, dict)
    )
    if raw_case_ids != expected_case_ids:
        raise P7T3.ResearchDecisionError("gap run: raw output inventory must exactly match targeted cases")
    return {
        "artifactType": RUN_ARTIFACT_TYPE,
        "schemaVersion": "1.0.0",
        "suite": {
            "id": gap_suite["suiteId"],
            "version": gap_suite["suiteVersion"],
            "digest": gap_suite["suiteDigest"],
        },
        "candidate": candidate,
        "candidateOutputDigest": candidate_digest,
        "automatic": automatic,
        "findings": findings,
        "rawOutputs": raw_outputs,
        "checkpoint": "AWAITING_USER:HUMAN_EVALUATION",
    }


def build_review_input(gap_suite: dict[str, Any], run_artifact: dict[str, Any]) -> dict[str, Any]:
    rubric = _load(RUBRIC_PATH)
    report: dict[str, Any] = {}
    errors = EVALUATOR.validate_human(gap_suite, run_artifact["candidate"], None, report, rubric)
    if errors:
        raise P7T3.ResearchDecisionError(errors)
    return {
        "artifactType": REVIEW_INPUT_ARTIFACT_TYPE,
        "schemaVersion": "1.0.0",
        "candidateOutputDigest": run_artifact["candidateOutputDigest"],
        "humanReport": report["humanReport"],
        "instruction": "Complete both immutable REFUSAL records and provide explicit USER_APPROVED sidecar approval; do not synthesize outcomes.",
    }


def freeze_gap_evidence(
    run_artifact: dict[str, Any],
    review_sidecar: dict[str, Any],
    gap_suite: dict[str, Any],
    base_suite: dict[str, Any],
    baseline_evidence: dict[str, Any],
    *,
    source_reference: str,
    review_reference: str,
    evidence_reference: str,
    source_commit: str,
) -> dict[str, Any]:
    P7T3.validate_gap_suite(gap_suite, base_suite)
    P7T3.validate_baseline_evidence(baseline_evidence, base_suite)
    diagnostics: list[str] = []
    run_fields = {
        "artifactType",
        "schemaVersion",
        "suite",
        "candidate",
        "candidateOutputDigest",
        "automatic",
        "findings",
        "rawOutputs",
        "checkpoint",
    }
    if not isinstance(run_artifact, dict) or set(run_artifact) != run_fields:
        diagnostics.append("gap run: exact artifact fields required")
    elif (
        run_artifact.get("artifactType") != RUN_ARTIFACT_TYPE
        or run_artifact.get("schemaVersion") != "1.0.0"
        or run_artifact.get("suite")
        != {
            "id": gap_suite["suiteId"],
            "version": gap_suite["suiteVersion"],
            "digest": gap_suite["suiteDigest"],
        }
        or run_artifact.get("checkpoint") != "AWAITING_USER:HUMAN_EVALUATION"
    ):
        diagnostics.append("gap run: exact suite identity and human checkpoint required")
    candidate = run_artifact.get("candidate") if isinstance(run_artifact, dict) else None
    if not isinstance(candidate, dict):
        diagnostics.append("gap run/candidate: object required")
        candidate = {}
    candidate_digest = P7T3.sha256_bytes(P7T3.canonical_bytes(candidate))
    if run_artifact.get("candidateOutputDigest") != candidate_digest:
        diagnostics.append("gap run/candidateOutputDigest: canonical candidate digest mismatch")
    expected_metadata = {
        "candidateId": "qwen3_4b",
        "sourceRunId": "qwen3_4b-R01",
        "identifier": P7T3.BASE_MODEL["identifier"],
        "revision": P7T3.BASE_MODEL["revision"],
    }
    if candidate.get("candidateRunId") != RUN_ID or candidate.get("modelMetadata") != expected_metadata:
        diagnostics.append("gap run/candidate: exact qwen3_4b-R01 shared-base identity required")
    score_findings, automatic = EVALUATOR.score_candidate(gap_suite, candidate)
    if run_artifact.get("findings") != score_findings or run_artifact.get("automatic") != automatic:
        diagnostics.append("gap run: automatic scorer evidence mismatch")

    review_fields = {"candidateOutputDigest", "approval", "review"}
    if not isinstance(review_sidecar, dict) or set(review_sidecar) != review_fields:
        diagnostics.append("gap review: exact candidate digest, approval, and review required")
        review_sidecar = {}
    if review_sidecar.get("candidateOutputDigest") != candidate_digest:
        diagnostics.append("gap review: candidate output digest mismatch")
    approval = review_sidecar.get("approval")
    if not isinstance(approval, dict) or set(approval) != {"decision", "source", "approvedAt"}:
        diagnostics.append("gap review/approval: exact approval fields required")
        approval = {}
    if approval.get("decision") != "USER_APPROVED":
        diagnostics.append("gap review/approval: USER_APPROVED required")
    if not all(isinstance(approval.get(field), str) and approval[field].strip() for field in ("source", "approvedAt")):
        diagnostics.append("gap review/approval: source and approvedAt required")
    rubric = _load(RUBRIC_PATH)
    human_report: dict[str, Any] = {}
    human_errors = EVALUATOR.validate_human(
        gap_suite,
        candidate,
        review_sidecar.get("review"),
        human_report,
        rubric,
    )
    diagnostics.extend(human_errors)
    records = human_report.get("humanReport", {}).get("records", [])
    if human_report.get("humanReviewState") != "COMPLETE" or any(
        record.get("overall") not in {"PASS", "FAIL"} for record in records if isinstance(record, dict)
    ):
        diagnostics.append("gap review: complete actual PASS/FAIL human outcomes required")
    for reference, path in (
        (source_reference, "gap run/reference"),
        (review_reference, "gap review/reference"),
        (evidence_reference, "gap evidence/reference"),
    ):
        P7T3._validate_reference(reference, path, diagnostics)
    if not isinstance(source_commit, str) or not source_commit.strip() or source_commit in {
        "UNAVAILABLE",
        "SOURCE_COMMIT",
    }:
        diagnostics.append("gap evidence/source commit: resolved Git commit required")
    if diagnostics:
        raise P7T3.ResearchDecisionError(diagnostics)

    record_by_case = {record["evalCaseId"]: record for record in records}
    evidence = {
        "artifactType": P7T3.GAP_EVIDENCE_ARTIFACT_TYPE,
        "schemaVersion": P7T3.SCHEMA_VERSION,
        "decisionRuleVersion": P7T3.GAP_DECISION_RULE_VERSION,
        "assistantKey": P7T3.ASSISTANT_KEY,
        "baseModel": dict(P7T3.BASE_MODEL),
        "promptProfile": dict(P7T3.PROMPT_PROFILE),
        "suiteLineage": {
            "base": dict(gap_suite["baseSuite"]),
            "gap": {
                "id": gap_suite["suiteId"],
                "version": gap_suite["suiteVersion"],
                "digest": gap_suite["suiteDigest"],
            },
        },
        "candidate": {
            "id": "qwen3_4b",
            "sourceRunId": "qwen3_4b-R01",
            "gapRunId": RUN_ID,
            "outputDigest": candidate_digest,
        },
        "approval": {
            "status": "USER_APPROVED",
            "reference": review_reference,
            "sha256": P7T3.sha256_bytes(P7T3.canonical_bytes(review_sidecar)),
        },
        "evidenceReference": evidence_reference,
        "sourceCommit": source_commit,
        "caseResults": [
            {
                "evalCaseId": case["evalCaseId"],
                "result": record_by_case[case["evalCaseId"]]["overall"],
                "caseDigest": P7T3.sha256_bytes(P7T3.canonical_bytes(case)),
                "evidenceSha256": record_by_case[case["evalCaseId"]]["candidateCaseDigest"],
                "sourceRecordReference": f"{source_reference}#/candidate/cases/{case['evalCaseId']}",
                "humanReviewStatus": "USER_APPROVED",
            }
            for case in gap_suite["caseInventory"]
        ],
        "artifactIdentity": "",
    }
    evidence["artifactIdentity"] = P7T3.gap_evidence_identity(evidence)
    P7T3.validate_gap_evidence(evidence, gap_suite, baseline_evidence, base_suite)
    return evidence


def execute_gap_cases(
    model_path: Path,
    device: str,
    gap_suite: dict[str, Any],
    gap_lock: dict[str, Any],
    base_suite: dict[str, Any],
    benchmark_config: dict[str, Any],
) -> dict[str, Any]:
    preflight = preflight_environment(model_path, gap_suite, gap_lock, base_suite, benchmark_config)
    if preflight["status"] != "READY_FOR_TARGETED_EXECUTION":
        raise P7T3.ResearchDecisionError(preflight["diagnostics"])
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer

    if device.startswith("cuda") and not torch.cuda.is_available():
        raise P7T3.ResearchDecisionError("CUDA_RUNTIME_UNAVAILABLE")
    tokenizer = AutoTokenizer.from_pretrained(model_path, local_files_only=True)
    model = AutoModelForCausalLM.from_pretrained(
        model_path,
        local_files_only=True,
        torch_dtype="auto",
        low_cpu_mem_usage=True,
    ).to(device)
    model.eval()
    decode = benchmark_config["runtime"]["decode"]
    BENCHMARK.configure_determinism(torch, decode["seed"])
    generation = BENCHMARK.generation_kwargs(decode)
    profile = BENCHMARK.assistant_profile(benchmark_config, P7T3.ASSISTANT_KEY)
    template_candidate = {"id": "qwen3_4b", "qwen3": True}
    cases: list[dict[str, Any]] = []
    raw_outputs: list[dict[str, Any]] = []
    with torch.inference_mode():
        for case in gap_suite["caseInventory"]:
            messages = BENCHMARK.render_prompt(case, profile["systemInstruction"])
            inputs = tokenizer.apply_chat_template(
                messages,
                **BENCHMARK.template_kwargs(template_candidate),
            ).to(device)
            output = model.generate(**inputs, **generation)
            raw = tokenizer.decode(
                output[0][inputs["input_ids"].shape[-1] :],
                skip_special_tokens=True,
            )
            parsed, parse_error = BENCHMARK.parse_raw_response(case["evalCaseId"], raw)
            cases.append(BENCHMARK.scored_case(case["evalCaseId"], parsed, parse_error))
            raw_outputs.append(
                {
                    "evalCaseId": case["evalCaseId"],
                    "rawText": raw,
                    "rawTextDigest": P7T3.sha256_bytes(raw.encode("utf-8")),
                }
            )
    candidate = {
        "suiteId": gap_suite["suiteId"],
        "suiteVersion": gap_suite["suiteVersion"],
        "candidateRunId": RUN_ID,
        "modelMetadata": {
            "candidateId": "qwen3_4b",
            "sourceRunId": "qwen3_4b-R01",
            "identifier": P7T3.BASE_MODEL["identifier"],
            "revision": P7T3.BASE_MODEL["revision"],
        },
        "cases": cases,
    }
    return build_run_artifact(gap_suite, candidate, raw_outputs=raw_outputs)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--preflight", action="store_true")
    mode.add_argument("--run", action="store_true")
    mode.add_argument("--freeze", action="store_true")
    parser.add_argument("--model-path", type=Path)
    parser.add_argument("--device", choices=("cpu", "cuda:0"), default="cpu")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "evidence" / "p7-t3-gap-run")
    parser.add_argument("--run-artifact", type=Path)
    parser.add_argument("--review", type=Path)
    parser.add_argument("--baseline-evidence", type=Path, default=BASELINE_EVIDENCE_PATH)
    parser.add_argument(
        "--output",
        type=Path,
        default=ROOT / "evidence" / "p7-t3-research-gap-evidence-v1.json",
    )
    parser.add_argument("--source-commit", default="UNAVAILABLE")
    args = parser.parse_args()
    try:
        base_suite = _load(BASE_SUITE_PATH)
        gap_suite = _load(GAP_SUITE_PATH)
        gap_lock = _load(GAP_LOCK_PATH)
        benchmark_config = _load(BENCHMARK_CONFIG_PATH)
        if args.preflight:
            result = preflight_environment(args.model_path, gap_suite, gap_lock, base_suite, benchmark_config)
            print(json.dumps(result, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
            return 0 if result["status"] == "READY_FOR_TARGETED_EXECUTION" else 3
        if args.run:
            if args.model_path is None:
                raise P7T3.ResearchDecisionError("MODEL_SNAPSHOT_REQUIRED")
            run_path = args.output_dir / f"{RUN_ID}.json"
            review_path = args.output_dir / f"{RUN_ID}-review-input.json"
            P7T3._repository_reference(run_path)
            P7T3._repository_reference(review_path)
            run_artifact = execute_gap_cases(
                args.model_path,
                args.device,
                gap_suite,
                gap_lock,
                base_suite,
                benchmark_config,
            )
            _write_append_only(run_path, run_artifact)
            _write_append_only(review_path, build_review_input(gap_suite, run_artifact))
            print(
                json.dumps(
                    {
                        "status": "AWAITING_USER:HUMAN_EVALUATION",
                        "caseIds": gap_suite["executionPolicy"]["caseIds"],
                        "runArtifact": P7T3._repository_reference(run_path),
                        "reviewInput": P7T3._repository_reference(review_path),
                    },
                    ensure_ascii=False,
                    sort_keys=True,
                    separators=(",", ":"),
                )
            )
            return 3
        if args.run_artifact is None or args.review is None:
            raise P7T3.ResearchDecisionError("--freeze requires --run-artifact and --review")
        run_artifact = _load(args.run_artifact)
        review = _load(args.review)
        baseline_evidence = _load(args.baseline_evidence)
        frozen = freeze_gap_evidence(
            run_artifact,
            review,
            gap_suite,
            base_suite,
            baseline_evidence,
            source_reference=args.run_artifact.name,
            review_reference=args.review.name,
            evidence_reference=P7T3._repository_reference(args.output),
            source_commit=args.source_commit,
        )
        _write_append_only(args.output, frozen)
        print(json.dumps(frozen, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0
    except P7T3.ResearchDecisionError as error:
        print(
            json.dumps(
                {"status": "ERROR", "diagnostics": error.diagnostics},
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            ),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
