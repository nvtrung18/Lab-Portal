import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { toast } from '../../../shared/components';
import {
  createGroup,
  createProject,
  createResearchGroup,
  createResearchProject,
  getResearchEligibleStudents,
  getResearchGroupsByProject,
  getMyResearchGroups,
  getResearchProject,
  getResearchProjectsByLab,
  createResearchTopic,
  getGroupsByTopic,
  getProjectsByGroup,
  getResearchTopicsByLab,
} from '../api';
import type {
  CreateGroupPayload,
  CreateProjectPayload,
  CreateResearchGroupPayload,
  CreateResearchProjectPayload,
  CreateTopicPayload,
} from '../types';

export const RESEARCH_TOPICS_QUERY_KEY = ['researchTopics'] as const;
export const GROUPS_QUERY_KEY = ['groups'] as const;
export const PROJECTS_QUERY_KEY = ['projects'] as const;
export const RESEARCH_PROJECTS_QUERY_KEY = ['researchProjects'] as const;
export const RESEARCH_PROJECT_QUERY_KEY = ['researchProject'] as const;
export const RESEARCH_GROUPS_QUERY_KEY = ['researchGroups'] as const;
export const RESEARCH_ELIGIBLE_STUDENTS_QUERY_KEY = ['researchEligibleStudents'] as const;
export const MY_RESEARCH_GROUPS_QUERY_KEY = ['myResearchGroups'] as const;

function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; errors?: string[] } | undefined;
    return data?.message ?? data?.errors?.[0] ?? fallback;
  }
  return fallback;
}

export function useResearchTopicsByLab(labId?: number | null) {
  return useQuery({
    queryKey: labId ? [...RESEARCH_TOPICS_QUERY_KEY, labId] : RESEARCH_TOPICS_QUERY_KEY,
    queryFn: () => getResearchTopicsByLab(labId as number),
    enabled: Boolean(labId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useCreateResearchTopic(labId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateTopicPayload) => createResearchTopic(payload),
    onSuccess: async (_topic, payload) => {
      await queryClient.invalidateQueries({ queryKey: [...RESEARCH_TOPICS_QUERY_KEY, labId ?? payload.labId] });
      toast.success('Đã tạo chủ đề nghiên cứu thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể tạo chủ đề nghiên cứu.'));
    },
  });
}

export function useGroupsByTopic(labId?: number | null, topicId?: number | null) {
  return useQuery({
    queryKey: labId && topicId ? [...GROUPS_QUERY_KEY, labId, topicId] : GROUPS_QUERY_KEY,
    queryFn: () => getGroupsByTopic(topicId as number),
    enabled: Boolean(labId && topicId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useCreateGroup(labId?: number | null, topicId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateGroupPayload) => createGroup(payload),
    onSuccess: async (_group, payload) => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: [...GROUPS_QUERY_KEY, labId ?? payload.labId, topicId ?? payload.topicId],
        }),
        queryClient.invalidateQueries({ queryKey: [...RESEARCH_TOPICS_QUERY_KEY, labId ?? payload.labId] }),
      ]);
      toast.success('Đã tạo nhóm nghiên cứu thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể tạo nhóm nghiên cứu.'));
    },
  });
}

export function useProjectsByGroup(groupId?: number | null) {
  return useQuery({
    queryKey: groupId ? [...PROJECTS_QUERY_KEY, groupId] : PROJECTS_QUERY_KEY,
    queryFn: () => getProjectsByGroup(groupId as number),
    enabled: Boolean(groupId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useResearchProjectsByLab(labId?: number | null) {
  return useQuery({
    queryKey: labId ? [...RESEARCH_PROJECTS_QUERY_KEY, labId] : RESEARCH_PROJECTS_QUERY_KEY,
    queryFn: () => getResearchProjectsByLab(labId as number),
    enabled: Boolean(labId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useResearchProject(projectId?: number | null) {
  return useQuery({
    queryKey: projectId ? [...RESEARCH_PROJECT_QUERY_KEY, projectId] : RESEARCH_PROJECT_QUERY_KEY,
    queryFn: () => getResearchProject(projectId as number),
    enabled: Boolean(projectId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useResearchGroupsByProject(projectId?: number | null) {
  return useQuery({
    queryKey: projectId ? [...RESEARCH_GROUPS_QUERY_KEY, projectId] : RESEARCH_GROUPS_QUERY_KEY,
    queryFn: () => getResearchGroupsByProject(projectId as number),
    enabled: Boolean(projectId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useResearchEligibleStudents(labId?: number | null) {
  return useQuery({
    queryKey: labId ? [...RESEARCH_ELIGIBLE_STUDENTS_QUERY_KEY, labId] : RESEARCH_ELIGIBLE_STUDENTS_QUERY_KEY,
    queryFn: () => getResearchEligibleStudents(labId as number),
    enabled: Boolean(labId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useMyResearchGroups() {
  return useQuery({
    queryKey: MY_RESEARCH_GROUPS_QUERY_KEY,
    queryFn: getMyResearchGroups,
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useCreateResearchProject(labId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateResearchProjectPayload) => createResearchProject(payload),
    onSuccess: async (_project, payload) => {
      await queryClient.invalidateQueries({
        queryKey: [...RESEARCH_PROJECTS_QUERY_KEY, labId ?? payload.labId],
      });
      toast.success('Đã tạo đề tài nghiên cứu thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể tạo đề tài nghiên cứu.'));
    },
  });
}

export function useCreateResearchGroup(projectId?: number | null, labId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateResearchGroupPayload) => createResearchGroup(payload),
    onSuccess: async (_group, payload) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: [...RESEARCH_GROUPS_QUERY_KEY, projectId ?? payload.projectId] }),
        labId
          ? queryClient.invalidateQueries({ queryKey: [...RESEARCH_PROJECTS_QUERY_KEY, labId] })
          : Promise.resolve(),
      ]);
      toast.success('Da tao nhom nghien cuu thanh cong.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Khong the tao nhom nghien cuu.'));
    },
  });
}

export function useCreateProject(
  groupId?: number | null,
  labId?: number | null,
  topicId?: number | null,
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateProjectPayload) => createProject(payload),
    onSuccess: async (_project, payload) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: [...PROJECTS_QUERY_KEY, groupId ?? payload.groupId] }),
        labId && topicId
          ? queryClient.invalidateQueries({ queryKey: [...GROUPS_QUERY_KEY, labId, topicId] })
          : Promise.resolve(),
      ]);
      toast.success('Đã tạo đề tài nghiên cứu thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể tạo đề tài nghiên cứu.'));
    },
  });
}
