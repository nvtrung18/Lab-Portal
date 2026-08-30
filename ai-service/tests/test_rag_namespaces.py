from __future__ import annotations

import pytest

from app.models import AssistantKey
from app.rag_namespaces import KnowledgeNamespace, namespace_for


def test_every_assistant_has_a_distinct_domain_namespace() -> None:
    resolved = {assistant: namespace_for(assistant) for assistant in AssistantKey}

    assert resolved == {
        AssistantKey.ADMIN_ASSISTANT: KnowledgeNamespace.ADMIN,
        AssistantKey.LAB_ASSISTANT: KnowledgeNamespace.LAB,
        AssistantKey.RESEARCH_ASSISTANT: KnowledgeNamespace.RESEARCH,
    }
    assert len(set(resolved.values())) == len(AssistantKey)


def test_namespace_resolution_has_no_default() -> None:
    with pytest.raises(ValueError, match="known assistant key"):
        namespace_for("UNKNOWN")  # type: ignore[arg-type]
