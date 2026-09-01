import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { changeFaceConsent, deleteFaceProfile, getFaceConsent, getFaceProfile, saveFaceProfile } from '../api';
import type { FaceConsentStatus, FaceImageRequest } from '../types';

export function useFaceProfile(userId: number | null, enabled = true) {
  const consent = useQuery({
    queryKey: queryKeys.face.consent(userId),
    queryFn: () => getFaceConsent(userId),
    enabled,
  });
  const profile = useQuery({
    queryKey: queryKeys.face.profile(userId),
    queryFn: () => getFaceProfile(userId),
    enabled,
  });
  return { consent, profile };
}

export function useFaceProfileActions(userId: number | null) {
  const queryClient = useQueryClient();
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.face.consent(userId) });
    void queryClient.invalidateQueries({ queryKey: queryKeys.face.profile(userId) });
  };
  const consent = useMutation({
    mutationFn: ({ status, reason }: { status: FaceConsentStatus; reason?: string }) => changeFaceConsent(userId, status, reason),
    onSuccess: () => { toast.success('Đã cập nhật đồng ý xử lý khuôn mặt.'); refresh(); },
  });
  const save = useMutation({
    mutationFn: ({ request, update }: { request: FaceImageRequest; update: boolean }) => saveFaceProfile(userId, request, update),
    onSuccess: () => { toast.success('Đã cập nhật hồ sơ khuôn mặt.'); refresh(); },
  });
  const remove = useMutation({
    mutationFn: () => deleteFaceProfile(userId),
    onSuccess: () => { toast.success('Đã xóa hồ sơ khuôn mặt.'); refresh(); },
  });
  return { consent, save, remove };
}
