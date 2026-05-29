import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { LABS_QUERY_KEY, STUDENT_LABS_QUERY_KEY } from '../../lab/hooks';
import { USER_ME_QUERY_KEY } from '../../user/hooks';
import { assignLabManager, createLab, getAdminLabs, updateLabStatus, type CreateLabRequest } from '../api';
import { ADMIN_USERS_QUERY_KEY } from './useAdminUsers';

export const ADMIN_LABS_QUERY_KEY = queryKeys.admin.labs;
export const AVAILABLE_MANAGERS_QUERY_KEY = queryKeys.admin.availableManagers;

export function useAdminLabs() {
  return useQuery({
    queryKey: ADMIN_LABS_QUERY_KEY,
    queryFn: getAdminLabs,
    staleTime: 60 * 1000,
  });
}

export function useAssignLabManager() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ labId, managerId }: { labId: number; managerId: number }) =>
      assignLabManager(labId, managerId),
    onSuccess: () => {
      toast.success('Đã gán quản lý PTN thành công.');
      void queryClient.invalidateQueries({ queryKey: ADMIN_LABS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: ADMIN_USERS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: ['assignableManagers'] });
      void queryClient.invalidateQueries({ queryKey: ['assignableLabs'] });
      void queryClient.invalidateQueries({ queryKey: USER_ME_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: queryKeys.admin.dashboardStats });
      void queryClient.invalidateQueries({ queryKey: ['adminAuditLogs'] });
    },
    onError: (err: any) => {
      const errMsg = err?.response?.data?.message || 'Không thể gán quản lý PTN.';
      toast.error(errMsg);
    },
  });
}

export function useCreateLab() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createLab,
    onSuccess: () => {
      toast.success('Đã tạo phòng thí nghiệm thành công.');
      void queryClient.invalidateQueries({ queryKey: ADMIN_LABS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: queryKeys.admin.dashboardStats });
      void queryClient.invalidateQueries({ queryKey: ['adminAuditLogs'] });
    },
    onError: () => {
      toast.error('Không thể tạo phòng thí nghiệm.');
    },
  });
}

export function useCreateLabWithManager() {
  const createLabMutation = useCreateLab();
  const assignManagerMutation = useAssignLabManager();

  return useMutation({
    mutationFn: async ({
      managerId,
      ...data
    }: CreateLabRequest & { managerId?: number | null }) => {
      const lab = await createLabMutation.mutateAsync(data);
      if (managerId) {
        return assignManagerMutation.mutateAsync({ labId: lab.id, managerId });
      }
      return lab;
    },
  });
}

export function useUpdateLabStatus() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ labId, status }: { labId: number; status: 'AVAILABLE' | 'INACTIVE' }) =>
      updateLabStatus(labId, status),
    onSuccess: () => {
      toast.success('Đã cập nhật trạng thái PTN thành công.');
      void queryClient.invalidateQueries({ queryKey: ADMIN_LABS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: LABS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: STUDENT_LABS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: queryKeys.admin.dashboardStats });
      void queryClient.invalidateQueries({ queryKey: ['adminAuditLogs'] });
    },
    onError: () => {
      toast.error('Không thể cập nhật trạng thái PTN.');
    },
  });
}
