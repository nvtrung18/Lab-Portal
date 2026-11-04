import json

import pytest
from pydantic import ValidationError

from app.models.contracts import AssistantRequest, ToolPlanningRequest


def test_full_public_message_fits_inside_trusted_dialogue_envelope():
    message = "x" * 32768
    wrapped = json.dumps({"dialogueVersion": 1, "message": message,
                          "pendingState": None, "history": []})
    request = AssistantRequest(assistantKey="LAB_ASSISTANT", input=wrapped)
    assert json.loads(request.input)["message"] == message


@pytest.mark.parametrize("message", ["x" * 32769, " "], ids=["too-long", "blank"])
def test_envelope_cannot_bypass_public_message_validation(message):
    with pytest.raises(ValidationError):
        AssistantRequest(assistantKey="LAB_ASSISTANT", input=json.dumps(
            {"dialogueVersion": 1, "message": message, "pendingState": None, "history": []}))


def test_plain_input_limit_is_not_widened():
    with pytest.raises(ValidationError):
        AssistantRequest(assistantKey="LAB_ASSISTANT", input="x" * 32769)
