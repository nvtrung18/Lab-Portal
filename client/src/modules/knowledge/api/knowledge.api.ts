import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';
import type { KnowledgeDocumentRequest, KnowledgeDocumentResponse } from '../types';

export async function ingestKnowledgeDocument(request: KnowledgeDocumentRequest) {
  const response = await apiClient.post<Response<KnowledgeDocumentResponse>>('/api/ai/rag/documents', request);
  return response.data.data;
}

export async function reindexKnowledgeDocument(documentId: number, request: KnowledgeDocumentRequest) {
  const response = await apiClient.put<Response<KnowledgeDocumentResponse>>(`/api/ai/rag/documents/${documentId}`, request);
  return response.data.data;
}

export async function revokeKnowledgeDocument(documentId: number) {
  await apiClient.delete(`/api/ai/rag/documents/${documentId}`);
}
