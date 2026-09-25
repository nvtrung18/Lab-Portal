import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { getStoredLocale, translate } from '../../../shared/i18n';
import { getNotifications, markAllNotificationsRead, markNotificationRead } from '../api';

export function useNotifications(page: number, size = 20) {
  return useQuery({
    queryKey: queryKeys.notifications.page(page, size),
    queryFn: () => getNotifications(page, size),
    staleTime: 30_000,
  });
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: markNotificationRead,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all });
    },
    onError: () => toast.error(translate('notifications.toast.markFailed')),
  });
}

export function useMarkAllNotificationsRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: (updatedCount) => {
      toast.success(
        updatedCount > 0
          ? translate('notifications.toast.markedCount', getStoredLocale(), undefined, { count: updatedCount })
          : translate('notifications.toast.none'),
      );
      void queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all });
    },
    onError: () => toast.error(translate('notifications.toast.markAllFailed')),
  });
}
