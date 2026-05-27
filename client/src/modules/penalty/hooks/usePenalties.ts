import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { createPenalty, getMyPenalties, getSlotPenalties, submitComplaint } from '../api';
import type { CreateComplaintPayload, CreatePenaltyPayload } from '../types';

export const PENALTIES_QUERY_KEY = queryKeys.penalties.mine;

function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; errors?: string[] } | undefined;
    return data?.message ?? data?.errors?.[0] ?? fallback;
  }
  return fallback;
}

export function useMyPenalties() {
  return useQuery({
    queryKey: PENALTIES_QUERY_KEY,
    queryFn: getMyPenalties,
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useSubmitComplaint() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateComplaintPayload) => submitComplaint(payload),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: PENALTIES_QUERY_KEY });
      toast.success('Đã gửi khiếu nại thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể gửi khiếu nại. Vui lòng thử lại sau.'));
    },
  });
}

export function useSlotPenalties(slotId?: number | null) {
  return useQuery({
    queryKey: queryKeys.penalties.bySlot(slotId as number),
    queryFn: () => getSlotPenalties(slotId as number),
    enabled: Boolean(slotId),
    staleTime: 15000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useCreatePenalty(slotId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreatePenaltyPayload) => createPenalty(payload),
    onSuccess: async (_penalty, payload) => {
      const effectiveSlotId = slotId ?? payload.slotId;
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.slots.bookings(effectiveSlotId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.slots.detail(effectiveSlotId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.penalties.bySlot(effectiveSlotId) }),
        queryClient.invalidateQueries({ queryKey: PENALTIES_QUERY_KEY }),
      ]);
      toast.success('Đã ghi nhận vi phạm.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể ghi nhận vi phạm. Vui lòng thử lại sau.'));
    },
  });
}
