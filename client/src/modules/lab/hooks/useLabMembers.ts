import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { USER_ME_QUERY_KEY } from '../../user/hooks';
import { getLabMembers, removeLabMember } from '../api';

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
      toast.success('Đã xóa thành viên khỏi lab.');
      void queryClient.invalidateQueries({
        queryKey: queryKeys.labs.members(variables.labId),
      });
      void queryClient.invalidateQueries({ queryKey: USER_ME_QUERY_KEY });
    },
    onError: () => {
      toast.error('Không thể xóa thành viên khỏi lab.');
    },
  });
}
