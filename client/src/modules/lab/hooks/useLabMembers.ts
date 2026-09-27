import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { translate, translateBackendMessage } from '../../../shared/i18n';
import { USER_ME_QUERY_KEY } from '../../user/hooks';
import { getLabMembers, removeLabMember, updateLabMemberRole, type LabMemberRole } from '../api';

export function useLabMembers(labId?: number | null) {
  return useQuery({
    queryKey: queryKeys.labs.members(labId as number),
    queryFn: () => getLabMembers(labId as number),
    enabled: Boolean(labId),
    staleTime: 60 * 1000,
  });
}

export function useRemoveLabMember() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ labId, userId }: { labId: number; userId: number }) =>
      removeLabMember(labId, userId),
    onSuccess: (_member, variables) => {
      toast.success(translate('lab.toast.memberRemoved'));
      void queryClient.invalidateQueries({
        queryKey: queryKeys.labs.members(variables.labId),
      });
      void queryClient.invalidateQueries({ queryKey: USER_ME_QUERY_KEY });
    },
    onError: () => {
      toast.error(translate('lab.toast.memberRemoveFailed'));
    },
  });
}

export function useUpdateLabMemberRole() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ labId, userId, role }: { labId: number; userId: number; role: LabMemberRole }) =>
      updateLabMemberRole(labId, userId, role),
    onSuccess: (_member, variables) => {
      toast.success(translate('lab.toast.memberRoleUpdated'));
      void queryClient.invalidateQueries({ queryKey: queryKeys.labs.members(variables.labId) });
      void queryClient.invalidateQueries({ queryKey: USER_ME_QUERY_KEY });
    },
    onError: (error) => {
      const data = axios.isAxiosError(error)
        ? error.response?.data as { message?: string; errors?: string[] } | undefined
        : undefined;
      toast.error(translateBackendMessage(data?.message ?? data?.errors?.[0]) ?? translate('lab.toast.memberRoleUpdateFailed'));
    },
  });
}
