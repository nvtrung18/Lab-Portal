import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { toast } from '../../../shared/components';
import { getStoredLocale, translate, translateBackendMessage } from '../../../shared/i18n';
import {
  addLabAllowedNetwork,
  disableLabAllowedNetwork,
  getLabCheckinPolicy,
  registerCurrentLabNetwork,
  updateLabCheckinPolicy,
  type LabCheckinPolicyPayload,
} from '../api';

function getErrorMessage(error: unknown, fallbackKey: string) {
  const locale = getStoredLocale();
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; errors?: string[] } | undefined;
    return translateBackendMessage(data?.message ?? data?.errors?.[0], locale) ?? translate(fallbackKey, locale);
  }
  return translate(fallbackKey, locale);
}

export function useLabCheckinPolicy(labId?: number | null) {
  return useQuery({
    queryKey: queryKeys.labs.checkinPolicy(labId as number),
    queryFn: () => getLabCheckinPolicy(labId as number),
    enabled: Boolean(labId),
    staleTime: 30000,
    refetchOnReconnect: true,
    refetchOnWindowFocus: true,
  });
}

export function useUpdateLabCheckinPolicy(labId?: number | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: LabCheckinPolicyPayload) => updateLabCheckinPolicy(labId as number, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.labs.checkinPolicy(labId as number) });
      toast.success(translate('lab.toast.checkinPolicySaved'));
    },
    onError: (error) => toast.error(getErrorMessage(error, 'lab.toast.checkinPolicySaveFailed')),
  });
}

export function useAddLabAllowedNetwork(labId?: number | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (network: string) => addLabAllowedNetwork(labId as number, network),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.labs.checkinPolicy(labId as number) });
      toast.success(translate('lab.toast.networkAdded'));
    },
    onError: (error) => toast.error(getErrorMessage(error, 'lab.toast.networkAddFailed')),
  });
}

export function useRegisterCurrentLabNetwork(labId?: number | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => registerCurrentLabNetwork(labId as number),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.labs.checkinPolicy(labId as number) });
      toast.success(translate('lab.toast.currentNetworkRegistered'));
    },
    onError: (error) => toast.error(getErrorMessage(error, 'lab.toast.currentNetworkRegisterFailed')),
  });
}

export function useDisableLabAllowedNetwork(labId?: number | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (networkId: number) => disableLabAllowedNetwork(labId as number, networkId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.labs.checkinPolicy(labId as number) });
      toast.success(translate('lab.toast.networkDisabled'));
    },
    onError: (error) => toast.error(getErrorMessage(error, 'lab.toast.networkDisableFailed')),
  });
}
