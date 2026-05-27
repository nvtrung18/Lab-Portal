import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { banUser, getAdminUsers, unbanUser, updateUserRoles } from '../api';

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
    mutationFn: ({ userId, roles }: { userId: number; roles: string[] }) =>
      updateUserRoles(userId, roles),
    onSuccess: () => {
      toast.success('Cập nhật role thành công.');
      void queryClient.invalidateQueries({ queryKey: ADMIN_USERS_QUERY_KEY });
    },
    onError: () => {
      toast.error('Không thể cập nhật role.');
    },
  });
}

export function useBanUser() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: banUser,
    onSuccess: () => {
      toast.success('Đã ban user.');
      void queryClient.invalidateQueries({ queryKey: ADMIN_USERS_QUERY_KEY });
    },
    onError: () => {
      toast.error('Không thể ban user.');
    },
  });
}

export function useUnbanUser() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: unbanUser,
    onSuccess: () => {
      toast.success('Đã unban user.');
      void queryClient.invalidateQueries({ queryKey: ADMIN_USERS_QUERY_KEY });
    },
    onError: () => {
      toast.error('Không thể unban user.');
    },
  });
}
