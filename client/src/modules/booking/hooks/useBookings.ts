import axios from 'axios';
import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { getStoredLocale, translate, translateBackendMessage } from '../../../shared/i18n';
import {
  cancelBooking,
  confirmCheckinByToken,
  createCheckinQr,
  getMyCheckinQrRequest,
  getMyCheckinQrHistory,
  getPendingCheckinQrRequests,
  reviewCheckinQrRequest,
  createBooking,
  getMyBookings,
  getSlotRegistrations,
  manualCheckin,
  reviewBooking,
  type ReviewBookingPayload,
} from '../api';
export const MY_BOOKINGS_QUERY_KEY = queryKeys.bookings.mine;

export function invalidateAttendanceQueries(
  queryClient: QueryClient,
  booking: Pick<import('../api').BookingResponse, 'labId' | 'slotId'>,
) {
  void queryClient.invalidateQueries({ queryKey: MY_BOOKINGS_QUERY_KEY });
  void queryClient.invalidateQueries({ queryKey: queryKeys.face.checkinCandidates });
  void queryClient.invalidateQueries({ queryKey: queryKeys.slots.bookings(booking.slotId) });
  void queryClient.invalidateQueries({ queryKey: queryKeys.slots.byLab(booking.labId) });
  void queryClient.invalidateQueries({ queryKey: queryKeys.labs.dashboardStats(booking.labId) });
  void queryClient.invalidateQueries({ queryKey: ['operationalLogs'] });
}

function getErrorMessage(error: unknown, fallbackKey: string) {
  const locale = getStoredLocale();
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; errors?: string[] } | undefined;
    return translateBackendMessage(data?.message ?? data?.errors?.[0], locale) ?? translate(fallbackKey, locale);
  }
  return translate(fallbackKey, getStoredLocale());
}

export function useMyBookings(enabled = true) {
  return useQuery({
    queryKey: MY_BOOKINGS_QUERY_KEY,
    queryFn: getMyBookings,
    enabled,
    staleTime: 30000,
    refetchInterval: enabled ? 15000 : false,
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
      toast.success(translate('booking.toast.created'));
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'booking.toast.createFailed'));
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
      toast.success(translate('booking.toast.cancelled'));
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'booking.toast.cancelFailed'));
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
      toast.success(translate('booking.toast.statusUpdated'));
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'booking.toast.statusUpdateFailed'));
    },
  });
}

export function useCreateCheckinQr() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ bookingId, fallbackReason, customReason }: { bookingId: number; fallbackReason: import('../api').FaceFallbackReason; customReason?: string }) => createCheckinQr(bookingId, fallbackReason, customReason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['checkinQrHistory'] });
      toast.success(translate('booking.toast.qrRequested'));
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'booking.toast.qrCreateFailed'));
    },
  });
}

export function useMyCheckinQrHistory(enabled = true) {
  return useQuery({
    queryKey: ['checkinQrHistory'],
    queryFn: getMyCheckinQrHistory,
    enabled,
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useMyCheckinQrRequest(bookingId: number, enabled: boolean) {
  return useQuery({
    queryKey: ['checkinQrRequest', bookingId],
    queryFn: () => getMyCheckinQrRequest(bookingId),
    enabled,
    retry: false,
    refetchInterval: enabled ? 30000 : false,
  });
}

export function usePendingCheckinQrRequests(enabled = true) {
  return useQuery({
    queryKey: ['pendingCheckinQrRequests'],
    queryFn: getPendingCheckinQrRequests,
    enabled,
    refetchInterval: enabled ? 30000 : false,
  });
}

export function useReviewCheckinQrRequest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ requestId, approved }: { requestId: string; approved: boolean }) => reviewCheckinQrRequest(requestId, approved),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['pendingCheckinQrRequests'] }),
    onError: (error) => toast.error(getErrorMessage(error, 'booking.toast.qrReviewFailed')),
  });
}

export function useManualCheckIn() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ bookingId, reason }: { bookingId: number; reason: string }) => manualCheckin(bookingId, reason),
    onSuccess: (result) => {
      invalidateAttendanceQueries(queryClient, result.booking);
      toast.success(translate('booking.toast.manualCheckinSuccess'));
    },
    onError: (error) => toast.error(getErrorMessage(error, 'booking.toast.manualCheckinFailed')),
  });
}

export function useConfirmCheckIn() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (token: string) => confirmCheckinByToken(token),
    onSuccess: (result) => {
      invalidateAttendanceQueries(queryClient, result.booking);
      toast.success(translate('booking.toast.checkinSuccess'));
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'booking.toast.checkinFailed'));
    },
  });
}

