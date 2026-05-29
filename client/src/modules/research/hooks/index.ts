import axios from 'axios';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

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
  createResearchLog,
  getResearchEligibleStudents,
  getResearchGroupsByProject,
  getMyResearchGroupsByLab,
  getMilestone,
  getMilestonesByProject,
  getTasksByMilestone,
  getReportsByMilestone,
  getMyReportsByMilestone,
  getReportsByTask,
  getPendingManagerReports,
  getReportComments,
  addReportComment,
  leaderReviewReport,
  managerReviewReport,
  updateTaskStatus,
  createTask,
  getResearchProject,
  getProjectDashboardStats,
  getResearchLogs,
  getEvaluationsByProject,
  submitEvaluation,
  getProductsByProject,
  submitProduct,
  getResearchProjectsByLab,
  createResearchTopic,
  getGroupsByTopic,
  getProjectsByGroup,
  getResearchTopicsByLab,
} from '../api';
import {
  getGroupEvaluations,
  getGroupMilestones,
  getGroupProducts,
  getGroupReports,
  getGroupResearchLogs,
  getGroupTasks,
  getMyGroupMilestones,
  getMyGroupReports,
  getMyGroupTasks,
  getResearchGroup,
  getResearchGroupMembers,
} from '../api/researchGroupApi';
import type {
  CreateGroupPayload,
  CreateProjectPayload,
  CreateResearchGroupPayload,
  UpdateResearchGroupPayload,
  CreateResearchProjectPayload,
  CreateResearchLogPayload,
  CreateMilestonePayload,
  CreateTaskPayload,
  UpdateMilestonePayload,
  CreateTopicPayload,
  SubmitProductPayload,
  SubmitEvaluationPayload,
  ResearchTask,
  TaskColumn,
  ManagerReportDecision,
  ResearchLogFilters,
} from '../types';
import { updateTaskStatusInCache } from '../taskBoardHelpers';

function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; errors?: string[] } | undefined;
    return data?.message ?? data?.errors?.[0] ?? fallback;
  }
  return fallback;
}

function isValidId(value?: number | null) {
  return typeof value === 'number' && Number.isFinite(value) && value > 0;
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

export function useProjectDashboardStats(projectId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.projectStats(projectId as number),
    queryFn: () => getProjectDashboardStats(projectId as number),
    enabled: Boolean(projectId),
    retry: (failureCount, error) => {
      if (axios.isAxiosError(error)) {
        const status = error.response?.status;
        if (status === 403) {
          return false;
        }
        if (!status || status >= 500) {
          return failureCount < 1;
        }
        return false;
      }
      return failureCount < 1;
    },
    staleTime: 60000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

const RESEARCH_LOG_PAGE_SIZE = 20;

export function useResearchLogs(projectId?: number | null, filters: ResearchLogFilters = {}) {
  return useInfiniteQuery({
    queryKey: queryKeys.research.logs(projectId as number, filters),
    queryFn: ({ pageParam }) => getResearchLogs(projectId as number, filters, pageParam, RESEARCH_LOG_PAGE_SIZE),
    initialPageParam: 0,
    getNextPageParam: (lastPage, allPages) =>
      lastPage.length === RESEARCH_LOG_PAGE_SIZE ? allPages.length : undefined,
    enabled: Boolean(projectId),
    staleTime: 30000,
    refetchOnWindowFocus: true,
  });
}

export function useGroupResearchLogs(groupId?: number | null, filters: ResearchLogFilters = {}) {
  const hasFilters = Object.values(filters).some((value) => value !== undefined && value !== null && value !== '');

  return useInfiniteQuery({
    queryKey: queryKeys.research.groupResearchLogs(groupId as number, hasFilters ? filters : undefined),
    queryFn: ({ pageParam }) => getGroupResearchLogs(groupId as number, filters, pageParam, RESEARCH_LOG_PAGE_SIZE),
    initialPageParam: 0,
    getNextPageParam: (lastPage, allPages) =>
      lastPage.length === RESEARCH_LOG_PAGE_SIZE ? allPages.length : undefined,
    enabled: isValidId(groupId),
    staleTime: 30000,
    refetchOnWindowFocus: true,
  });
}

export function useCreateResearchLog(projectId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateResearchLogPayload) => createResearchLog(payload),
    onSuccess: async (_log, payload) => {
      const targetProjectId = projectId ?? payload.projectId;
      const invalidations = [
        queryClient.invalidateQueries({ queryKey: queryKeys.research.logs(targetProjectId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.projectStats(targetProjectId) }),
      ];
      if (payload.groupId) {
        invalidations.push(
          queryClient.invalidateQueries({ queryKey: queryKeys.research.groupResearchLogs(payload.groupId) })
        );
      }
      await Promise.all(invalidations);
      toast.success('Đã tạo nhật ký nghiên cứu.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể tạo nhật ký nghiên cứu.'));
    },
  });
}

export function useEvaluationsByProject(projectId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.evaluations(projectId as number),
    queryFn: () => getEvaluationsByProject(projectId as number),
    enabled: Boolean(projectId),
    staleTime: 30000,
    refetchOnWindowFocus: true,
  });
}

export function useEvaluationsByGroup(groupId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.groupEvaluations(groupId as number),
    queryFn: () => getGroupEvaluations(groupId as number),
    enabled: isValidId(groupId),
    staleTime: 30000,
    refetchOnWindowFocus: true,
  });
}

export function useSubmitEvaluation(projectId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: SubmitEvaluationPayload) => submitEvaluation(payload),
    onSuccess: async (_evaluation, payload) => {
      const targetProjectId = projectId ?? payload.projectId;
      const invalidations = [
        queryClient.invalidateQueries({ queryKey: queryKeys.research.evaluations(targetProjectId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.projectStats(targetProjectId) }),
      ];
      if (payload.groupId) {
        invalidations.push(
          queryClient.invalidateQueries({ queryKey: queryKeys.research.groupEvaluations(payload.groupId) })
        );
      }
      await Promise.all(invalidations);
      toast.success('Đã lưu đánh giá kết quả nghiên cứu.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể lưu đánh giá kết quả nghiên cứu.'));
    },
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

export function useProductsByGroup(groupId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.groupProducts(groupId as number),
    queryFn: () => getGroupProducts(groupId as number),
    enabled: isValidId(groupId),
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
      const invalidations = [
        queryClient.invalidateQueries({ queryKey: queryKeys.research.products(targetProjectId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.projectStats(targetProjectId) }),
      ];
      if (payload.groupId) {
        invalidations.push(
          queryClient.invalidateQueries({ queryKey: queryKeys.research.groupProducts(payload.groupId) })
        );
      }
      await Promise.all(invalidations);
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
    enabled: isValidId(groupId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useResearchGroupMembers(groupId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.groupMembers(groupId as number),
    queryFn: () => getResearchGroupMembers(groupId as number),
    enabled: isValidId(groupId),
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

export function useMilestonesByGroup(groupId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.groupMilestones(groupId as number),
    queryFn: () => getGroupMilestones(groupId as number),
    enabled: isValidId(groupId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useMyMilestonesByGroup(groupId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.myGroupMilestones(groupId as number),
    queryFn: () => getMyGroupMilestones(groupId as number),
    enabled: isValidId(groupId),
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
    queryFn: () => getGroupReports(groupId as number),
    enabled: isValidId(groupId),
    staleTime: 30000,
    refetchOnWindowFocus: true,
  });
}

export function useMyGroupReports(groupId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.myGroupReports(groupId as number),
    queryFn: () => getMyGroupReports(groupId as number),
    enabled: isValidId(groupId),
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
    queryFn: () => getMyGroupTasks(groupId as number),
    enabled: isValidId(groupId),
    staleTime: 30000,
    refetchOnWindowFocus: true,
  });
}

export function useGroupTasks(groupId?: number | null) {
  return useQuery({
    queryKey: queryKeys.research.groupTasks(groupId as number),
    queryFn: () => getGroupTasks(groupId as number),
    enabled: isValidId(groupId),
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
    queryClient.invalidateQueries({ queryKey: queryKeys.research.projectStats(projectId) }),
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
  groupId?: number | null,
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ decision, comment }: { decision: ManagerReportDecision; comment: string }) =>
      managerReviewReport(reportId, decision, comment),
    onSuccess: async (_report, variables) => {
      await invalidateReviewedReport(queryClient, reportId, milestoneId, projectId, groupId, taskId);
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

export function useUpdateTaskStatus(milestoneId: number, projectId?: number | null) {
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
      const invalidations = [queryClient.invalidateQueries({ queryKey })];
      if (projectId) {
        invalidations.push(queryClient.invalidateQueries({ queryKey: queryKeys.research.projectStats(projectId) }));
      }
      await Promise.all(invalidations);
    },
  });
}

export function useCreateMilestone(projectId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateMilestonePayload) => createMilestone(payload),
    onSuccess: async (milestone, payload) => {
      const targetProjectId = projectId ?? payload.projectId;
      const invalidations = [
        queryClient.invalidateQueries({
          queryKey: queryKeys.research.milestones(targetProjectId),
        }),
        queryClient.invalidateQueries({
          queryKey: queryKeys.research.milestone(milestone.id),
        }),
        queryClient.invalidateQueries({
          queryKey: queryKeys.research.project(targetProjectId),
        }),
        queryClient.invalidateQueries({
          queryKey: queryKeys.research.projectStats(targetProjectId),
        }),
      ];
      if (milestone.groupId) {
        invalidations.push(
          queryClient.invalidateQueries({
            queryKey: queryKeys.research.groupMilestones(milestone.groupId),
          }),
          queryClient.invalidateQueries({
            queryKey: queryKeys.research.myGroupMilestones(milestone.groupId),
          })
        );
      }
      await Promise.all(invalidations);
      toast.success('Đã tạo mốc nghiên cứu thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể tạo mốc nghiên cứu.'));
    },
  });
}

export function useCreateTask(milestoneId: number, projectId?: number | null, groupId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateTaskPayload) => createTask(milestoneId, payload),
    onSuccess: async () => {
      const invalidations = [
        queryClient.invalidateQueries({
          queryKey: queryKeys.research.tasks(milestoneId),
        }),
      ];
      if (groupId) {
        invalidations.push(
          queryClient.invalidateQueries({
            queryKey: queryKeys.research.groupTasks(groupId),
          }),
          queryClient.invalidateQueries({
            queryKey: queryKeys.research.myTasks(groupId),
          }),
          queryClient.invalidateQueries({
            queryKey: queryKeys.research.groupMilestones(groupId),
          }),
          queryClient.invalidateQueries({
            queryKey: queryKeys.research.myGroupMilestones(groupId),
          })
        );
      }
      if (projectId) {
        invalidations.push(
          queryClient.invalidateQueries({
            queryKey: queryKeys.research.projectStats(projectId),
          })
        );
      }
      await Promise.all(invalidations);
      toast.success('Đã tạo nhiệm vụ thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể tạo nhiệm vụ.'));
    },
  });
}

export function useUpdateMilestone(projectId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ milestoneId, payload }: { milestoneId: number; payload: UpdateMilestonePayload }) =>
      updateMilestone(milestoneId, payload),
    onSuccess: async (milestone, variables) => {
      const invalidations = [
        queryClient.invalidateQueries({ queryKey: queryKeys.research.milestones(projectId as number) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.milestone(variables.milestoneId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.project(projectId as number) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.projectStats(projectId as number) }),
      ];
      if (milestone.groupId) {
        invalidations.push(
          queryClient.invalidateQueries({
            queryKey: queryKeys.research.groupMilestones(milestone.groupId),
          }),
          queryClient.invalidateQueries({
            queryKey: queryKeys.research.myGroupMilestones(milestone.groupId),
          })
        );
      }
      await Promise.all(invalidations);
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
    onSuccess: async (project, payload) => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: queryKeys.research.projects(labId ?? payload.labId),
        }),
        queryClient.invalidateQueries({
          queryKey: queryKeys.research.project(project.id),
        }),
      ]);
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
        queryClient.invalidateQueries({
          queryKey: queryKeys.research.projectStats(variables.projectId),
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
    onSuccess: async (group, payload) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.research.groups(projectId ?? payload.projectId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.group(group.id) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.project(projectId ?? payload.projectId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.research.projectStats(projectId ?? payload.projectId) }),
        labId
          ? queryClient.invalidateQueries({ queryKey: queryKeys.research.projects(labId) })
          : Promise.resolve(),
        labId
          ? queryClient.invalidateQueries({ queryKey: queryKeys.research.myGroups(labId) })
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
        queryClient.invalidateQueries({ queryKey: queryKeys.research.projectStats(projectId as number) }),
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
