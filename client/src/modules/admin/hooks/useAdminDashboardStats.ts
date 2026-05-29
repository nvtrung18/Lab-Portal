import { useQuery } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { getAdminDashboardStats } from '../api';

export const ADMIN_DASHBOARD_STATS_QUERY_KEY = queryKeys.admin.dashboardStats;

export function useAdminDashboardStats() {
  return useQuery({
    queryKey: ADMIN_DASHBOARD_STATS_QUERY_KEY,
    queryFn: getAdminDashboardStats,
    staleTime: 60 * 1000,
  });
}
