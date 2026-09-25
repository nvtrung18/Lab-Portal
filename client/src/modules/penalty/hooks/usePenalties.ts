import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { getStoredLocale, translate, translateBackendMessage } from '../../../shared/i18n';
import { createPenalty, getMyPenalties, getSlotPenalties, submitComplaint } from '../api';
import type { CreateComplaintPayload, CreatePenaltyPayload } from '../types';

export const PENALTIES_QUERY_KEY = queryKeys.penalties.mine;

function getErrorMessage(error: unknown, fallbackKey: string) {
  const locale = getStoredLocale();
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; errors?: string[] } | undefined;
    return translateBackendMessage(data?.message ?? data?.errors?.[0], locale) ?? translate(fallbackKey, locale);
  }
  return translate(fallbackKey, getStoredLocale());
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
      toast.success(translate('penalty.toast.complaintSubmitted'));
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'penalty.toast.complaintSubmitFailed'));
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
      toast.success(translate('penalty.toast.created'));
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'penalty.toast.createFailed'));
    },
  });
}
