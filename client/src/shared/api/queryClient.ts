import axios from 'axios';
import { QueryClient } from '@tanstack/react-query';

function shouldRetryQuery(failureCount: number, error: unknown) {
  if (failureCount >= 1 || !axios.isAxiosError(error)) {
    return false;
  }

  const status = error.response?.status;
  return status === undefined || status >= 500;
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: shouldRetryQuery,
    },
    mutations: {
      retry: false,
    },
  },
});

