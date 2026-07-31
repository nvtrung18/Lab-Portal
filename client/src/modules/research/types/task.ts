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
