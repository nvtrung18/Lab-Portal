import { useMutation, useQueryClient } from '@tanstack/react-query';

import { applyForLab } from '../api';
import { toast } from '../../../shared/components';
import { APPLICATIONS_QUERY_KEY } from '../../application/hooks';

interface ApplyLabVariables {
  labId: number;
  cvUrl: string;
}

export function useApplyLab() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ labId, cvUrl }: ApplyLabVariables) => applyForLab(labId, cvUrl),
    onSuccess: () => {
      toast.success('Nộp CV thành công.');
      void queryClient.invalidateQueries({ queryKey: APPLICATIONS_QUERY_KEY });
    },
    onError: () => {
      toast.error('Không thể nộp CV. Vui lòng thử lại.');
    },
  });
}
