export type TopicStatus = 'RECRUITING' | 'ONGOING' | 'PAUSED' | 'COMPLETED';

export type GroupStatus = 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED';

export type ProjectStatus = 'DRAFT' | 'PLANNED' | 'ONGOING' | 'WAITING_REVIEW' | 'COMPLETED' | 'ARCHIVED' | 'CANCELLED';

export type ResearchPriority = 'HIGH' | 'MEDIUM' | 'LOW';

export interface ResearchTopic {
  id: number;
  labId: number;
  name: string;
  description?: string | null;
  requirements?: string | null;
  references?: string | null;
  managerName?: string | null;
  createdByName?: string | null;
  status?: TopicStatus | null;
  groupCount?: number | null;
  createdAt?: string | null;
}

export interface ResearchGroup {
  id: number;
  labId: number;
  topicId?: number | null;
  name: string;
  description?: string | null;
  objective?: string | null;
  plan?: string | null;
  status?: GroupStatus | null;
  memberCount?: number | null;
  projectCount?: number | null;
  createdByName?: string | null;
  createdAt?: string | null;
}

export interface ResearchProject {
  id: number;
  groupId: number;
  topicId?: number | null;
  code?: string | null;
  title: string;
  description?: string | null;
  objective?: string | null;
  status?: ProjectStatus | null;
  managerName?: string | null;
  createdByName?: string | null;
  priority?: ResearchPriority | null;
  requiredProducts?: string | null;
  evaluationCriteria?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  expectedEndDate?: string | null;
  createdAt?: string | null;
}

export interface CreateTopicPayload {
  labId: number;
  name: string;
  description?: string;
  requirements?: string;
  references?: string;
  status?: TopicStatus;
}

export interface CreateGroupPayload {
  labId: number;
  topicId: number;
  name: string;
  description?: string;
  objective?: string;
  plan?: string;
  status?: GroupStatus;
}

export interface CreateProjectPayload {
  groupId: number;
  code?: string;
  title: string;
  description?: string;
  objective?: string;
  startDate?: string;
  expectedEndDate?: string;
  priority?: ResearchPriority;
  requiredProducts?: string;
  evaluationCriteria?: string;
  status?: ProjectStatus;
}
