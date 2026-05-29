import { useQuery } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { getLabDashboardStats } from '../api';

export function useLabDashboardStats(labId?: number | null) {
  return useQuery({
    queryKey: queryKeys.labs.dashboardStats(labId as number),
    queryFn: () => getLabDashboardStats(labId as number),
    enabled: Boolean(labId),
    staleTime: 60000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}
