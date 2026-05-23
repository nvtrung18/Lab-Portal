import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { toast } from '../../../shared/components';
import { getManagerComplaints, reviewComplaint, type ReviewComplaintPayload } from '../api';

export const MANAGER_COMPLAINTS_QUERY_KEY = ['managerComplaints'] as const;

function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; errors?: string[] } | undefined;
    return data?.message ?? data?.errors?.[0] ?? fallback;
  }
  return fallback;
}

export function useManagerComplaints(labId?: number | null) {
  return useQuery({
    queryKey: labId ? [...MANAGER_COMPLAINTS_QUERY_KEY, labId] : MANAGER_COMPLAINTS_QUERY_KEY,
    queryFn: () => getManagerComplaints(labId as number),
    enabled: Boolean(labId),
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
    staleTime: 30000,
  });
}

export function useReviewComplaint(labId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ReviewComplaintPayload) => reviewComplaint(payload),
    onSuccess: () => {
      if (labId) {
        queryClient.invalidateQueries({ queryKey: [...MANAGER_COMPLAINTS_QUERY_KEY, labId] });
      }
      queryClient.invalidateQueries({ queryKey: ['penalties'] });
      toast.success('Đã xử lý khiếu nại thành công.');
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'Không thể xử lý khiếu nại.'));
    },
  });
}
