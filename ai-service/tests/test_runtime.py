from __future__ import annotations

from pathlib import Path
from types import ModuleType, SimpleNamespace
from contextlib import contextmanager
import sys

from app.models import AssistantKey
from app.runtime import TransformersRuntimeBackend


class _FakeModel:
    def __init__(self) -> None:
        self.hf_device_map = {"model": "cuda:0"}
        self.train_modes: list[bool] = []

    def train(self, mode: bool) -> None:
        self.train_modes.append(mode)


def test_transformers_backend_uses_offline_quantized_t4_contract(monkeypatch, tmp_path: Path) -> None:
    calls: dict[str, object] = {}
    base_model = _FakeModel()

    class AutoTokenizer:
        @staticmethod
        def from_pretrained(path, **kwargs):
            calls["tokenizer"] = (path, kwargs)
            return "tokenizer"

    class AutoModelForCausalLM:
        @staticmethod
        def from_pretrained(path, **kwargs):
            calls["model"] = (path, kwargs)
            return base_model

    class BitsAndBytesConfig:
        def __init__(self, **kwargs) -> None:
            calls["quantization"] = kwargs

    class PeftModel(_FakeModel):
        @classmethod
        def from_pretrained(cls, model, path, **kwargs):
            calls["adapter"] = (model, path, kwargs)
            return cls()

    torch = ModuleType("torch")
    torch.float16 = "float16"
    transformers = ModuleType("transformers")
    transformers.AutoTokenizer = AutoTokenizer
    transformers.AutoModelForCausalLM = AutoModelForCausalLM
    transformers.BitsAndBytesConfig = BitsAndBytesConfig
    peft = ModuleType("peft")
    peft.PeftModel = PeftModel
    monkeypatch.setitem(sys.modules, "torch", torch)
    monkeypatch.setitem(sys.modules, "transformers", transformers)
    monkeypatch.setitem(sys.modules, "peft", peft)

    backend = TransformersRuntimeBackend(device="cuda:0")
    base_path = tmp_path / "base"
    adapter_path = tmp_path / "adapter"
    backend.load_base_model(base_path, "ignored-after-validation", "ignored-after-validation")
    backend.load_adapter(AssistantKey.RESEARCH_ASSISTANT, adapter_path, "approved-adapter")

    assert calls["tokenizer"] == (base_path, {"local_files_only": True})
    _, model_options = calls["model"]
    assert model_options["local_files_only"] is True
    assert model_options["device_map"] == "cuda:0"
    assert calls["quantization"] == {
        "load_in_4bit": True,
        "bnb_4bit_quant_type": "nf4",
        "bnb_4bit_use_double_quant": True,
        "bnb_4bit_compute_dtype": "float16",
    }
    assert calls["adapter"] == (
        base_model,
        adapter_path,
        {
            "adapter_name": "RESEARCH_ASSISTANT",
            "is_trainable": False,
            "local_files_only": True,
        },
    )
    assert base_model.train_modes == [False]
    assert backend.model.train_modes == [False]


def test_transformers_backend_rejects_cpu_or_disk_offload(monkeypatch, tmp_path: Path) -> None:
    class AutoTokenizer:
        @staticmethod
        def from_pretrained(_path, **_kwargs):
            return "tokenizer"

    class AutoModelForCausalLM:
        @staticmethod
        def from_pretrained(_path, **_kwargs):
            model = _FakeModel()
            model.hf_device_map = {"model": "cpu"}
            return model

    transformers = ModuleType("transformers")
    transformers.AutoTokenizer = AutoTokenizer
    transformers.AutoModelForCausalLM = AutoModelForCausalLM
    transformers.BitsAndBytesConfig = lambda **_kwargs: SimpleNamespace()
    torch = ModuleType("torch")
    torch.float16 = "float16"
    monkeypatch.setitem(sys.modules, "torch", torch)
    monkeypatch.setitem(sys.modules, "transformers", transformers)

    backend = TransformersRuntimeBackend()

    try:
        backend.load_base_model(tmp_path, "model", "revision")
    except RuntimeError as error:
        assert "offload" in str(error).lower()
    else:
        raise AssertionError("CPU offload must fail closed")


def test_transformers_backend_generates_with_selected_research_adapter() -> None:
    calls: dict[str, object] = {}

    class Encoded(dict):
        def to(self, device):
            calls["device"] = device
            return self

    class Tokenizer:
        def apply_chat_template(self, messages, **kwargs):
            calls["messages"] = messages
            calls["templateOptions"] = kwargs
            return Encoded(input_ids=[[1, 2, 3]])

        def decode(self, tokens, **kwargs):
            calls["decode"] = (tokens, kwargs)
            return " Grounded answer "

    class Model:
        def set_adapter(self, adapter_name):
            calls["adapter"] = adapter_name

        def generate(self, **kwargs):
            calls["generationOptions"] = kwargs
            return [[1, 2, 3, 4, 5]]

    backend = TransformersRuntimeBackend(device="cuda:0")
    backend.tokenizer = Tokenizer()
    backend.model = Model()

    result = backend.generate(
        AssistantKey.RESEARCH_ASSISTANT,
        (
            {"role": "system", "content": "Use bounded context."},
            {"role": "user", "content": "Summarize it."},
        ),
        json_output=False,
    )

    assert result.text == "Grounded answer"
    assert result.prompt_tokens == 3
    assert result.completion_tokens == 2
    assert calls["adapter"] == "RESEARCH_ASSISTANT"
    assert calls["device"] == "cuda:0"
    assert calls["templateOptions"] == {
        "add_generation_prompt": True,
        "return_tensors": "pt",
        "return_dict": True,
    }
    assert calls["generationOptions"]["do_sample"] is False
    assert calls["generationOptions"]["max_new_tokens"] == 512


def test_transformers_backend_disables_loaded_adapter_for_lab_shared_base() -> None:
    calls: list[str] = []

    class Encoded(dict):
        def to(self, _device):
            return self

    class Tokenizer:
        def apply_chat_template(self, _messages, **_kwargs):
            return Encoded(input_ids=[[1, 2]])

        def decode(self, _tokens, **_kwargs):
            return "Lab answer"

    class Model:
        @contextmanager
        def disable_adapter(self):
            calls.append("disabled")
            yield
            calls.append("restored")

        def generate(self, **_kwargs):
            calls.append("generated")
            return [[1, 2, 3]]

    backend = TransformersRuntimeBackend(device="cuda:0")
    backend.tokenizer = Tokenizer()
    backend.model = Model()

    result = backend.generate(
        AssistantKey.LAB_ASSISTANT,
        ({"role": "user", "content": "Show the authorized slot."},),
        json_output=False,
    )

    assert result.text == "Lab answer"
    assert calls == ["disabled", "generated", "restored"]
