import type { PropsWithChildren } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';

import { queryClient } from '../../shared/api';
import { ToastContainer } from '../../shared/components';
import { I18nProvider } from '../../shared/i18n';
import { ThemeProvider } from '../../shared/theme';

export function GlobalProviders({ children }: PropsWithChildren) {
  return (
    <I18nProvider>
      <ThemeProvider>
        <QueryClientProvider client={queryClient}>
          {children}
          <ToastContainer />
        </QueryClientProvider>
      </ThemeProvider>
    </I18nProvider>
  );
}
