from __future__ import annotations

from enum import StrEnum
from types import MappingProxyType
from typing import Mapping

from app.models.contracts import AssistantKey


class KnowledgeNamespace(StrEnum):
    ADMIN = "admin-knowledge"
    LAB = "lab-knowledge"
    RESEARCH = "research-knowledge"


_NAMESPACE_BY_ASSISTANT: Mapping[AssistantKey, KnowledgeNamespace] = MappingProxyType(
    {
        AssistantKey.ADMIN_ASSISTANT: KnowledgeNamespace.ADMIN,
        AssistantKey.LAB_ASSISTANT: KnowledgeNamespace.LAB,
        AssistantKey.RESEARCH_ASSISTANT: KnowledgeNamespace.RESEARCH,
    }
)


def namespace_for(assistant_key: AssistantKey) -> KnowledgeNamespace:
    """Resolve an explicit assistant namespace without a cross-domain fallback."""
    try:
        return _NAMESPACE_BY_ASSISTANT[assistant_key]
    except (KeyError, TypeError):
        raise ValueError("A known assistant key is required for retrieval.") from None
