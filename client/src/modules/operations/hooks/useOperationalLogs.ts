import { useQuery } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { getAiActionLogs, getAiUsageLogs, getFaceCheckinLogs } from '../api';
import type { OperationalFilters, OperationalLogItem, OperationalLogKind, OperationalLogPage } from '../types';

export function useOperationalLogs(kind: OperationalLogKind, filters: OperationalFilters, page: number, size = 20) {
  return useQuery<OperationalLogPage<OperationalLogItem>>({
    queryKey: queryKeys.admin.operationalLogs(kind, page, filters),
    queryFn: async () => {
      if (kind === 'ai-usage') return await getAiUsageLogs(filters, page, size);
      if (kind === 'ai-actions') return await getAiActionLogs(filters, page, size);
      return await getFaceCheckinLogs(filters, page, size);
    },
  });
}
