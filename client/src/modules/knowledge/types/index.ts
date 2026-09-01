export type KnowledgeDomain = 'ADMIN' | 'LAB' | 'RESEARCH';
export type KnowledgeVisibility = 'OWNER' | 'LAB_MEMBERS' | 'PROJECT_MEMBERS' | 'GROUP_MEMBERS' | 'ADMIN_ONLY';

export interface KnowledgeDocumentRequest {
  domain: KnowledgeDomain;
  resourceId: string;
  version: number;
  sourceType: string;
  title: string;
  content: string;
  visibility: KnowledgeVisibility;
  labId?: number;
  projectId?: number;
  groupId?: number;
}

export interface KnowledgeDocumentResponse {
  documentId: number;
  namespace: string;
  domain: KnowledgeDomain;
  resourceId: string;
  version: number;
  sourceType: string;
  visibility: KnowledgeVisibility;
  chunkCount: number;
}
