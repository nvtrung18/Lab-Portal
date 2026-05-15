import { useMutation, useQueryClient } from '@tanstack/react-query';

import { setStoredUser } from '../../../shared/api';
import { updateProfile, type UpdateProfileRequest } from '../api';
import { USER_ME_QUERY_KEY } from './useProfile';

export function useUpdateProfile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: UpdateProfileRequest) => updateProfile(data),
    onSuccess: (updatedUser) => {
      queryClient.setQueryData(USER_ME_QUERY_KEY, updatedUser);
      setStoredUser({
        id: updatedUser.id,
        fullName: updatedUser.fullName,
        email: updatedUser.email,
        roles: updatedUser.roles.map((role) => role.replace(/^ROLE_/, '')),
        memberships: updatedUser.memberships?.map((membership) => ({
          labId: membership.labId ?? membership.lab?.id ?? membership.id ?? 0,
          labName: membership.labName ?? membership.lab?.name ?? membership.lab?.labName ?? 'Lab',
          status: membership.status,
        })),
      });
      void queryClient.invalidateQueries({ queryKey: USER_ME_QUERY_KEY });
    },
  });
}
