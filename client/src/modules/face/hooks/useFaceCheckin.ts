import axios from 'axios';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { translate, translateBackendMessage } from '../../../shared/i18n';
import { invalidateAttendanceQueries } from '../../booking/hooks';
import type { BookingResponse } from '../../booking/api';
import { faceCheckinForStudent } from '../api';
import type { StudentFaceCheckinPayload } from '../types';

function errorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as { message?: string; errors?: string[] } | undefined;
    return translateBackendMessage(body?.message ?? body?.errors?.[0]) ?? translate('face.error');
  }
  return translateBackendMessage(error instanceof Error ? error.message : null) ?? translate('face.error');
}

export function useStudentFaceCheckin(booking: Pick<BookingResponse, 'labId' | 'slotId'>) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: StudentFaceCheckinPayload) => faceCheckinForStudent(request),
    onSuccess: (result) => {
      if (result.checkedIn) {
        invalidateAttendanceQueries(queryClient, booking);
        toast.success(translate('face.successToast'));
      }
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
}
