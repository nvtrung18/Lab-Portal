import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import {
  cancelBooking,
  confirmCheckinByToken,
  createCheckinQr,
  createBooking,
  getMyBookings,
  getSlotRegistrations,
  manualCheckin,
  reviewBooking,
  type ReviewBookingPayload,
} from '../api';
export const MY_BOOKINGS_QUERY_KEY = queryKeys.bookings.mine;

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
    queryKey: queryKeys.slots.bookings(slotId as number),
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
        queryClient.invalidateQueries({ queryKey: queryKeys.slots.byLab(labId) });
      }
      queryClient.invalidateQueries({ queryKey: MY_BOOKINGS_QUERY_KEY });
      queryClient.invalidateQueries({ queryKey: queryKeys.slots.bookings(slotId) });
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
        queryClient.invalidateQueries({ queryKey: queryKeys.slots.byLab(labId) });
      }
      queryClient.invalidateQueries({ queryKey: MY_BOOKINGS_QUERY_KEY });
      if (booking.slotId) {
        queryClient.invalidateQueries({ queryKey: queryKeys.slots.bookings(booking.slotId) });
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
        queryClient.invalidateQueries({ queryKey: queryKeys.slots.bookings(slotId) });
      }
      if (labId) {
        queryClient.invalidateQueries({ queryKey: queryKeys.slots.byLab(labId) });
      }
      toast.success('Đã cập nhật trạng thái đăng ký.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể cập nhật trạng thái đăng ký.'));
    },
  });
}

export function useCreateCheckinQr() {
  return useMutation({
    mutationFn: ({ bookingId, fallbackReason }: { bookingId: number; fallbackReason: import('../api').FaceFallbackReason }) => createCheckinQr(bookingId, fallbackReason),
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể tạo mã QR check-in.'));
    },
  });
}

export function useManualCheckIn() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ bookingId, reason }: { bookingId: number; reason: string }) => manualCheckin(bookingId, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: MY_BOOKINGS_QUERY_KEY });
      toast.success('Xác nhận có mặt thủ công thành công.');
    },
    onError: (error) => toast.error(getErrorMessage(error, 'Không thể xác nhận có mặt thủ công.')),
  });
}

export function useConfirmCheckIn() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (token: string) => confirmCheckinByToken(token),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: MY_BOOKINGS_QUERY_KEY });
      if (result.booking.labId) {
        queryClient.invalidateQueries({ queryKey: queryKeys.slots.byLab(result.booking.labId) });
      }
      if (result.booking.slotId) {
        queryClient.invalidateQueries({ queryKey: queryKeys.slots.bookings(result.booking.slotId) });
      }
      toast.success('Xác nhận có mặt thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể xác nhận có mặt. Vui lòng thử lại sau.'));
    },
  });
}

