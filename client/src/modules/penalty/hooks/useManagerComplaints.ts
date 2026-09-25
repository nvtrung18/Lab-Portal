import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { getStoredLocale, translate, translateBackendMessage } from '../../../shared/i18n';
import { getManagerComplaints, reviewComplaint, type ReviewComplaintPayload } from '../api';

function getErrorMessage(error: unknown, fallbackKey: string) {
  const locale = getStoredLocale();
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; errors?: string[] } | undefined;
    return translateBackendMessage(data?.message ?? data?.errors?.[0], locale) ?? translate(fallbackKey, locale);
  }
  return translate(fallbackKey, getStoredLocale());
}

export function useManagerComplaints(labId?: number | null) {
  return useQuery({
    queryKey: queryKeys.penalties.managerComplaints(labId as number),
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
        queryClient.invalidateQueries({ queryKey: queryKeys.penalties.managerComplaints(labId) });
      }
      queryClient.invalidateQueries({ queryKey: queryKeys.penalties.mine });
      toast.success(translate('penalty.toast.complaintReviewed'));
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, 'penalty.toast.complaintReviewFailed'));
    },
  });
}
