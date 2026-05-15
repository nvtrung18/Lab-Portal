import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { toast } from '../../../shared/components';
import { LABS_QUERY_KEY, STUDENT_LABS_QUERY_KEY } from '../../lab/hooks';
import { USER_ME_QUERY_KEY } from '../../user/hooks';
import { assignLabManager, createLab, getAdminLabs, updateLabStatus, type CreateLabRequest } from '../api';
import { ADMIN_USERS_QUERY_KEY } from './useAdminUsers';

export const ADMIN_LABS_QUERY_KEY = ['adminLabs'] as const;
export const AVAILABLE_MANAGERS_QUERY_KEY = ['availableManagers'] as const;

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
      toast.success('Gán manager thành công.');
      void queryClient.invalidateQueries({ queryKey: ADMIN_LABS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: ADMIN_USERS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: AVAILABLE_MANAGERS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: USER_ME_QUERY_KEY });
    },
    onError: () => {
      toast.error('Không thể gán manager.');
    },
  });
}

export function useCreateLab() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createLab,
    onSuccess: () => {
      toast.success('Tạo lab thành công.');
      void queryClient.invalidateQueries({ queryKey: ADMIN_LABS_QUERY_KEY });
    },
    onError: () => {
      toast.error('Không thể tạo lab.');
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
      toast.success('Cập nhật trạng thái lab thành công.');
      void queryClient.invalidateQueries({ queryKey: ADMIN_LABS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: LABS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: STUDENT_LABS_QUERY_KEY });
    },
    onError: () => {
      toast.error('Không thể cập nhật trạng thái lab.');
    },
  });
}
