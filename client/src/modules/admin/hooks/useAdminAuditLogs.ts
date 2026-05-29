import { useQuery } from '@tanstack/react-query';

import { getAdminAuditLogs, type AuditLogFilters } from '../api';

export function useAdminAuditLogs(page: number, filters: AuditLogFilters, size = 20) {
  return useQuery({
    queryKey: ['adminAuditLogs', page, filters] as const,
    queryFn: () => getAdminAuditLogs(page, size, filters),
    placeholderData: (previousData) => previousData, // keep previous data while fetching new page
    staleTime: 30 * 1000,
  });
}
