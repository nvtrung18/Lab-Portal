import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';
import type { NotificationItem, NotificationPage } from '../types';

export async function getNotifications(page: number, size: number): Promise<NotificationPage> {
  const response = await apiClient.get<Response<NotificationPage>>('/api/notifications', {
    params: { page, size },
  });
  return response.data.data;
}

export async function markNotificationRead(notificationId: number): Promise<NotificationItem> {
  const response = await apiClient.patch<Response<NotificationItem>>(
    `/api/notifications/${notificationId}/read`,
  );
  return response.data.data;
}

export async function markAllNotificationsRead(): Promise<number> {
  const response = await apiClient.patch<Response<number>>('/api/notifications/read-all');
  return response.data.data;
}
