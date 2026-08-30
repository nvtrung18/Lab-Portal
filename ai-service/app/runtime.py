from __future__ import annotations

from pathlib import Path
from typing import Any

from app.models.contracts import AssistantKey


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
