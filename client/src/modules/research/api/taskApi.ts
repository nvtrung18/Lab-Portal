import { apiClient } from '../../../shared/api';
import type { TaskApiResponse } from '../types';
import type {
  InstantString,
  LocalDateString,
  ProjectTaskBoardResponse,
  TaskBacklogPageResponse,
  TaskPriority,
  TaskResponse,
  TaskStatus,
  TaskType,
} from '../types';

export interface ProjectTaskBoardQuery {
  groupId?: number;
  assigneeId?: number;
  status?: TaskStatus;
  priority?: TaskPriority;
  type?: TaskType;
  includeBacklog?: boolean;
  includeCancelled?: boolean;
}

export interface ProjectTaskBacklogQuery {
  /** Zero-based; omitted defaults to the backend's page 0. */
  page?: number;
  /** Omitted defaults to 20; backend accepts values from 1 through 100. */
  size?: number;
}

export interface CreateTaskProposalPayload {
  projectId: number;
  groupId: number;
  milestoneId?: number;
  parentTaskId?: number;
  title: string;
  description?: string;
  priority?: TaskPriority;
  type?: TaskType;
  dueDate?: LocalDateString;
}

export interface RejectTaskProposalPayload {
  reason: string;
}

export type TaskProposalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface TaskProposalResponse {
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
}

export interface TaskProposalReviewResponse {
  proposalId: number;
  status: TaskProposalStatus;
  reviewedById: number | null;
  reason: string | null;
  reviewedAt: InstantString;
  createdTask: TaskResponse | null;
}

function assertValidId(id: number, name: string) {
  if (!Number.isSafeInteger(id) || id <= 0) {
    throw new Error(`Invalid ${name}: ${id}`);
  }
}

function encodeId(id: number) {
  return encodeURIComponent(String(id));
}

function boardQueryParams(options: ProjectTaskBoardQuery): Partial<ProjectTaskBoardQuery> {
  if (options.groupId !== undefined) {
    assertValidId(options.groupId, 'group id');
  }
  if (options.assigneeId !== undefined) {
    assertValidId(options.assigneeId, 'assignee id');
  }

  const params: Partial<ProjectTaskBoardQuery> = {};
  if (options.groupId !== undefined) params.groupId = options.groupId;
  if (options.assigneeId !== undefined) params.assigneeId = options.assigneeId;
  if (options.status !== undefined) params.status = options.status;
  if (options.priority !== undefined) params.priority = options.priority;
  if (options.type !== undefined) params.type = options.type;
  if (options.includeBacklog !== undefined) params.includeBacklog = options.includeBacklog;
  if (options.includeCancelled !== undefined) params.includeCancelled = options.includeCancelled;
  return params;
}

function createProposalBody(payload: CreateTaskProposalPayload): Partial<CreateTaskProposalPayload> {
  assertValidId(payload.projectId, 'project id');
  assertValidId(payload.groupId, 'group id');
  if (payload.milestoneId !== undefined) {
    assertValidId(payload.milestoneId, 'milestone id');
  }
  if (payload.parentTaskId !== undefined) {
    assertValidId(payload.parentTaskId, 'parent task id');
  }

  const body: Partial<CreateTaskProposalPayload> = {
    projectId: payload.projectId,
    groupId: payload.groupId,
    title: payload.title,
  };
  if (payload.milestoneId !== undefined) body.milestoneId = payload.milestoneId;
  if (payload.parentTaskId !== undefined) body.parentTaskId = payload.parentTaskId;
  if (payload.description !== undefined) body.description = payload.description;
  if (payload.priority !== undefined) body.priority = payload.priority;
  if (payload.type !== undefined) body.type = payload.type;
  if (payload.dueDate !== undefined) body.dueDate = payload.dueDate;
  return body;
}

function unwrapResponseData<T>(responseBody: TaskApiResponse<T>): T {
  if (responseBody.data == null) {
    throw new Error('API response does not contain data');
  }

  return responseBody.data;
}

export async function getProjectTaskBoard(
  projectId: number,
  options: ProjectTaskBoardQuery = {},
): Promise<ProjectTaskBoardResponse> {
  assertValidId(projectId, 'project id');
  const response = await apiClient.get<TaskApiResponse<ProjectTaskBoardResponse>>(
    `/api/research/projects/${encodeId(projectId)}/board`,
    { params: boardQueryParams(options) },
  );
  return unwrapResponseData(response.data);
}

export async function getProjectTaskBacklog(
  projectId: number,
  options: ProjectTaskBacklogQuery = {},
): Promise<TaskBacklogPageResponse> {
  assertValidId(projectId, 'project id');
  const params: ProjectTaskBacklogQuery = {};
  if (options.page !== undefined) params.page = options.page;
  if (options.size !== undefined) params.size = options.size;
  const response = await apiClient.get<TaskApiResponse<TaskBacklogPageResponse>>(
    `/api/research/projects/${encodeId(projectId)}/backlog`,
    { params },
  );
  return unwrapResponseData(response.data);
}

export async function submitTaskProposal(payload: CreateTaskProposalPayload): Promise<TaskProposalResponse> {
  const response = await apiClient.post<TaskApiResponse<TaskProposalResponse>>(
    '/api/research/task-proposals',
    createProposalBody(payload),
  );
  return unwrapResponseData(response.data);
}

export async function approveTaskProposal(proposalId: number): Promise<TaskProposalReviewResponse> {
  assertValidId(proposalId, 'task proposal id');
  const response = await apiClient.post<TaskApiResponse<TaskProposalReviewResponse>>(
    `/api/research/task-proposals/${encodeId(proposalId)}/approve`,
  );
  return unwrapResponseData(response.data);
}

export async function rejectTaskProposal(
  proposalId: number,
  payload: RejectTaskProposalPayload,
): Promise<TaskProposalReviewResponse> {
  assertValidId(proposalId, 'task proposal id');
  const response = await apiClient.post<TaskApiResponse<TaskProposalReviewResponse>>(
    `/api/research/task-proposals/${encodeId(proposalId)}/reject`,
    { reason: payload.reason },
  );
  return unwrapResponseData(response.data);
}
