import { useQuery } from '@tanstack/react-query';

import { getApplications, getApplicationsByLab, getApplicationsByUser } from '../api';

export const APPLICATIONS_QUERY_KEY = ['applications'] as const;

export function useApplications(labId?: number | null) {
  return useQuery({
    queryKey: labId ? [...APPLICATIONS_QUERY_KEY, { labId }] : APPLICATIONS_QUERY_KEY,
    queryFn: () => (labId ? getApplicationsByLab(labId) : getApplications()),
    enabled: labId !== null,
    staleTime: 60 * 1000,
  });
}

export function useUserApplications(userId?: number | null) {
  return useQuery({
    queryKey: userId ? [...APPLICATIONS_QUERY_KEY, { userId }] : APPLICATIONS_QUERY_KEY,
    queryFn: () => getApplicationsByUser(userId as number),
    enabled: Boolean(userId),
    staleTime: 60 * 1000,
  });
}
