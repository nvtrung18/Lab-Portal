import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { streamRealtimeEvents } from '../api';
import type { RealtimeEvent } from '../types';

const RECONNECT_DELAY_MS = 3000;

export function useRealtimeEvents(enabled: boolean) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled) return;

    const controller = new AbortController();
    let reconnectTimer: ReturnType<typeof setTimeout> | undefined;

    const handleEvent = (event: RealtimeEvent) => {
      if (event.type !== 'NOTIFICATION_CREATED' || !event.notificationEventType) return;

      void queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all });
      if (event.title && event.message) {
        toast.info(event.message, event.title);
      }

      if (
        event.notificationEventType === 'BOOKING_REQUESTED' ||
        event.notificationEventType === 'BOOKING_CANCELLED'
      ) {
        void queryClient.invalidateQueries({ queryKey: ['slotBookings'] });
        void queryClient.invalidateQueries({ queryKey: ['labSlots'] });
        void queryClient.invalidateQueries({ queryKey: ['labDashboardStats'] });
      }
      if (
        event.notificationEventType === 'BOOKING_APPROVED' ||
        event.notificationEventType === 'BOOKING_REJECTED' ||
        event.notificationEventType === 'BOOKING_CANCELLED' ||
        event.notificationEventType === 'BOOKING_SESSION_COMPLETED' ||
        event.notificationEventType === 'BOOKING_NO_SHOW' ||
        event.notificationEventType === 'BOOKING_CHECKED_IN' ||
        event.notificationEventType === 'PENALTY_CREATED'
      ) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.bookings.mine });
        void queryClient.invalidateQueries({ queryKey: ['labSlots'] });
      }

      if (event.notificationEventType === 'QR_CHECKIN_REQUESTED') {
        void queryClient.invalidateQueries({ queryKey: ['pendingCheckinQrRequests'] });
      }
      if (event.notificationEventType === 'QR_CHECKIN_REVIEWED') {
        void queryClient.invalidateQueries({ queryKey: ['checkinQrHistory'] });
        if (event.targetId !== null) {
          void queryClient.invalidateQueries({ queryKey: ['checkinQrRequest', event.targetId] });
        }
        void queryClient.invalidateQueries({ queryKey: queryKeys.bookings.mine });
      }
      if (
        event.notificationEventType === 'FACE_CHECKIN_SUCCEEDED' ||
        event.notificationEventType === 'FACE_CHECKIN_FAILED'
      ) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.bookings.mine });
        void queryClient.invalidateQueries({ queryKey: queryKeys.face.checkinCandidates });
        void queryClient.invalidateQueries({ queryKey: ['operationalLogs'] });
      }
    };

    const connect = async () => {
      try {
        await streamRealtimeEvents(controller.signal, handleEvent);
      } catch (error) {
        if (controller.signal.aborted) return;
        console.warn('Realtime event stream disconnected.', error);
      }
      if (!controller.signal.aborted) {
        reconnectTimer = setTimeout(() => void connect(), RECONNECT_DELAY_MS);
      }
    };

    void connect();
    return () => {
      controller.abort();
      if (reconnectTimer) clearTimeout(reconnectTimer);
    };
  }, [enabled, queryClient]);
}
