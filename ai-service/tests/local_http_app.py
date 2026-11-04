"""Loopback-only test factory. Scripted generation; production HTTP/validation remains real."""
from __future__ import annotations

from contextvars import ContextVar
import json
import os
from pathlib import Path
import runpy

from pydantic import SecretStr
from app.config import Settings
# app.main has a module-level default application; isolate its configuration too.
os.environ["AI_INTERNAL_SERVICE_TOKEN"] = "hybrid-test-token"
os.environ["AI_RUNTIME_LOAD_ENABLED"] = "false"
from app.main import create_app
from app.runtime import RuntimeGeneration

request_id = ContextVar("hybrid_request_id", default="")
scenario_file = Path(os.environ["AI_TEST_SCENARIO_FILE"])


class ScriptedBackend:
    def generate(self, assistant_key, messages, *, json_output):
        scenarios = json.loads(scenario_file.read_text(encoding="utf-8"))
        scenario = scenarios.get(request_id.get())
        if scenario is None:
            raise RuntimeError("No scripted generation for this request")
        if scenario.get("raise"):
            raise RuntimeError("Controlled generation failure")
        if "rawOutput" in scenario:
            output = scenario["rawOutput"]
        else:
            try:
                prompt = json.loads(messages[1]["content"])
            except (ValueError, IndexError):
                prompt = {}
            if "candidates" in prompt:
                selected = [c for c in prompt["candidates"] if c["toolId"] == scenario.get("toolId")
                            and (scenario.get("resourceId") is None
                                 or c["resource"]["resourceId"] == scenario["resourceId"])]
                decision = scenario.get("decision", "TOOL_REQUEST")
                if decision == "TOOL_REQUEST" and len(selected) != 1:
                    raise RuntimeError("Scripted tool must identify exactly one authorized candidate")
                output = json.dumps({"decision": decision, "intent": scenario.get("intent", "READ"),
                    "candidateIndex": selected[0]["candidateIndex"] if decision == "TOOL_REQUEST" else None,
                    "message": None if decision == "TOOL_REQUEST" else scenario["message"]})
            else:
                output = json.dumps(scenario["generation"], ensure_ascii=False)
        return RuntimeGeneration(text=output, prompt_tokens=17, completion_tokens=7)


settings = Settings(internal_service_token=SecretStr("hybrid-test-token"), request_timeout_seconds=30)
app = create_app(settings, runtime_backend=ScriptedBackend())
# Same explicit test adapter used by the existing assistant tests. This is not GPU readiness evidence.
ready_loader = runpy.run_path(str(Path(__file__).with_name("test_admin_mvp.py")))["ArtifactReadyLoader"]
app.state.artifact_loader = ready_loader(app.state.artifact_loader)


@app.middleware("http")
async def scripted_request_context(request, call_next):
    current = request_id.set(request.headers.get("X-Request-Id", ""))
    try:
        return await call_next(request)
    finally:
        request_id.reset(current)
