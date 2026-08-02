import type { PageResponse, Response } from '../../../shared/types';

export type TaskStatus =
  | 'BACKLOG'
  | 'TODO'
  | 'IN_PROGRESS'
  | 'IN_REVIEW'
  | 'NEEDS_REVISION'
  | 'DONE'
  | 'BLOCKED'
  | 'CANCELLED';

export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export type TaskType =
  | 'TASK'
  | 'SUBTASK'
  | 'BUG'
  | 'EXPERIMENT'
  | 'DOCUMENT'
  | 'REVIEW';

export type TaskProposalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface TaskProposal {
  id: number;
  proposedById: number;
  projectId: number;
  groupId: number;
  milestoneId: number | null;
  parentTaskId: number | null;
  title: string;
  description: string | null;
  priority: TaskPriority | null;
  type: TaskType | null;
  dueDate: LocalDateString | null;
  assistedByAi: boolean;
  aiActionSuggestionId: number | null;
  status: TaskProposalStatus;
  createdAt: InstantString;
  updatedAt: InstantString;
  reviewedById?: number | null;
  reason?: string | null;
  reviewedAt?: InstantString | null;
  canReview?: boolean;
}

export type TaskProposalListItem = Required<Omit<TaskProposal, 'reviewedById' | 'reason' | 'reviewedAt' | 'canReview'>> & {
  reviewedById: number | null;
  reason: string | null;
  reviewedAt: InstantString | null;
  canReview: boolean;
};

export type TaskProposalPageResponse = PageResponse<TaskProposalListItem>;

export interface TaskProposalReview {
  proposalId: number;
  status: TaskProposalStatus;
  reviewedById: number | null;
  reason: string | null;
  reviewedAt: InstantString;
  createdTask: TaskResponse | null;
}

/** Backend LocalDate serialized as yyyy-MM-dd. */
export type LocalDateString = string;

/** Backend Instant serialized as an ISO-8601 string. */
export type InstantString = string;

export interface TaskResponse {
  id: number;

  milestoneId?: number | null;
  projectId?: number | null;
  groupId?: number | null;
  parentTaskId?: number | null;
  epicId?: number | null;
  milestoneTitle?: string | null;

  title: string;

  description?: string | null;
  latestReportStatus?: string | null;
  assignedToStudentId?: number | null;
  assignedToStudentName?: string | null;
  assignedToStudentEmail?: string | null;
  deadline?: LocalDateString | null;
  dueDate?: LocalDateString | null;

  status: TaskStatus;
  priority: TaskPriority;
  type: TaskType;

  blockedReason?: string | null;
  createdBy?: number | null;

  progressPercent: number;
  createdAt: InstantString;
  updatedAt: InstantString;
}

export interface TaskBoardColumnResponse {
  status: TaskStatus;
  tasks: TaskResponse[];
}

export interface ProjectTaskBoardResponse {
  projectId: number;
  columns: TaskBoardColumnResponse[];
}

export type TaskBacklogPageResponse = PageResponse<TaskResponse>;

export interface TaskApiResponse<T> extends Response<T> {
  timestamp: InstantString;
}
