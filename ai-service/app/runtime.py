from __future__ import annotations

from contextlib import nullcontext
from contextvars import ContextVar
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping, Sequence
from threading import Lock
from time import perf_counter
import logging

from app.models.contracts import AssistantKey

generation_deadline: ContextVar[float | None] = ContextVar("generation_deadline", default=None)
logger = logging.getLogger(__name__)


class GenerationBudgetExceeded(RuntimeError):
    """No completed answer may be returned after the request's inference budget."""


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
        # ponytail: one shared GPU/model; serialize adapter switching with generation.
        self._generation_lock = Lock()

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
        deadline = generation_deadline.get() or (perf_counter() + 90)
        queued_at = perf_counter()
        remaining = deadline - perf_counter()
        if remaining <= 0 or not self._generation_lock.acquire(timeout=max(remaining, 0)):
            raise GenerationBudgetExceeded("Inference queue deadline exceeded")
        try:
            if perf_counter() >= deadline:
                raise GenerationBudgetExceeded("Inference deadline exceeded")
            started_at = perf_counter()
            result = self._generate(assistant_key, messages, json_output=json_output, deadline=deadline)
            logger.info("ai_generation assistant=%s queueMs=%.1f generationMs=%.1f promptTokens=%d completionTokens=%d",
                        assistant_key.value, (started_at - queued_at) * 1000,
                        (perf_counter() - started_at) * 1000, result.prompt_tokens, result.completion_tokens)
            return result
        finally:
            self._generation_lock.release()

    def _generate(self, assistant_key, messages, *, json_output, deadline) -> RuntimeGeneration:
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
        remaining = deadline - perf_counter()
        if remaining <= 0:
            raise GenerationBudgetExceeded("Inference deadline exceeded")
        with adapter_context:
            generated = self.model.generate(
                **encoded,
                max_new_tokens=512,
                max_time=remaining,
                do_sample=False,
            )
        completion_ids = generated[0][prompt_tokens:]
        eos = getattr(getattr(self.model, "generation_config", None), "eos_token_id", None)
        if eos is None:
            eos = getattr(self.tokenizer, "eos_token_id", None)
        end_ids = eos if isinstance(eos, (list, tuple)) else [eos] if eos is not None else []
        ended = bool(len(completion_ids)) and int(completion_ids[-1]) in end_ids
        if perf_counter() >= deadline or len(completion_ids) >= 512 and not ended:
            raise GenerationBudgetExceeded("Generation stopped before a complete answer")
        text = self.tokenizer.decode(completion_ids, skip_special_tokens=True).strip()
        if not text:
            raise RuntimeError("Model returned an empty response.")
        return RuntimeGeneration(
            text=text,
            prompt_tokens=prompt_tokens,
            completion_tokens=len(completion_ids),
        )
