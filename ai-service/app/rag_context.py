from __future__ import annotations

from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, StringConstraints

from app.models.contracts import to_camel


BoundedText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=1200)]
BoundedIdentifier = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=255)]
RetrievalNamespace = Annotated[
    str,
    StringConstraints(pattern=r"^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$", min_length=1, max_length=64),
]


class _RagModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, extra="forbid", frozen=True, populate_by_name=True)


class AuthorizedRagChunk(_RagModel):
    document_id: int = Field(gt=0)
    resource_id: BoundedIdentifier
    version: int = Field(gt=0)
    chunk_index: int = Field(ge=0)
    page_number: int | None = Field(default=None, gt=0)
    source_type: BoundedIdentifier
    content: BoundedText
    trusted: Literal[False]


class AuthorizedRetrieval(_RagModel):
    namespace: RetrievalNamespace
    chunks: tuple[AuthorizedRagChunk, ...] = Field(max_length=5)
