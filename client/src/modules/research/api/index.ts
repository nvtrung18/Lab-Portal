import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';
import type {
  CreateGroupPayload,
  CreateProjectPayload,
  CreateResearchGroupPayload,
  UpdateResearchGroupPayload,
  CreateResearchProjectPayload,
  CreateMilestonePayload,
  UpdateMilestonePayload,
  CreateTopicPayload,
  ResearchMilestone,
  RawResearchTask,
  ResearchTask,
  TaskColumn,
  ResearchEligibleStudent,
  ResearchGroup,
  ResearchProject,
  ResearchProduct,
  RawResearchProduct,
  ResearchTopic,
  ResearchReport,
  ResearchReportComment,
  SubmitProductPayload,
  SubmitReportPayload,
  ManagerReportDecision,
} from '../types';
import { normalizeTask } from '../taskBoardHelpers';

export async function getResearchProjectsByLab(labId: number): Promise<ResearchProject[]> {
  const response = await apiClient.get<Response<ResearchProject[]>>(`/api/labs/${labId}/research-projects`);
  return response.data.data;
}

export async function createResearchProject(payload: CreateResearchProjectPayload): Promise<ResearchProject> {
  const response = await apiClient.post<Response<ResearchProject>>('/api/research-projects', payload);
  return response.data.data;
}

export async function updateResearchProject(
  projectId: number,
  payload: CreateResearchProjectPayload,
): Promise<ResearchProject> {
  const response = await apiClient.put<Response<ResearchProject>>(`/api/research-projects/${projectId}`, payload);
  return response.data.data;
}

export async function getResearchProject(projectId: number): Promise<ResearchProject> {
  const response = await apiClient.get<Response<ResearchProject>>(`/api/research-projects/${projectId}`);
  return response.data.data;
}

export async function getProductsByProject(projectId: number): Promise<ResearchProduct[]> {
  const response = await apiClient.get<Response<RawResearchProduct[]>>(`/api/projects/${projectId}/products`);
  return response.data.data.map(normalizeProduct);
}

export async function submitProduct(
  payload: SubmitProductPayload,
  onUploadProgress?: (percent: number) => void,
): Promise<ResearchProduct> {
  const formData = new FormData();
  formData.append('projectId', String(payload.projectId));
  if (payload.groupId) {
    formData.append('groupId', String(payload.groupId));
  }
  formData.append('productType', payload.productType);
  formData.append('title', payload.title);
  if (payload.description) {
    formData.append('description', payload.description);
  }
  if (payload.externalLink) {
    formData.append('externalLink', payload.externalLink);
  }
  if (payload.file) {
    formData.append('file', payload.file);
  }

  const response = await apiClient.post<Response<RawResearchProduct>>('/api/products', formData, {
    onUploadProgress: (event) => {
      if (event.total) {
        onUploadProgress?.(Math.min(100, Math.round((event.loaded * 100) / event.total)));
      }
    },
  });
  return normalizeProduct(response.data.data);
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
    title: product.title ?? 'Sản phẩm nghiên cứu',
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

export async function getMilestonesByProject(projectId: number): Promise<ResearchMilestone[]> {
  const response = await apiClient.get<Response<ResearchMilestone[]>>(`/api/projects/${projectId}/milestones`);
  return response.data.data;
}

export async function getMilestone(milestoneId: number): Promise<ResearchMilestone> {
  const response = await apiClient.get<Response<ResearchMilestone>>(`/api/milestones/${milestoneId}`);
  return response.data.data;
}

export async function getTasksByMilestone(milestoneId: number): Promise<ResearchTask[]> {
  const response = await apiClient.get<Response<RawResearchTask[]>>(`/api/milestones/${milestoneId}/tasks`);
  return response.data.data.map(normalizeTask);
}

export async function updateTaskStatus(taskId: number, status: TaskColumn): Promise<ResearchTask> {
  const response = await apiClient.put<Response<RawResearchTask>>(`/api/tasks/${taskId}/status`, { status });
  return normalizeTask(response.data.data);
}

export async function getReportsByMilestone(milestoneId: number): Promise<ResearchReport[]> {
  const response = await apiClient.get<Response<ResearchReport[]>>(`/api/milestones/${milestoneId}/reports`);
  return response.data.data;
}

export async function getMyReportsByMilestone(milestoneId: number): Promise<ResearchReport[]> {
  const response = await apiClient.get<Response<ResearchReport[]>>(`/api/milestones/${milestoneId}/reports/me`);
  return response.data.data;
}

export async function getReportsByTask(taskId: number): Promise<ResearchReport[]> {
  const response = await apiClient.get<Response<ResearchReport[]>>(`/api/tasks/${taskId}/reports`);
  return response.data.data;
}

export async function downloadReportFile(reportId: number): Promise<Blob> {
  const response = await apiClient.get<Blob>(`/api/reports/${reportId}/file`, {
    responseType: 'blob',
  });
  return response.data;
}

export async function getReportsByGroup(groupId: number): Promise<ResearchReport[]> {
  const response = await apiClient.get<Response<ResearchReport[]>>(`/api/groups/${groupId}/reports`);
  return response.data.data;
}

export async function getPendingManagerReports(labId: number): Promise<ResearchReport[]> {
  const response = await apiClient.get<Response<ResearchReport[]>>(`/api/labs/${labId}/reports/pending-review`);
  return response.data.data;
}

export async function getMyResearchTasksByGroup(groupId: number): Promise<ResearchTask[]> {
  const response = await apiClient.get<Response<RawResearchTask[]>>(`/api/groups/${groupId}/tasks`);
  return response.data.data.map(normalizeTask);
}

export async function submitReport(
  payload: SubmitReportPayload,
  onUploadProgress?: (percent: number) => void,
): Promise<ResearchReport> {
  const formData = new FormData();
  formData.append('taskId', String(payload.taskId));
  formData.append('title', payload.title);
  formData.append('contentDone', payload.contentDone);
  formData.append('result', payload.result);
  formData.append('difficulty', payload.difficulty);
  formData.append('nextPlan', payload.nextPlan);
  formData.append('selfAssessment', payload.selfAssessment);
  if (payload.evidenceLink) {
    formData.append('evidenceLink', payload.evidenceLink);
  }
  formData.append('file', payload.file);

  const response = await apiClient.post<Response<ResearchReport>>('/api/reports', formData, {
    onUploadProgress: (event) => {
      if (event.total) {
        onUploadProgress?.(Math.min(100, Math.round((event.loaded * 100) / event.total)));
      }
    },
  });
  return response.data.data;
}

export async function getReportComments(reportId: number): Promise<ResearchReportComment[]> {
  const response = await apiClient.get<Response<ResearchReportComment[]>>(`/api/reports/${reportId}/comments`);
  return response.data.data;
}

export async function addReportComment(reportId: number, content: string): Promise<ResearchReportComment> {
  const response = await apiClient.post<Response<ResearchReportComment>>(`/api/reports/${reportId}/comments`, {
    content,
  });
  return response.data.data;
}

export async function leaderReviewReport(reportId: number, note: string): Promise<ResearchReport> {
  const response = await apiClient.patch<Response<ResearchReport>>(`/api/reports/${reportId}/leader-review`, {
    note,
  });
  return response.data.data;
}

export async function managerReviewReport(
  reportId: number,
  decision: ManagerReportDecision,
  comment: string,
): Promise<ResearchReport> {
  const response = await apiClient.patch<Response<ResearchReport>>(`/api/reports/${reportId}/manager-review`, {
    decision,
    comment,
  });
  return response.data.data;
}

export async function createMilestone(payload: CreateMilestonePayload): Promise<ResearchMilestone> {
  const response = await apiClient.post<Response<ResearchMilestone>>('/api/milestones', payload);
  return response.data.data;
}

export async function updateMilestone(
  milestoneId: number,
  payload: UpdateMilestonePayload,
): Promise<ResearchMilestone> {
  const response = await apiClient.put<Response<ResearchMilestone>>(`/api/milestones/${milestoneId}`, payload);
  return response.data.data;
}

export async function getResearchTopicsByLab(labId: number): Promise<ResearchTopic[]> {
  const response = await apiClient.get<Response<ResearchTopic[]>>(`/api/labs/${labId}/research-topics`);
  return response.data.data;
}

export async function createResearchTopic(payload: CreateTopicPayload): Promise<ResearchTopic> {
  const response = await apiClient.post<Response<ResearchTopic>>('/api/research-topics', payload);
  return response.data.data;
}

export async function getGroupsByTopic(topicId: number): Promise<ResearchGroup[]> {
  const response = await apiClient.get<Response<ResearchGroup[]>>(`/api/research-topics/${topicId}/groups`);
  return response.data.data;
}

export async function getGroupsByLab(labId: number): Promise<ResearchGroup[]> {
  const response = await apiClient.get<Response<ResearchGroup[]>>(`/api/labs/${labId}/groups`);
  return response.data.data;
}

export async function getResearchGroupsByProject(projectId: number): Promise<ResearchGroup[]> {
  const response = await apiClient.get<Response<ResearchGroup[]>>(`/api/research-projects/${projectId}/groups`);
  return response.data.data;
}

export async function getResearchGroup(groupId: number): Promise<ResearchGroup> {
  const response = await apiClient.get<Response<ResearchGroup>>(`/api/research-groups/${groupId}`);
  return response.data.data;
}

export async function getMyResearchGroupsByLab(labId: number): Promise<ResearchGroup[]> {
  const response = await apiClient.get<Response<ResearchGroup[]>>(`/api/labs/${labId}/research-groups/me`);
  return response.data.data;
}

export async function getResearchEligibleStudents(labId: number): Promise<ResearchEligibleStudent[]> {
  const response = await apiClient.get<Response<ResearchEligibleStudent[]>>(
    `/api/labs/${labId}/research-eligible-students`,
  );
  return response.data.data;
}

export async function createGroup(payload: CreateGroupPayload): Promise<ResearchGroup> {
  const response = await apiClient.post<Response<ResearchGroup>>('/api/groups', payload);
  return response.data.data;
}

export async function createResearchGroup(payload: CreateResearchGroupPayload): Promise<ResearchGroup> {
  const response = await apiClient.post<Response<ResearchGroup>>('/api/research-groups', payload);
  return response.data.data;
}

export async function updateResearchGroup(
  groupId: number,
  payload: UpdateResearchGroupPayload,
): Promise<ResearchGroup> {
  const response = await apiClient.put<Response<ResearchGroup>>(`/api/research-groups/${groupId}`, payload);
  return response.data.data;
}

export async function getProjectsByGroup(groupId: number): Promise<ResearchProject[]> {
  const response = await apiClient.get<Response<ResearchProject[]>>(`/api/groups/${groupId}/projects`);
  return response.data.data;
}

export async function createProject(payload: CreateProjectPayload): Promise<ResearchProject> {
  const response = await apiClient.post<Response<ResearchProject>>('/api/projects', payload);
  return response.data.data;
}
