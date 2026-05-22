import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { toast } from '../../../shared/components';
import {
  cancelBooking,
  createBooking,
  getMyBookings,
  getSlotRegistrations,
  reviewBooking,
  type ReviewBookingPayload,
} from '../api';
import { LAB_SLOTS_QUERY_KEY } from './useLabSlots';

export const MY_BOOKINGS_QUERY_KEY = ['myBookings'] as const;
export const SLOT_REGISTRATIONS_QUERY_KEY = ['slotRegistrations'] as const;

function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; errors?: string[] } | undefined;
    return data?.message ?? data?.errors?.[0] ?? fallback;
  }
  return fallback;
}

export function useMyBookings(enabled = true) {
  return useQuery({
    queryKey: MY_BOOKINGS_QUERY_KEY,
    queryFn: getMyBookings,
    enabled,
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useSlotRegistrations(slotId?: number | null) {
  return useQuery({
    queryKey: slotId ? [...SLOT_REGISTRATIONS_QUERY_KEY, slotId] : SLOT_REGISTRATIONS_QUERY_KEY,
    queryFn: () => getSlotRegistrations(slotId as number),
    enabled: Boolean(slotId),
    staleTime: 15000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useCreateBooking(labId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (slotId: number) => createBooking(slotId),
    onSuccess: (_booking, slotId) => {
      if (labId) {
        queryClient.invalidateQueries({ queryKey: [...LAB_SLOTS_QUERY_KEY, labId] });
      }
      queryClient.invalidateQueries({ queryKey: MY_BOOKINGS_QUERY_KEY });
      queryClient.invalidateQueries({ queryKey: [...SLOT_REGISTRATIONS_QUERY_KEY, slotId] });
      toast.success('Đã gửi đăng ký sử dụng PTN. Vui lòng chờ quản lý phê duyệt.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể đăng ký sử dụng khung giờ này.'));
    },
  });
}

export function useCancelBooking(labId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (bookingId: number) => cancelBooking(bookingId),
    onSuccess: (booking) => {
      if (labId) {
        queryClient.invalidateQueries({ queryKey: [...LAB_SLOTS_QUERY_KEY, labId] });
      }
      queryClient.invalidateQueries({ queryKey: MY_BOOKINGS_QUERY_KEY });
      if (booking.slotId) {
        queryClient.invalidateQueries({ queryKey: [...SLOT_REGISTRATIONS_QUERY_KEY, booking.slotId] });
      }
      toast.success('Đã hủy đăng ký sử dụng khung giờ.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể hủy đăng ký.'));
    },
  });
}

export function useReviewBooking(labId?: number | null, slotId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ReviewBookingPayload) => reviewBooking(payload),
    onSuccess: () => {
      if (slotId) {
        queryClient.invalidateQueries({ queryKey: [...SLOT_REGISTRATIONS_QUERY_KEY, slotId] });
      }
      if (labId) {
        queryClient.invalidateQueries({ queryKey: [...LAB_SLOTS_QUERY_KEY, labId] });
      }
      toast.success('Đã cập nhật trạng thái đăng ký.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể cập nhật trạng thái đăng ký.'));
    },
  });
}
