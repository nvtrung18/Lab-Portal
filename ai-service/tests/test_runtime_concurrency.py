from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from types import SimpleNamespace
from threading import Lock
from time import sleep, perf_counter

import pytest

from app.models import AssistantKey
from app.runtime import TransformersRuntimeBackend, GenerationBudgetExceeded, generation_deadline


def runtime():
    class Encoded(dict):
        def to(self, device):
            return self

    class Tokenizer:
        def apply_chat_template(self, *args, **kwargs):
            return Encoded(input_ids=[[1]])
        def decode(self, *args, **kwargs):
            return "Complete answer"

    class Model:
        def __init__(self):
            self.adapter = "BASE"
            self.active = 0
            self.max_active = 0
            self.guard = Lock()
        def set_adapter(self, key):
            self.adapter = key
        @contextmanager
        def disable_adapter(self):
            previous = self.adapter
            self.adapter = "BASE"
            yield
            self.adapter = previous
        def generate(self, **kwargs):
            expected = self.adapter
            with self.guard:
                self.active += 1
                self.max_active = max(self.max_active, self.active)
            sleep(0.02)
            assert self.adapter == expected, "Another request changed this request's adapter"
            with self.guard:
                self.active -= 1
            return [[1, 2]]
    backend = TransformersRuntimeBackend()
    backend.tokenizer = Tokenizer()
    backend.model = Model()
    return backend


def test_four_callers_cannot_switch_adapters_during_another_generation():
    backend = runtime()
    keys = [AssistantKey.RESEARCH_ASSISTANT, AssistantKey.ADMIN_ASSISTANT,
            AssistantKey.LAB_ASSISTANT, AssistantKey.RESEARCH_ASSISTANT]
    with ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(lambda key: backend.generate(key, [{"role":"user","content":"test"}],
                                                            json_output=False), keys))
    assert len(results) == 4
    assert backend.model.max_active == 1


def test_expired_request_never_enters_model_generation():
    backend = runtime()
    token = generation_deadline.set(perf_counter() - 1)
    try:
        with pytest.raises(GenerationBudgetExceeded):
            backend.generate(AssistantKey.LAB_ASSISTANT, [], json_output=False)
    finally:
        generation_deadline.reset(token)
    assert backend.model.max_active == 0


def test_max_token_stop_without_eos_is_never_returned_as_completed_answer():
    backend = runtime()
    backend.model.generate = lambda **kwargs: [[1] + [2] * 512]
    with pytest.raises(GenerationBudgetExceeded, match="complete answer"):
        backend.generate(AssistantKey.LAB_ASSISTANT, [], json_output=False)


def test_eos_at_token_budget_is_a_completed_answer_and_deadline_reaches_transformers():
    backend = runtime()
    backend.model.generation_config = SimpleNamespace(eos_token_id=3)
    def complete(**kwargs):
        assert 0 < kwargs["max_time"] <= 90
        return [[1] + [2] * 511 + [3]]
    backend.model.generate = complete
    assert backend.generate(AssistantKey.LAB_ASSISTANT, [], json_output=False).completion_tokens == 512
