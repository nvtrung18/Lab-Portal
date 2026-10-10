import { useQuery } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { getProfile } from '../api';

export const USER_ME_QUERY_KEY = queryKeys.auth.userMe;

export function useCurrentUser(options: { enabled?: boolean; staleTime?: number; refetchInterval?: number } = {}) {
  return useQuery({
    queryKey: USER_ME_QUERY_KEY,
    queryFn: getProfile,
    retry: false,
    staleTime: 5 * 60 * 1000,
    ...options,
  });
}

export const useProfile = useCurrentUser;
