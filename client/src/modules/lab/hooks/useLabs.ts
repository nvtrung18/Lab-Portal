import { useQuery } from '@tanstack/react-query';

import { getLabs } from '../api';

export const LABS_QUERY_KEY = ['labs'] as const;

export function useLabs() {
  return useQuery({
    queryKey: LABS_QUERY_KEY,
    queryFn: getLabs,
    staleTime: 5 * 60 * 1000,
  });
}
