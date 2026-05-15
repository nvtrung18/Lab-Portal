import { useQuery } from '@tanstack/react-query';

import { getProfile } from '../api';

export const USER_ME_QUERY_KEY = ['userMe'] as const;

export function useCurrentUser() {
  return useQuery({
    queryKey: USER_ME_QUERY_KEY,
    queryFn: getProfile,
    staleTime: 5 * 60 * 1000,
  });
}

export const useProfile = useCurrentUser;
