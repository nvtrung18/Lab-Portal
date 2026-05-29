import type { ReactNode } from 'react';

import { Button } from './Button';

interface MessageStateProps {
  children?: ReactNode;
  className?: string;
}

interface ErrorStateProps extends MessageStateProps {
  onRetry?: () => void;
}

export function LoadingState({ children, className = '' }: MessageStateProps) {
  return <p className={`text-sm text-slate-600 ${className}`}>{children ?? 'Đang tải dữ liệu...'}</p>;
}

export function EmptyState({ children, className = '' }: MessageStateProps) {
  return (
    <div className={`rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600 ${className}`}>
      {children ?? 'Chưa có dữ liệu.'}
    </div>
  );
}

export function ErrorState({ children, className = '', onRetry }: ErrorStateProps) {
  return (
    <div
      className={`flex flex-wrap items-center gap-3 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700 ${className}`}
    >
      <span>{children ?? 'Không thể tải dữ liệu. Vui lòng thử lại.'}</span>
      {onRetry ? (
        <Button onClick={onRetry} size="sm" variant="ghost">
          Tải lại
        </Button>
      ) : null}
    </div>
  );
}

export function ResponsiveTable({ children, className = '' }: MessageStateProps) {
  return (
    <div className={`max-w-full overscroll-x-contain overflow-x-auto rounded-md border border-slate-200 ${className}`}>
      {children}
    </div>
  );
}
