import { useMutation, useQueryClient } from '@tanstack/react-query';

import { toast } from '../../../shared/components';
import { LAB_MEMBERS_QUERY_KEY } from '../../lab/hooks/useLabMembers';
import { USER_ME_QUERY_KEY } from '../../user/hooks';
import { reviewApplication, type ApplicationStatus } from '../api';
import { APPLICATIONS_QUERY_KEY } from './useApplications';

interface ReviewApplicationVariables {
  appId: number;
  status: Extract<ApplicationStatus, 'APPROVED' | 'REJECTED'>;
}

export function useReviewApplication() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ appId, status }: ReviewApplicationVariables) =>
      reviewApplication(appId, status),
    onSuccess: () => {
      toast.success('Cập nhật trạng thái đơn thành công.');
      void queryClient.invalidateQueries({ queryKey: APPLICATIONS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: LAB_MEMBERS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: USER_ME_QUERY_KEY });
    },
    onError: () => {
      toast.error('Không thể duyệt đơn. Vui lòng thử lại.');
    },
  });
}
