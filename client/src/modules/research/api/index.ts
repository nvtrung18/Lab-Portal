import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';
import type {
  CreateGroupPayload,
  CreateProjectPayload,
  CreateResearchGroupPayload,
  CreateResearchProjectPayload,
  CreateTopicPayload,
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

export async function getResearchProject(projectId: number): Promise<ResearchProject> {
  const response = await apiClient.get<Response<ResearchProject>>(`/api/research-projects/${projectId}`);
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

export async function getMyResearchGroups(): Promise<ResearchGroup[]> {
  const response = await apiClient.get<Response<ResearchGroup[]>>('/api/research-groups/me');
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

export async function getProjectsByGroup(groupId: number): Promise<ResearchProject[]> {
  const response = await apiClient.get<Response<ResearchProject[]>>(`/api/groups/${groupId}/projects`);
  return response.data.data;
}

export async function createProject(payload: CreateProjectPayload): Promise<ResearchProject> {
  const response = await apiClient.post<Response<ResearchProject>>('/api/projects', payload);
  return response.data.data;
}
