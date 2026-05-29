import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';
import { normalizeTask } from '../taskBoardHelpers';
import type {
  RawResearchEvaluation,
  RawResearchLog,
  RawResearchProduct,
  RawResearchTask,
  ResearchEvaluation,
  ResearchGroup,
  ResearchGroupMember,
  ResearchLog,
  ResearchLogFilters,
  ResearchMilestone,
  ResearchProduct,
  ResearchReport,
  ResearchTask,
} from '../types';

type PageLike<T> = {
  items?: T[];
  content?: T[];
  data?: T[];
};

type ApiListResponse<T> = Response<T[]> | Response<PageLike<T>> | T[] | PageLike<T> | null | undefined;

function isValidGroupId(groupId: number) {
  return Number.isFinite(groupId) && groupId > 0;
}

function assertValidGroupId(groupId: number) {
  if (!isValidGroupId(groupId)) {
    throw new Error(`Invalid research group id: ${groupId}`);
  }
}

function unwrapData<T>(responseBody: Response<T> | T | null | undefined): T | null {
  if (responseBody && typeof responseBody === 'object' && 'data' in responseBody) {
    return (responseBody as Response<T>).data ?? null;
  }

  return responseBody ?? null;
}

export function normalizeListResponse<T>(responseBody: ApiListResponse<T>): T[] {
  const data = unwrapData<T[] | PageLike<T>>(responseBody);

  if (Array.isArray(data)) {
    return data;
  }

  if (data && typeof data === 'object') {
    if (Array.isArray(data.items)) {
      return data.items;
    }

    if (Array.isArray(data.content)) {
      return data.content;
    }

    if (Array.isArray(data.data)) {
      return data.data;
    }
  }

  return [];
}

function normalizeObjectResponse<T>(responseBody: Response<T> | T | null | undefined): T {
  const data = unwrapData<T>(responseBody);

  if (data == null) {
    throw new Error('API response does not contain data');
  }

  return data;
}

function normalizeResearchLog(log: RawResearchLog): ResearchLog {
  return {
    id: log.id,
    projectId: log.projectId ?? log.project_id ?? 0,
    groupId: log.groupId ?? log.group_id ?? null,
    groupName: log.groupName ?? log.group_name ?? null,
    milestoneId: log.milestoneId ?? log.milestone_id ?? null,
    milestoneTitle: log.milestoneTitle ?? log.milestone_title ?? null,
    taskId: log.taskId ?? log.task_id ?? null,
    taskTitle: log.taskTitle ?? log.task_title ?? null,
    authorId: log.authorId ?? log.author_id ?? 0,
    authorName: log.authorName ?? log.author_name ?? null,
    authorRole: log.authorRole ?? log.author_role ?? null,
    groupRole: log.groupRole ?? log.group_role ?? null,
    logType: log.logType ?? log.log_type ?? 'MANUAL',
    workDate: log.workDate ?? log.work_date ?? '',
    durationMinutes: log.durationMinutes ?? log.duration_minutes ?? 0,
    content: log.content ?? '',
    result: log.result ?? null,
    problem: log.problem ?? null,
    nextPlan: log.nextPlan ?? log.next_plan ?? null,
    evidenceLink: log.evidenceLink ?? log.evidence_link ?? null,
    visibility: log.visibility ?? 'GROUP',
    createdAt: log.createdAt ?? log.created_at ?? null,
    updatedAt: log.updatedAt ?? log.updated_at ?? null,
  };
}

function normalizeEvaluation(evaluation: RawResearchEvaluation): ResearchEvaluation {
  return {
    id: evaluation.id,
    projectId: evaluation.projectId ?? evaluation.project_id ?? 0,
    groupId: evaluation.groupId ?? evaluation.group_id ?? null,
    groupName: evaluation.groupName ?? evaluation.group_name ?? null,
    studentId: evaluation.studentId ?? evaluation.student_id ?? 0,
    studentName: evaluation.studentName ?? evaluation.student_name ?? null,
    evaluatorId: evaluation.evaluatorId ?? evaluation.evaluator_id ?? null,
    evaluatorName: evaluation.evaluatorName ?? evaluation.evaluator_name ?? null,
    contributionScore: evaluation.contributionScore ?? evaluation.contribution_score ?? 0,
    taskScore: evaluation.taskScore ?? evaluation.task_score ?? 0,
    reportScore: evaluation.reportScore ?? evaluation.report_score ?? 0,
    productScore: evaluation.productScore ?? evaluation.product_score ?? 0,
    attitudeScore: evaluation.attitudeScore ?? evaluation.attitude_score ?? 0,
    totalScore: evaluation.totalScore ?? evaluation.total_score ?? 0,
    lecturerComment: evaluation.lecturerComment ?? evaluation.lecturer_comment ?? null,
    createdAt: evaluation.createdAt ?? evaluation.created_at ?? null,
    updatedAt: evaluation.updatedAt ?? evaluation.updated_at ?? null,
  };
}

function normalizeProduct(product: RawResearchProduct): ResearchProduct {
  return {
    id: product.id,
    projectId: product.projectId ?? product.project_id ?? 0,
    groupId: product.groupId ?? product.group_id ?? null,
    submittedById: product.submittedById ?? product.submitted_by_id ?? null,
    submittedByName: product.submittedByName ?? product.submitted_by_name ?? null,
    submittedByEmail: product.submittedByEmail ?? product.submitted_by_email ?? null,
    productType: product.productType ?? product.product_type ?? 'OTHER',
    title: product.title ?? 'San pham nghien cuu',
    description: product.description ?? null,
    fileUrl: product.fileUrl ?? product.file_url ?? null,
    fileName: product.fileName ?? product.file_name ?? null,
    fileType: product.fileType ?? product.file_type ?? null,
    fileSize: product.fileSize ?? product.file_size ?? null,
    externalLink: product.externalLink ?? product.external_link ?? null,
    version: product.version ?? 1,
    status: product.status ?? 'SUBMITTED',
    submittedAt: product.submittedAt ?? product.submitted_at ?? null,
    createdAt: product.createdAt ?? product.created_at ?? null,
    updatedAt: product.updatedAt ?? product.updated_at ?? null,
  };
}

export async function getResearchGroup(groupId: number): Promise<ResearchGroup> {
  assertValidGroupId(groupId);
  const response = await apiClient.get<Response<ResearchGroup>>(`/api/research-groups/${groupId}`);
  return normalizeObjectResponse<ResearchGroup>(response.data);
}

export async function getResearchGroupMembers(groupId: number): Promise<ResearchGroupMember[]> {
  assertValidGroupId(groupId);
  const response = await apiClient.get<Response<ResearchGroupMember[]>>(`/api/research-groups/${groupId}/members`);
  return normalizeListResponse<ResearchGroupMember>(response.data);
}

export async function getGroupMilestones(groupId: number): Promise<ResearchMilestone[]> {
  assertValidGroupId(groupId);
  const response = await apiClient.get<Response<ResearchMilestone[]>>(`/api/research-groups/${groupId}/milestones`);
  return normalizeListResponse<ResearchMilestone>(response.data);
}

export async function getMyGroupMilestones(groupId: number): Promise<ResearchMilestone[]> {
  assertValidGroupId(groupId);
  const response = await apiClient.get<Response<ResearchMilestone[]>>(`/api/research-groups/${groupId}/milestones/me`);
  return normalizeListResponse<ResearchMilestone>(response.data);
}

export async function getGroupTasks(groupId: number): Promise<ResearchTask[]> {
  assertValidGroupId(groupId);
  const response = await apiClient.get<Response<RawResearchTask[]>>(`/api/research-groups/${groupId}/tasks`);
  return normalizeListResponse<RawResearchTask>(response.data).map(normalizeTask);
}

export async function getMyGroupTasks(groupId: number): Promise<ResearchTask[]> {
  assertValidGroupId(groupId);
  const response = await apiClient.get<Response<RawResearchTask[]>>(`/api/research-groups/${groupId}/tasks/me`);
  return normalizeListResponse<RawResearchTask>(response.data).map(normalizeTask);
}

export async function getGroupReports(groupId: number): Promise<ResearchReport[]> {
  assertValidGroupId(groupId);
  const response = await apiClient.get<Response<ResearchReport[]>>(`/api/research-groups/${groupId}/reports`);
  return normalizeListResponse<ResearchReport>(response.data);
}

export async function getMyGroupReports(groupId: number): Promise<ResearchReport[]> {
  assertValidGroupId(groupId);
  const response = await apiClient.get<Response<ResearchReport[]>>(`/api/research-groups/${groupId}/reports/me`);
  return normalizeListResponse<ResearchReport>(response.data);
}

export async function getGroupProducts(groupId: number): Promise<ResearchProduct[]> {
  assertValidGroupId(groupId);
  const response = await apiClient.get<Response<RawResearchProduct[]>>(`/api/research-groups/${groupId}/products`);
  return normalizeListResponse<RawResearchProduct>(response.data).map(normalizeProduct);
}

export async function getGroupEvaluations(groupId: number): Promise<ResearchEvaluation[]> {
  assertValidGroupId(groupId);
  const response = await apiClient.get<Response<RawResearchEvaluation[]>>(
    `/api/research-groups/${groupId}/evaluations`,
  );
  return normalizeListResponse<RawResearchEvaluation>(response.data).map(normalizeEvaluation);
}

export async function getGroupResearchLogs(
  groupId: number,
  filters: ResearchLogFilters = {},
  page = 0,
  size = 20,
): Promise<ResearchLog[]> {
  assertValidGroupId(groupId);
  const response = await apiClient.get<Response<RawResearchLog[]>>(`/api/research-groups/${groupId}/logs`, {
    params: {
      milestoneId: filters.milestoneId || undefined,
      taskId: filters.taskId || undefined,
      authorId: filters.authorId || undefined,
      logType: filters.logType || undefined,
      page,
      size,
    },
  });
  return normalizeListResponse<RawResearchLog>(response.data).map(normalizeResearchLog);
}
