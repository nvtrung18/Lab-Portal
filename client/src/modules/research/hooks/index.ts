import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
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
  getReportsByMilestone,
  getMyReportsByMilestone,
  getReportsByTask,
  getReportsByGroup,
  getPendingManagerReports,
  getMyResearchTasksByGroup,
  getReportComments,
  addReportComment,
  leaderReviewReport,
  managerReviewReport,
  updateTaskStatus,
  getResearchProject,
  getProductsByProject,
  submitProduct,
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
  SubmitProductPayload,
  ResearchTask,
  TaskColumn,
  ManagerReportDecision,
} from '../types';
import { updateTaskStatusInCache } from '../taskBoardHelpers';

function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; errors?: string[] } | undefined;
    return data?.message ?? data?.errors?.[0] ?? fallback;
  }
  return fallback;
}

export function useResearchTopicsByLab(labId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.topics(labId as number),
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
      await queryClient.invalidateQueries({ queryKey: queryKeys.research.topics(labId ?? payload.labId) });
      toast.success('Đã tạo chủ đề nghiên cứu thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể tạo chủ đề nghiên cứu.'));
    },
  });
}

export function useGroupsByTopic(labId?: number | null, topicId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.topicGroups(labId as number, topicId as number),
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
          queryKey: queryKeys.research.topicGroups(labId ?? payload.labId, topicId ?? payload.topicId),
        }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.topics(labId ?? payload.labId) }),
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
    queryKey: queryKeys.research.groupProjects(groupId as number),
    queryFn: () => getProjectsByGroup(groupId as number),
    enabled: Boolean(groupId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useResearchProjectsByLab(labId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.projects(labId as number),
    queryFn: () => getResearchProjectsByLab(labId as number),
    enabled: Boolean(labId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useStudentResearchProjectsByLab(labId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.studentProjects(labId as number),
    queryFn: () => getResearchProjectsByLab(labId as number),
    enabled: Boolean(labId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useResearchProject(projectId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.project(projectId as number),
    queryFn: () => getResearchProject(projectId as number),
    enabled: Boolean(projectId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useProductsByProject(projectId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.products(projectId as number),
    queryFn: () => getProductsByProject(projectId as number),
    enabled: Boolean(projectId),
    staleTime: 30000,
    refetchOnWindowFocus: true,
  });
}

export function useSubmitProduct(projectId?: number | null, onUploadProgress?: (percent: number) => void) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: SubmitProductPayload) => submitProduct(payload, onUploadProgress),
    onSuccess: async (_product, payload) => {
      const targetProjectId = projectId ?? payload.projectId;
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.research.products(targetProjectId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.project(targetProjectId) }),
      ]);
      toast.success('Đã nộp sản phẩm nghiên cứu.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể nộp sản phẩm nghiên cứu.'));
    },
  });
}

export function useResearchGroupsByProject(projectId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.groups(projectId as number),
    queryFn: () => getResearchGroupsByProject(projectId as number),
    enabled: Boolean(projectId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useResearchGroup(groupId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.group(groupId as number),
    queryFn: () => getResearchGroup(groupId as number),
    enabled: Boolean(groupId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useResearchEligibleStudents(labId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.eligibleStudents(labId as number),
    queryFn: () => getResearchEligibleStudents(labId as number),
    enabled: Boolean(labId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useMyResearchGroups(labId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.myGroups(labId as number),
    queryFn: () => getMyResearchGroupsByLab(labId as number),
    enabled: Boolean(labId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useMilestonesByProject(projectId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.milestones(projectId as number),
    queryFn: () => getMilestonesByProject(projectId as number),
    enabled: Boolean(projectId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useMilestone(milestoneId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.milestone(milestoneId as number),
    queryFn: () => getMilestone(milestoneId as number),
    enabled: Boolean(milestoneId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useTasksByMilestone(milestoneId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.tasks(milestoneId as number),
    queryFn: () => getTasksByMilestone(milestoneId as number),
    enabled: Boolean(milestoneId),
    staleTime: 30000,
    refetchOnWindowFocus: true,
  });
}

export function useReportsByMilestone(milestoneId?: number | null, mine = false) {
  return useQuery({
    queryKey: mine
      ? queryKeys.research.myMilestoneReports(milestoneId as number)
      : queryKeys.research.reports(milestoneId as number),
    queryFn: () => mine
      ? getMyReportsByMilestone(milestoneId as number)
      : getReportsByMilestone(milestoneId as number),
    enabled: Boolean(milestoneId),
    staleTime: 30000,
    refetchOnWindowFocus: true,
  });
}

export function useReportsByTask(taskId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.taskReports(taskId as number),
    queryFn: () => getReportsByTask(taskId as number),
    enabled: Boolean(taskId),
    staleTime: 30000,
    refetchOnWindowFocus: true,
  });
}

export function useGroupReports(groupId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.groupReports(groupId as number),
    queryFn: () => getReportsByGroup(groupId as number),
    enabled: Boolean(groupId),
    staleTime: 30000,
    refetchOnWindowFocus: true,
  });
}

export function usePendingManagerReports(labId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.managerReports(labId as number),
    queryFn: () => getPendingManagerReports(labId as number),
    enabled: Boolean(labId),
    staleTime: 30000,
    refetchOnWindowFocus: true,
  });
}

export function useMyResearchTasks(groupId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.myTasks(groupId as number),
    queryFn: () => getMyResearchTasksByGroup(groupId as number),
    enabled: Boolean(groupId),
    staleTime: 30000,
    refetchOnWindowFocus: true,
  });
}

export function useReportComments(reportId: number) {
  return useQuery({
    queryKey: queryKeys.research.reportComments(reportId),
    queryFn: () => getReportComments(reportId),
    staleTime: 30000,
  });
}

export function useAddReportComment(reportId: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (content: string) => addReportComment(reportId, content),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.research.reportComments(reportId) });
      toast.success('Đã gửi nhận xét báo cáo.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể gửi nhận xét báo cáo.'));
    },
  });
}

function invalidateReviewedReport(
  queryClient: ReturnType<typeof useQueryClient>,
  reportId: number,
  milestoneId: number,
  projectId: number,
  groupId?: number | null,
  taskId?: number | null,
) {
  const invalidations = [
    queryClient.invalidateQueries({ queryKey: queryKeys.research.reports(milestoneId) }),
    queryClient.invalidateQueries({ queryKey: queryKeys.research.reportComments(reportId) }),
    queryClient.invalidateQueries({ queryKey: queryKeys.research.tasks(milestoneId) }),
    queryClient.invalidateQueries({ queryKey: queryKeys.research.milestones(projectId) }),
    queryClient.invalidateQueries({ queryKey: queryKeys.research.milestone(milestoneId) }),
  ];
  if (groupId) {
    invalidations.push(queryClient.invalidateQueries({ queryKey: queryKeys.research.groupReports(groupId) }));
  }
  if (taskId) {
    invalidations.push(queryClient.invalidateQueries({ queryKey: queryKeys.research.taskReports(taskId) }));
  }
  return Promise.all(invalidations);
}

export function useLeaderReviewReport(
  reportId: number,
  milestoneId: number,
  projectId: number,
  groupId?: number | null,
  taskId?: number | null,
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (note: string) => leaderReviewReport(reportId, note),
    onSuccess: async () => {
      await invalidateReviewedReport(queryClient, reportId, milestoneId, projectId, groupId, taskId);
      toast.success('Đã đánh dấu báo cáo đã kiểm tra.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể kiểm tra báo cáo.'));
    },
  });
}

export function useManagerReviewReport(
  reportId: number,
  milestoneId: number,
  projectId: number,
  labId?: number | null,
  taskId?: number | null,
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ decision, comment }: { decision: ManagerReportDecision; comment: string }) =>
      managerReviewReport(reportId, decision, comment),
    onSuccess: async (_report, variables) => {
      await invalidateReviewedReport(queryClient, reportId, milestoneId, projectId, undefined, taskId);
      if (labId) {
        await queryClient.invalidateQueries({ queryKey: queryKeys.research.managerReports(labId) });
      }
      toast.success(
        variables.decision === 'APPROVE'
          ? 'Đã duyệt báo cáo.'
          : variables.decision === 'REQUEST_REVISION'
            ? 'Đã yêu cầu chỉnh sửa báo cáo.'
            : 'Đã từ chối báo cáo.',
      );
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể duyệt báo cáo.'));
    },
  });
}

export function useUpdateTaskStatus(milestoneId: number) {
  const queryClient = useQueryClient();
  const queryKey = queryKeys.research.tasks(milestoneId);

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
    onError: (error, _variables, context) => {
      if (context?.previousTasks) {
        queryClient.setQueryData(queryKey, context.previousTasks);
      }
      toast.error(getErrorMessage(error, 'Không thể cập nhật trạng thái nhiệm vụ. Dữ liệu đã được khôi phục.'));
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
          queryKey: queryKeys.research.milestones(targetProjectId),
        }),
        queryClient.invalidateQueries({
          queryKey: queryKeys.research.project(targetProjectId),
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
        queryClient.invalidateQueries({ queryKey: queryKeys.research.milestones(projectId as number) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.milestone(variables.milestoneId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.project(projectId as number) }),
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
        queryKey: queryKeys.research.projects(labId ?? payload.labId),
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
          queryKey: queryKeys.research.projects(labId ?? variables.payload.labId),
        }),
        queryClient.invalidateQueries({
          queryKey: queryKeys.research.project(variables.projectId),
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
        queryClient.invalidateQueries({ queryKey: queryKeys.research.groups(projectId ?? payload.projectId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.project(projectId ?? payload.projectId) }),
        labId
          ? queryClient.invalidateQueries({ queryKey: queryKeys.research.projects(labId) })
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
        queryClient.invalidateQueries({ queryKey: queryKeys.research.groups(projectId as number) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.group(variables.groupId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.project(projectId as number) }),
        labId
          ? queryClient.invalidateQueries({ queryKey: queryKeys.research.projects(labId) })
          : Promise.resolve(),
        labId
          ? queryClient.invalidateQueries({ queryKey: queryKeys.research.myGroups(labId) })
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
        queryClient.invalidateQueries({ queryKey: queryKeys.research.groupProjects(groupId ?? payload.groupId) }),
        labId && topicId
          ? queryClient.invalidateQueries({ queryKey: queryKeys.research.topicGroups(labId, topicId) })
          : Promise.resolve(),
      ]);
      toast.success('Đã tạo đề tài nghiên cứu thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể tạo đề tài nghiên cứu.'));
    },
  });
}
