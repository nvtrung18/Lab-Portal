from __future__ import annotations

from contextlib import nullcontext
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping, Sequence

from app.models.contracts import AssistantKey


@dataclass(frozen=True)
class RuntimeGeneration:
    text: str
    prompt_tokens: int
    completion_tokens: int


class TransformersRuntimeBackend:
    """Offline-only Transformers/PEFT runtime used after artifact validation."""

    def __init__(self, *, device: str = "cuda:0") -> None:
        self.device = device
        self.model: Any | None = None
        self.tokenizer: Any | None = None

    def load_base_model(self, artifact_path: Path, identifier: str, revision: str) -> None:
        del identifier, revision
        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig

        tokenizer = AutoTokenizer.from_pretrained(artifact_path, local_files_only=True)
        quantization = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_use_double_quant=True,
            bnb_4bit_compute_dtype=torch.float16,
        )
        model = AutoModelForCausalLM.from_pretrained(
            artifact_path,
            local_files_only=True,
            quantization_config=quantization,
            device_map=self.device,
        )
        device_map = getattr(model, "hf_device_map", {})
        if any(str(device).lower() in {"cpu", "disk"} for device in device_map.values()):
            raise RuntimeError("Runtime offload is not allowed for the approved profile.")
        model.train(False)
        self.tokenizer = tokenizer
        self.model = model

    def load_adapter(self, assistant_key: AssistantKey, artifact_path: Path, identifier: str) -> None:
        del identifier
        if self.model is None:
            raise RuntimeError("Base model must be loaded before an adapter.")
        from peft import PeftModel

        if isinstance(self.model, PeftModel):
            self.model.load_adapter(
                artifact_path,
                adapter_name=assistant_key.value,
                is_trainable=False,
                local_files_only=True,
            )
            self.model.train(False)
            return
        self.model = PeftModel.from_pretrained(
            self.model,
            artifact_path,
            adapter_name=assistant_key.value,
            is_trainable=False,
            local_files_only=True,
        )
        self.model.train(False)

    def generate(
        self,
        assistant_key: AssistantKey,
        messages: Sequence[Mapping[str, str]],
        *,
        json_output: bool,
    ) -> RuntimeGeneration:
        del json_output
        if self.model is None or self.tokenizer is None:
            raise RuntimeError("Model runtime is not loaded.")
        set_adapter = getattr(self.model, "set_adapter", None)
        if assistant_key is AssistantKey.RESEARCH_ASSISTANT:
            if not callable(set_adapter):
                raise RuntimeError("Approved Research adapter is not loaded.")
            set_adapter(assistant_key.value)

        adapter_context = nullcontext()
        if assistant_key in {AssistantKey.ADMIN_ASSISTANT, AssistantKey.LAB_ASSISTANT}:
            disable_adapter = getattr(self.model, "disable_adapter", None)
            if callable(disable_adapter):
                adapter_context = disable_adapter()

        encoded = self.tokenizer.apply_chat_template(
            list(messages),
            add_generation_prompt=True,
            return_tensors="pt",
            return_dict=True,
        ).to(self.device)
        input_ids = encoded["input_ids"]
        prompt_tokens = input_ids.shape[-1] if hasattr(input_ids, "shape") else len(input_ids[0])
        with adapter_context:
            generated = self.model.generate(
                **encoded,
                max_new_tokens=512,
                do_sample=False,
            )
        completion_ids = generated[0][prompt_tokens:]
        text = self.tokenizer.decode(completion_ids, skip_special_tokens=True).strip()
        if not text:
            raise RuntimeError("Model returned an empty response.")
        return RuntimeGeneration(
            text=text,
            prompt_tokens=prompt_tokens,
            completion_tokens=len(completion_ids),
        )
