import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
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
    onError: () => toast.error('Không thể đánh dấu thông báo đã đọc.'),
  });
}

export function useMarkAllNotificationsRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: (updatedCount) => {
      toast.success(
        updatedCount > 0 ? `Đã đánh dấu ${updatedCount} thông báo đã đọc.` : 'Không có thông báo mới.',
      );
      void queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all });
    },
    onError: () => toast.error('Không thể đánh dấu tất cả thông báo đã đọc.'),
  });
}
