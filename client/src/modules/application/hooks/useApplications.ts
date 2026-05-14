import { useQuery } from '@tanstack/react-query';

import { getApplications } from '../api';

export const APPLICATIONS_QUERY_KEY = ['applications'] as const;

export function useApplications() {
  return useQuery({
    queryKey: APPLICATIONS_QUERY_KEY,
    queryFn: getApplications,
    staleTime: 60 * 1000,
  });
}
