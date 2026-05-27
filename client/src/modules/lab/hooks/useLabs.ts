import { useQuery } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { getLabById, getLabs } from '../api';

export const LABS_QUERY_KEY = queryKeys.labs.all;
export const STUDENT_LABS_QUERY_KEY = queryKeys.labs.student;

export function useLabs() {
  return useQuery({
    queryKey: LABS_QUERY_KEY,
    queryFn: getLabs,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
    staleTime: 0,
  });
}

export function useLab(labId?: number | null) {
  return useQuery({
    queryKey: queryKeys.labs.detail(labId as number),
    queryFn: () => getLabById(labId as number),
    enabled: Boolean(labId),
    staleTime: 5 * 60 * 1000,
  });
}
