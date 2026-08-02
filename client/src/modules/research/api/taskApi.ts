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
  TaskProposalStatus,
  TaskProposalPageResponse,
  TaskProposalListItem,
  TaskProposal,
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

export interface TaskProposalQuery {
  projectId?: number;
  groupId?: number;
  status?: TaskProposalStatus;
  page?: number;
  size?: number;
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

const proposalStatuses = new Set<TaskProposalStatus>(['PENDING', 'APPROVED', 'REJECTED']);
const proposalPriorities = new Set<TaskPriority>(['LOW', 'MEDIUM', 'HIGH', 'URGENT']);
const proposalTypes = new Set<TaskType>(['TASK', 'SUBTASK', 'BUG', 'EXPERIMENT', 'DOCUMENT', 'REVIEW']);
const proposalPageKeys = ['content', 'page', 'size', 'totalElements', 'totalPages'] as const;
const proposalItemKeys = [
  'id', 'proposedById', 'projectId', 'groupId', 'milestoneId', 'parentTaskId',
  'title', 'description', 'priority', 'type', 'dueDate', 'assistedByAi',
  'aiActionSuggestionId', 'status', 'reviewedById', 'reason', 'reviewedAt',
  'createdAt', 'updatedAt', 'canReview',
] as const;

export class TaskProposalContractError extends Error {
  constructor() {
    super('Task proposal data could not be verified.');
    this.name = 'TaskProposalContractError';
  }
}

function proposalContractFailure(): never {
  throw new TaskProposalContractError();
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value: Record<string, unknown>, keys: readonly string[]) {
  const actualKeys = Object.keys(value);
  return actualKeys.length === keys.length
    && keys.every((key) => Object.prototype.hasOwnProperty.call(value, key));
}

function isPositiveId(value: unknown): value is number {
  return Number.isSafeInteger(value) && (value as number) > 0;
}

function isNullableId(value: unknown): value is number | null {
  return value === null || isPositiveId(value);
}

function isNullableString(value: unknown, maxLength: number): value is string | null {
  return value === null || (typeof value === 'string' && value.length <= maxLength);
}

function isInstant(value: unknown): value is InstantString {
  return typeof value === 'string'
    && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(value)
    && Number.isFinite(Date.parse(value));
}

function isLocalDate(value: unknown): value is LocalDateString {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split('-').map(Number);
  const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const daysInMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  return month >= 1 && month <= 12 && day >= 1 && day <= daysInMonth[month - 1];
}

function isNullableLocalDate(value: unknown): value is LocalDateString | null {
  return value === null || isLocalDate(value);
}

function proposalListItem(value: unknown): TaskProposalListItem {
  if (!isRecord(value) || !hasExactKeys(value, proposalItemKeys)) proposalContractFailure();
  const row = value as Record<(typeof proposalItemKeys)[number], unknown>;
  if (!isPositiveId(row.id) || !isPositiveId(row.proposedById)
    || !isPositiveId(row.projectId) || !isPositiveId(row.groupId)
    || !isNullableId(row.milestoneId) || !isNullableId(row.parentTaskId)
    || typeof row.title !== 'string' || row.title.trim().length === 0 || row.title.length > 200
    || !isNullableString(row.description, 4000)
    || typeof row.priority !== 'string' || !proposalPriorities.has(row.priority as TaskPriority)
    || typeof row.type !== 'string' || !proposalTypes.has(row.type as TaskType)
    || !isNullableLocalDate(row.dueDate)
    || typeof row.assistedByAi !== 'boolean' || !isNullableId(row.aiActionSuggestionId)
    || typeof row.status !== 'string' || !proposalStatuses.has(row.status as TaskProposalStatus)
    || !isNullableId(row.reviewedById) || !isNullableString(row.reason, 4000)
    || (row.reason !== null && row.reason.trim().length === 0)
    || (row.reviewedAt !== null && !isInstant(row.reviewedAt))
    || !isInstant(row.createdAt) || !isInstant(row.updatedAt)
    || Date.parse(row.updatedAt) < Date.parse(row.createdAt)
    || typeof row.canReview !== 'boolean') {
    proposalContractFailure();
  }
  if ((row.status === 'PENDING'
      && (row.reviewedById !== null || row.reason !== null || row.reviewedAt !== null))
    || (row.status !== 'PENDING' && (row.reviewedById === null || row.reviewedAt === null))
    || (row.status === 'APPROVED' && row.reason !== null)
    || (row.status === 'REJECTED' && row.reason === null)
    || (row.status !== 'PENDING' && row.canReview)) {
    proposalContractFailure();
  }
  return row as unknown as TaskProposalListItem;
}

function proposalPageBody(value: unknown): TaskProposalPageResponse {
  if (!isRecord(value) || !hasExactKeys(value, proposalPageKeys)) proposalContractFailure();
  const { content, page, size, totalElements, totalPages } = value;
  if (!Array.isArray(content)
    || !Number.isSafeInteger(page) || (page as number) < 0
    || !Number.isSafeInteger(size) || (size as number) < 1 || (size as number) > 100
    || !Number.isSafeInteger(totalElements) || (totalElements as number) < 0
    || !Number.isSafeInteger(totalPages) || (totalPages as number) < 0
    || content.length > (size as number)) {
    proposalContractFailure();
  }
  const expectedTotalPages = (totalElements as number) === 0
    ? 0
    : Math.ceil((totalElements as number) / (size as number));
  const firstElement = (page as number) * (size as number);
  if (!Number.isSafeInteger(firstElement)
    || totalPages !== expectedTotalPages
    || ((page as number) < (totalPages as number)
      ? content.length !== Math.min((size as number), (totalElements as number) - firstElement)
      : content.length !== 0)) {
    proposalContractFailure();
  }
  return {
    content: content.map(proposalListItem),
    page: page as number,
    size: size as number,
    totalElements: totalElements as number,
    totalPages: totalPages as number,
  };
}

function proposalResponseData(responseBody: unknown): unknown {
  if (!isRecord(responseBody) || responseBody.code !== 0
    || !Object.prototype.hasOwnProperty.call(responseBody, 'data')) {
    proposalContractFailure();
  }
  return responseBody.data;
}

function proposalReviewBody(value: unknown): TaskProposalReviewResponse {
  const keys = ['proposalId', 'status', 'reviewedById', 'reason', 'reviewedAt', 'createdTask'] as const;
  if (!isRecord(value) || !hasExactKeys(value, keys)) proposalContractFailure();
  const { proposalId, status, reviewedById, reason, reviewedAt, createdTask } = value;
  if (!isPositiveId(proposalId) || (status !== 'APPROVED' && status !== 'REJECTED')
    || !isPositiveId(reviewedById) || !isNullableString(reason, 4000)
    || !isInstant(reviewedAt) || (createdTask !== null && !isRecord(createdTask))
    || (status === 'APPROVED' && (reason !== null || createdTask === null))
    || (status === 'REJECTED' && (reason === null || reason.trim().length === 0 || createdTask !== null))
    || (isRecord(createdTask) && !isPositiveId(createdTask.id))) {
    proposalContractFailure();
  }
  return value as unknown as TaskProposalReviewResponse;
}

export async function getTaskProposals(options: TaskProposalQuery = {}): Promise<TaskProposalPageResponse> {
  const params: TaskProposalQuery = {};
  if (options.projectId !== undefined) { assertValidId(options.projectId, 'project id'); params.projectId = options.projectId; }
  if (options.groupId !== undefined) { assertValidId(options.groupId, 'group id'); params.groupId = options.groupId; }
  if (options.status !== undefined) {
    if (!proposalStatuses.has(options.status)) throw new Error('Invalid task proposal status');
    params.status = options.status;
  }
  if (options.page !== undefined) { if (!Number.isInteger(options.page) || options.page < 0) throw new Error('Invalid proposal page'); params.page = options.page; }
  if (options.size !== undefined) { if (!Number.isInteger(options.size) || options.size < 1 || options.size > 100) throw new Error('Invalid proposal page size'); params.size = options.size; }
  const response = await apiClient.get<unknown>('/api/research/task-proposals', { params });
  return proposalPageBody(proposalResponseData(response.data));
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

export async function submitTaskProposal(payload: CreateTaskProposalPayload): Promise<TaskProposal> {
  const response = await apiClient.post<TaskApiResponse<TaskProposal>>(
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
  return proposalReviewBody(proposalResponseData(response.data));
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
  return proposalReviewBody(proposalResponseData(response.data));
}
