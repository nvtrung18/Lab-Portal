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
  ResearchEligibleStudent,
  ResearchGroup,
  ResearchProject,
  ResearchTopic,
} from '../types';

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

export async function getMilestonesByProject(projectId: number): Promise<ResearchMilestone[]> {
  const response = await apiClient.get<Response<ResearchMilestone[]>>(`/api/projects/${projectId}/milestones`);
  return response.data.data;
}

export async function getMilestone(milestoneId: number): Promise<ResearchMilestone> {
  const response = await apiClient.get<Response<ResearchMilestone>>(`/api/milestones/${milestoneId}`);
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
