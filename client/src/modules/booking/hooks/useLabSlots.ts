import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { toast } from '../../../shared/components';
import {
  cancelSlot,
  createSlot,
  getLabSlots,
  getSlot,
  type CancelSlotPayload,
  type CreateSlotPayload,
} from '../api';
import { normalizeSlot } from '../utils';

export const LAB_SLOTS_QUERY_KEY = ['labSlots'] as const;
export const SLOT_QUERY_KEY = ['slot'] as const;

export function useLabSlots(labId?: number | null) {
  return useQuery({
    queryKey: labId ? [...LAB_SLOTS_QUERY_KEY, labId] : LAB_SLOTS_QUERY_KEY,
    queryFn: async () => {
      const slots = await getLabSlots(labId as number);
      return slots.map(normalizeSlot);
    },
    enabled: Boolean(labId),
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
    staleTime: 30000,
  });
}

export function useSlot(slotId?: number | null) {
  return useQuery({
    queryKey: slotId ? [...SLOT_QUERY_KEY, slotId] : SLOT_QUERY_KEY,
    queryFn: async () => normalizeSlot(await getSlot(slotId as number)),
    enabled: Boolean(slotId),
    staleTime: 30000,
  });
}

function getErrorMessage(error: unknown, fallback = 'Không thể tạo khung giờ sử dụng.') {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; errors?: string[] } | undefined;
    return data?.message ?? data?.errors?.[0] ?? fallback;
  }

  return fallback;
}

export function useCreateSlot() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateSlotPayload) => createSlot(payload),
    onSuccess: (_slot, payload) => {
      queryClient.invalidateQueries({ queryKey: [...LAB_SLOTS_QUERY_KEY, payload.labId] });
      toast.success('Đã tạo khung giờ sử dụng thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error));
    },
  });
}

export function useCancelSlot(labId?: number | null, slotId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CancelSlotPayload) => cancelSlot(payload),
    onSuccess: (_slot, payload) => {
      if (labId) {
        queryClient.invalidateQueries({ queryKey: [...LAB_SLOTS_QUERY_KEY, labId] });
      }
      queryClient.invalidateQueries({ queryKey: ['slotRegistrations', slotId ?? payload.slotId] });
      toast.success('Đã hủy khung giờ sử dụng.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể hủy khung giờ sử dụng.'));
    },
  });
}
