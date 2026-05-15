import { useQuery } from '@tanstack/react-query';

import { getLabById, getLabs } from '../api';

export const LABS_QUERY_KEY = ['labs'] as const;
export const STUDENT_LABS_QUERY_KEY = ['studentLabs'] as const;
export const LAB_QUERY_KEY = ['lab'] as const;

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
    queryKey: labId ? [...LAB_QUERY_KEY, labId] : LAB_QUERY_KEY,
    queryFn: () => getLabById(labId as number),
    enabled: Boolean(labId),
    staleTime: 5 * 60 * 1000,
  });
}
