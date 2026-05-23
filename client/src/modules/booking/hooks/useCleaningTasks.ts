import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { toast } from '../../../shared/components';
import {
  assignCleaningTasks,
  cancelCleaningTask,
  completeCleaningTask,
  getEligibleCleaners,
  getLabCleaningTasks,
  getMyCleaningTasks,
  type AssignCleaningPayload,
} from '../api';
import { isUsableSlot } from '../utils';

export const CLEANING_TASKS_QUERY_KEY = ['cleaningTasks'] as const;
export const CLEANING_PENDING_QUERY_KEY = ['cleaning', 'pending'] as const;
export const ELIGIBLE_CLEANERS_QUERY_KEY = ['eligibleCleaners'] as const;
export const MY_CLEANING_TASKS_QUERY_KEY = ['myCleaningTasks'] as const;

function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; errors?: string[] } | undefined;
    return data?.message ?? data?.errors?.[0] ?? fallback;
  }
  return fallback;
}

export function useLabCleaningTasks(labId?: number | null) {
  return useQuery({
    queryKey: labId ? [...CLEANING_TASKS_QUERY_KEY, labId] : CLEANING_TASKS_QUERY_KEY,
    queryFn: async () => {
      const tasks = await getLabCleaningTasks(labId as number);
      return tasks.filter(isUsableSlot);
    },
    enabled: Boolean(labId),
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
    refetchInterval: 60000,
    staleTime: 30000,
  });
}

export function useEligibleCleaners(slotId?: number | null) {
  return useQuery({
    queryKey: slotId ? [...ELIGIBLE_CLEANERS_QUERY_KEY, slotId] : ELIGIBLE_CLEANERS_QUERY_KEY,
    queryFn: () => getEligibleCleaners(slotId as number),
    enabled: Boolean(slotId),
    staleTime: 15000,
  });
}

export function useMyCleaningTasks() {
  return useQuery({
    queryKey: CLEANING_PENDING_QUERY_KEY,
    queryFn: getMyCleaningTasks,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
    staleTime: 30000,
  });
}

export function useAssignCleaningTask(labId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: AssignCleaningPayload) => assignCleaningTasks(payload),
    onSuccess: (_tasks, payload) => {
      if (labId) {
        queryClient.invalidateQueries({ queryKey: [...CLEANING_TASKS_QUERY_KEY, labId] });
      }
      queryClient.invalidateQueries({ queryKey: [...ELIGIBLE_CLEANERS_QUERY_KEY, payload.slotId] });
      queryClient.invalidateQueries({ queryKey: MY_CLEANING_TASKS_QUERY_KEY });
      toast.success('Đã phân công vệ sinh thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể phân công vệ sinh.'));
    },
  });
}

export function useCompleteCleaningTask(labId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (taskId: number) => completeCleaningTask(taskId),
    onSuccess: (task) => {
      queryClient.invalidateQueries({ queryKey: CLEANING_PENDING_QUERY_KEY });
      queryClient.invalidateQueries({ queryKey: MY_CLEANING_TASKS_QUERY_KEY });
      const effectiveLabId = labId ?? task.labId;
      if (effectiveLabId) {
        queryClient.invalidateQueries({ queryKey: [...CLEANING_TASKS_QUERY_KEY, effectiveLabId] });
      }
      toast.success('Đã xác nhận hoàn thành nhiệm vụ vệ sinh.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể xác nhận nhiệm vụ vệ sinh.'));
    },
  });
}

export function useCancelCleaningTask(labId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (taskId: number) => cancelCleaningTask(taskId),
    onSuccess: () => {
      if (labId) {
        queryClient.invalidateQueries({ queryKey: [...CLEANING_TASKS_QUERY_KEY, labId] });
      }
      queryClient.invalidateQueries({ queryKey: MY_CLEANING_TASKS_QUERY_KEY });
      toast.success('Đã hủy nhiệm vụ vệ sinh.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể hủy nhiệm vụ vệ sinh.'));
    },
  });
}
