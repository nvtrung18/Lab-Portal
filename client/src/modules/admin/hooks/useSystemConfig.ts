import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { getSystemConfig, updateSystemConfig, type SystemConfig } from '../api';

export const SYSTEM_CONFIG_QUERY_KEY = queryKeys.admin.systemConfig;

export function useSystemConfig() {
  return useQuery({
    queryKey: SYSTEM_CONFIG_QUERY_KEY,
    queryFn: getSystemConfig,
    staleTime: 60 * 1000,
  });
}

export function useUpdateSystemConfig() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (config: SystemConfig) => updateSystemConfig(config),
    onSuccess: () => {
      toast.success('Đã cập nhật cấu hình hệ thống.');
      void queryClient.invalidateQueries({ queryKey: SYSTEM_CONFIG_QUERY_KEY });
    },
    onError: () => {
      toast.error('Không thể cập nhật cấu hình hệ thống.');
    },
  });
}
