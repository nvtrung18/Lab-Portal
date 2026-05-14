import { useMutation, useQueryClient } from '@tanstack/react-query';

import { updateProfile, type UpdateProfileRequest } from '../api';
import { USER_ME_QUERY_KEY } from './useProfile';

export function useUpdateProfile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: UpdateProfileRequest) => updateProfile(data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: USER_ME_QUERY_KEY });
    },
  });
}
