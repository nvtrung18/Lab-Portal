import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { getStoredLocale, translate, translateBackendMessage } from '../../../shared/i18n';
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

export const MY_CLEANING_TASKS_QUERY_KEY = queryKeys.cleaning.mine;

function getErrorMessage(error: unknown, fallbackKey: string) {
  const locale = getStoredLocale();
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; errors?: string[] } | undefined;
    return translateBackendMessage(data?.message ?? data?.errors?.[0], locale) ?? translate(fallbackKey, locale);
  }
  return translate(fallbackKey, getStoredLocale());
}

export function useLabCleaningTasks(labId?: number | null) {
  return useQuery({
    queryKey: queryKeys.cleaning.overview(labId as number),
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
    queryKey: queryKeys.cleaning.eligible(slotId as number),
    queryFn: () => getEligibleCleaners(slotId as number),
    enabled: Boolean(slotId),
    staleTime: 15000,
  });
}

export function useMyCleaningTasks() {
  return useQuery({
    queryKey: MY_CLEANING_TASKS_QUERY_KEY,
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
        queryClient.invalidateQueries({ queryKey: queryKeys.cleaning.overview(labId) });
      }
      queryClient.invalidateQueries({ queryKey: queryKeys.cleaning.eligible(payload.slotId) });
      queryClient.invalidateQueries({ queryKey: MY_CLEANING_TASKS_QUERY_KEY });
      toast.success(translate('cleaning.toast.assigned'));
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'cleaning.toast.assignFailed'));
    },
  });
}

export function useCompleteCleaningTask(labId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (taskId: number) => completeCleaningTask(taskId),
    onSuccess: (task) => {
      queryClient.invalidateQueries({ queryKey: MY_CLEANING_TASKS_QUERY_KEY });
      const effectiveLabId = labId ?? task.labId;
      if (effectiveLabId) {
        queryClient.invalidateQueries({ queryKey: queryKeys.cleaning.overview(effectiveLabId) });
      }
      toast.success(translate('cleaning.toast.completed'));
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'cleaning.toast.completeFailed'));
    },
  });
}

export function useCancelCleaningTask(labId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (taskId: number) => cancelCleaningTask(taskId),
    onSuccess: () => {
      if (labId) {
        queryClient.invalidateQueries({ queryKey: queryKeys.cleaning.overview(labId) });
      }
      queryClient.invalidateQueries({ queryKey: MY_CLEANING_TASKS_QUERY_KEY });
      toast.success(translate('cleaning.toast.cancelled'));
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'cleaning.toast.cancelFailed'));
    },
  });
}
