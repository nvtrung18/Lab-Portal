import { useQuery } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { getApplications, getApplicationsByLab, getApplicationsByUser } from '../api';

export const APPLICATIONS_QUERY_KEY = queryKeys.applications.all;

export function useApplications(labId?: number | null) {
  return useQuery({
    queryKey: labId ? queryKeys.applications.manager(labId) : APPLICATIONS_QUERY_KEY,
    queryFn: () => (labId ? getApplicationsByLab(labId) : getApplications()),
    enabled: labId !== null,
    staleTime: 60 * 1000,
  });
}

export function useUserApplications(userId?: number | null) {
  return useQuery({
    queryKey: queryKeys.applications.user(userId as number),
    queryFn: () => getApplicationsByUser(userId as number),
    enabled: Boolean(userId),
    staleTime: 60 * 1000,
  });
}
