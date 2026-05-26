import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { toast } from '../../../shared/components';
import {
  createGroup,
  createMilestone,
  updateMilestone,
  createProject,
  createResearchGroup,
  updateResearchGroup,
  createResearchProject,
  updateResearchProject,
  getResearchEligibleStudents,
  getResearchGroup,
  getResearchGroupsByProject,
  getMyResearchGroupsByLab,
  getMilestone,
  getMilestonesByProject,
  getTasksByMilestone,
  updateTaskStatus,
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
  UpdateResearchGroupPayload,
  CreateResearchProjectPayload,
  CreateMilestonePayload,
  UpdateMilestonePayload,
  CreateTopicPayload,
  ResearchTask,
  TaskColumn,
} from '../types';
import { updateTaskStatusInCache } from '../taskBoardHelpers';

export const RESEARCH_TOPICS_QUERY_KEY = ['researchTopics'] as const;
export const GROUPS_QUERY_KEY = ['groups'] as const;
export const PROJECTS_QUERY_KEY = ['projects'] as const;
export const RESEARCH_PROJECTS_QUERY_KEY = ['researchProjects'] as const;
export const STUDENT_RESEARCH_PROJECTS_QUERY_KEY = ['studentResearchProjects'] as const;
export const RESEARCH_PROJECT_QUERY_KEY = ['researchProject'] as const;
export const RESEARCH_GROUPS_QUERY_KEY = ['researchGroups'] as const;
export const RESEARCH_GROUP_QUERY_KEY = ['researchGroup'] as const;
export const RESEARCH_ELIGIBLE_STUDENTS_QUERY_KEY = ['researchEligibleStudents'] as const;
export const MY_RESEARCH_GROUPS_QUERY_KEY = ['myResearchGroups'] as const;
export const MILESTONES_QUERY_KEY = ['milestones'] as const;
export const MILESTONE_QUERY_KEY = ['milestone'] as const;
export const TASKS_QUERY_KEY = ['tasks'] as const;

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

export function useStudentResearchProjectsByLab(labId?: number | null) {
  return useQuery({
    queryKey: labId ? [...STUDENT_RESEARCH_PROJECTS_QUERY_KEY, labId] : STUDENT_RESEARCH_PROJECTS_QUERY_KEY,
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

export function useResearchGroup(groupId?: number | null) {
  return useQuery({
    queryKey: groupId ? [...RESEARCH_GROUP_QUERY_KEY, groupId] : RESEARCH_GROUP_QUERY_KEY,
    queryFn: () => getResearchGroup(groupId as number),
    enabled: Boolean(groupId),
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

export function useMyResearchGroups(labId?: number | null) {
  return useQuery({
    queryKey: labId ? [...MY_RESEARCH_GROUPS_QUERY_KEY, labId] : MY_RESEARCH_GROUPS_QUERY_KEY,
    queryFn: () => getMyResearchGroupsByLab(labId as number),
    enabled: Boolean(labId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useMilestonesByProject(projectId?: number | null) {
  return useQuery({
    queryKey: projectId ? [...MILESTONES_QUERY_KEY, projectId] : MILESTONES_QUERY_KEY,
    queryFn: () => getMilestonesByProject(projectId as number),
    enabled: Boolean(projectId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useMilestone(milestoneId?: number | null) {
  return useQuery({
    queryKey: milestoneId ? [...MILESTONE_QUERY_KEY, milestoneId] : MILESTONE_QUERY_KEY,
    queryFn: () => getMilestone(milestoneId as number),
    enabled: Boolean(milestoneId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useTasksByMilestone(milestoneId?: number | null) {
  return useQuery({
    queryKey: milestoneId ? [...TASKS_QUERY_KEY, milestoneId] : TASKS_QUERY_KEY,
    queryFn: () => getTasksByMilestone(milestoneId as number),
    enabled: Boolean(milestoneId),
    staleTime: 30000,
    refetchOnWindowFocus: true,
  });
}

export function useUpdateTaskStatus(milestoneId: number) {
  const queryClient = useQueryClient();
  const queryKey = [...TASKS_QUERY_KEY, milestoneId] as const;

  return useMutation({
    mutationFn: ({ taskId, status }: { taskId: number; status: TaskColumn }) =>
      updateTaskStatus(taskId, status),
    onMutate: async ({ taskId, status }) => {
      await queryClient.cancelQueries({ queryKey });
      const previousTasks = queryClient.getQueryData<ResearchTask[]>(queryKey);

      queryClient.setQueryData<ResearchTask[]>(queryKey, (currentTasks = []) =>
        updateTaskStatusInCache(currentTasks, taskId, status),
      );

      return { previousTasks };
    },
    onError: (_error, _variables, context) => {
      if (context?.previousTasks) {
        queryClient.setQueryData(queryKey, context.previousTasks);
      }
      toast.error('Không thể cập nhật trạng thái nhiệm vụ. Dữ liệu đã được khôi phục.');
    },
    onSuccess: (updatedTask) => {
      queryClient.setQueryData<ResearchTask[]>(queryKey, (currentTasks = []) =>
        currentTasks.map((task) => (task.id === updatedTask.id ? updatedTask : task)),
      );
      toast.success('Đã cập nhật trạng thái nhiệm vụ.');
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey });
    },
  });
}

export function useCreateMilestone(projectId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateMilestonePayload) => createMilestone(payload),
    onSuccess: async (_milestone, payload) => {
      const targetProjectId = projectId ?? payload.projectId;
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: [...MILESTONES_QUERY_KEY, targetProjectId],
        }),
        queryClient.invalidateQueries({
          queryKey: [...RESEARCH_PROJECT_QUERY_KEY, targetProjectId],
        }),
      ]);
      toast.success('Đã tạo mốc nghiên cứu thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể tạo mốc nghiên cứu.'));
    },
  });
}

export function useUpdateMilestone(projectId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ milestoneId, payload }: { milestoneId: number; payload: UpdateMilestonePayload }) =>
      updateMilestone(milestoneId, payload),
    onSuccess: async (_milestone, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: [...MILESTONES_QUERY_KEY, projectId] }),
        queryClient.invalidateQueries({ queryKey: [...MILESTONE_QUERY_KEY, variables.milestoneId] }),
        queryClient.invalidateQueries({ queryKey: [...RESEARCH_PROJECT_QUERY_KEY, projectId] }),
      ]);
      toast.success('Đã cập nhật mốc nghiên cứu thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể cập nhật mốc nghiên cứu.'));
    },
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

export function useUpdateResearchProject(labId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ projectId, payload }: { projectId: number; payload: CreateResearchProjectPayload }) =>
      updateResearchProject(projectId, payload),
    onSuccess: async (_project, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: [...RESEARCH_PROJECTS_QUERY_KEY, labId ?? variables.payload.labId],
        }),
        queryClient.invalidateQueries({
          queryKey: [...RESEARCH_PROJECT_QUERY_KEY, variables.projectId],
        }),
      ]);
      toast.success('Đã cập nhật đề tài nghiên cứu thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể cập nhật đề tài nghiên cứu.'));
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
        queryClient.invalidateQueries({ queryKey: [...RESEARCH_PROJECT_QUERY_KEY, projectId ?? payload.projectId] }),
        labId
          ? queryClient.invalidateQueries({ queryKey: [...RESEARCH_PROJECTS_QUERY_KEY, labId] })
          : Promise.resolve(),
      ]);
      toast.success('Đã tạo nhóm nghiên cứu thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể tạo nhóm nghiên cứu.'));
    },
  });
}

export function useUpdateResearchGroup(projectId?: number | null, labId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ groupId, payload }: { groupId: number; payload: UpdateResearchGroupPayload }) =>
      updateResearchGroup(groupId, payload),
    onSuccess: async (_group, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: [...RESEARCH_GROUPS_QUERY_KEY, projectId] }),
        queryClient.invalidateQueries({ queryKey: [...RESEARCH_GROUP_QUERY_KEY, variables.groupId] }),
        queryClient.invalidateQueries({ queryKey: [...RESEARCH_PROJECT_QUERY_KEY, projectId] }),
        labId
          ? queryClient.invalidateQueries({ queryKey: [...RESEARCH_PROJECTS_QUERY_KEY, labId] })
          : Promise.resolve(),
        labId
          ? queryClient.invalidateQueries({ queryKey: [...MY_RESEARCH_GROUPS_QUERY_KEY, labId] })
          : Promise.resolve(),
      ]);
      toast.success('Đã cập nhật nhóm nghiên cứu thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể cập nhật nhóm nghiên cứu.'));
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
