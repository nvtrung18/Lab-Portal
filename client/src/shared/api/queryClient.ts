import axios from 'axios';
import { QueryClient } from '@tanstack/react-query';

function shouldRetryQuery(failureCount: number, error: unknown) {
  if (failureCount >= 1) {
    return false;
  }

  if (!axios.isAxiosError(error)) {
    return false;
  }

  const status = error.response?.status;
  return status === undefined || status >= 500;
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      gcTime: 10 * 60 * 1000,
      refetchOnWindowFocus: false,
      retry: shouldRetryQuery,
      staleTime: 30 * 1000,
    },
    mutations: {
      retry: false,
    },
  },
});
