import { Component, type ErrorInfo, type ReactNode } from 'react';

import { Button } from './Button';
import { getHomePath } from './errorNavigation';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = {
    hasError: false,
  };

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    if (import.meta.env.DEV) {
      console.error('UI render error', error, errorInfo);
    }
  }

  private handleReload = () => {
    window.location.reload();
  };

  private handleGoHome = () => {
    window.location.assign(getHomePath());
  };

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }

    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-100 px-4 py-10 text-slate-900">
        <section className="w-full max-w-lg rounded-lg border border-slate-200 bg-white p-6 text-center shadow-sm">
          <h1 className="text-xl font-semibold text-slate-950">Đã xảy ra lỗi giao diện.</h1>
          <p className="mt-2 text-sm text-slate-600">Vui lòng thử tải lại trang.</p>
          <div className="mt-6 flex flex-col justify-center gap-3 sm:flex-row">
            <Button onClick={this.handleReload}>Tải lại trang</Button>
            <Button variant="outline" onClick={this.handleGoHome}>
              Quay về trang chủ
            </Button>
          </div>
        </section>
      </main>
    );
  }
}
