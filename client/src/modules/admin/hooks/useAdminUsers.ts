import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { translate, translateBackendMessage } from '../../../shared/i18n';
import { banUser, getAdminUsers, unbanUser, updateUserRoles, getAssignableLabs, patchUserRole, getAssignableManagers } from '../api';

export const ADMIN_USERS_QUERY_KEY = queryKeys.admin.users;

export function useAdminUsers() {
  return useQuery({
    queryKey: ADMIN_USERS_QUERY_KEY,
    queryFn: getAdminUsers,
    staleTime: 60 * 1000,
  });
}

export function useUpdateUserRoles() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ userId, role, labId }: { userId: number; role: string; labId?: number }) =>
      patchUserRole(userId, role, labId),
    onSuccess: (data, variables) => {
      if (data?.message) {
        toast.success(translateBackendMessage(data.message) ?? data.message);
      } else if (variables.role === 'LAB_MANAGER') {
        toast.success(translate('admin.roleAssigned'));
      } else {
        toast.success(translate('admin.roleUpdated'));
      }
      void queryClient.invalidateQueries({ queryKey: ADMIN_USERS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: ['adminLabs'] });
      void queryClient.invalidateQueries({ queryKey: ['assignableLabs'] });
      void queryClient.invalidateQueries({ queryKey: queryKeys.admin.dashboardStats });
      void queryClient.invalidateQueries({ queryKey: ['adminAuditLogs'] });
    },
    onError: (err: any) => {
      const errMsg = translateBackendMessage(err?.response?.data?.message) ?? translate('admin.roleUpdateFailed');
      toast.error(errMsg);
    },
  });
}

export function useAssignableLabs(keyword?: string, includeInactive?: boolean) {
  return useQuery({
    queryKey: ['assignableLabs', keyword, includeInactive],
    queryFn: () => getAssignableLabs(keyword, includeInactive),
    staleTime: 30 * 1000,
  });
}

export function useAssignableManagers() {
  return useQuery({
    queryKey: ['assignableManagers'],
    queryFn: getAssignableManagers,
    staleTime: 30 * 1000,
  });
}

export function useBanUser() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: banUser,
    onSuccess: () => {
      toast.success(translate('admin.userBanned'));
      void queryClient.invalidateQueries({ queryKey: ADMIN_USERS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: queryKeys.admin.dashboardStats });
      void queryClient.invalidateQueries({ queryKey: ['adminAuditLogs'] });
    },
    onError: () => {
      toast.error(translate('admin.userBanFailed'));
    },
  });
}

export function useUnbanUser() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: unbanUser,
    onSuccess: () => {
      toast.success(translate('admin.userUnbanned'));
      void queryClient.invalidateQueries({ queryKey: ADMIN_USERS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: queryKeys.admin.dashboardStats });
      void queryClient.invalidateQueries({ queryKey: ['adminAuditLogs'] });
    },
    onError: () => {
      toast.error(translate('admin.userUnbanFailed'));
    },
  });
}
