import { useMutation, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { translate } from '../../../shared/i18n';
import { APPLICATIONS_QUERY_KEY } from '../../application/hooks';
import { USER_ME_QUERY_KEY } from '../../user/hooks';
import { applyForLab } from '../api';
import { LABS_QUERY_KEY, STUDENT_LABS_QUERY_KEY } from './useLabs';

interface ApplyLabVariables {
  labId: number;
  cvUrl?: string;
  cvFile?: File | null;
}

export function useApplyLab() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ labId, cvUrl, cvFile }: ApplyLabVariables) =>
      applyForLab(labId, { cvUrl, cvFile }),
    onSuccess: (_application, variables) => {
      toast.success(translate('application.toast.submitted'));
      void queryClient.invalidateQueries({ queryKey: LABS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: STUDENT_LABS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: APPLICATIONS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: queryKeys.applications.manager(variables.labId) });
      void queryClient.invalidateQueries({ queryKey: USER_ME_QUERY_KEY });
    },
    onError: () => {
      toast.error(translate('application.toast.submitFailed'));
    },
  });
}
