import { useMutation, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { translate } from '../../../shared/i18n';
import { USER_ME_QUERY_KEY } from '../../user/hooks';
import { reviewApplication, type ApplicationStatus } from '../api';

interface ReviewApplicationVariables {
  appId: number;
  status: Extract<ApplicationStatus, 'APPROVED' | 'REJECTED'>;
}

export function useReviewApplication(labId?: number | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ appId, status }: ReviewApplicationVariables) =>
      reviewApplication(appId, status),
    onSuccess: (application) => {
      toast.success(translate('application.toast.reviewed'));
      if (labId) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.applications.manager(labId) });
        void queryClient.invalidateQueries({ queryKey: queryKeys.labs.members(labId) });
      }
      void queryClient.invalidateQueries({ queryKey: queryKeys.applications.user(application.userId) });
      void queryClient.invalidateQueries({ queryKey: USER_ME_QUERY_KEY });
    },
    onError: () => {
      toast.error(translate('application.toast.reviewFailed'));
    },
  });
}
