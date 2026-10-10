import type { ReactNode } from 'react';

interface AdminStatCardProps {
  title: string;
  value: ReactNode;
  suffix?: string;
  loading?: boolean;
}

function isRenderableValue(value: ReactNode) {
  if (value === null || value === undefined) {
    return false;
  }

  if (typeof value === 'number') {
    return Number.isFinite(value);
  }

  if (typeof value === 'string') {
    return value.trim().length > 0 && value !== 'NaN' && value !== 'undefined' && value !== 'null';
  }

  return true;
}

export function AdminStatCard({ title, value, suffix, loading = false }: AdminStatCardProps) {
  const displayValue = isRenderableValue(value) ? value : 0;
  const shouldShowSuffix = !loading && suffix && isRenderableValue(value);

  return (
    <article className="app-stat-card app-surface-elevated flex min-h-32 min-w-0 flex-col rounded-xl p-5">
      <p className="min-h-10 text-sm font-medium leading-5 text-slate-600 dark:text-slate-300">{title}</p>
      {loading ? (
        <div className="mt-3 h-9 w-28 animate-pulse rounded-lg bg-slate-200 dark:bg-slate-700" />
      ) : (
        <div className="mt-auto flex min-w-0 items-baseline gap-2 pt-3">
          <span className="app-stat-value break-words text-3xl font-semibold tabular-nums tracking-tight text-slate-950 dark:text-white">{displayValue}</span>
          {shouldShowSuffix ? <span className="text-sm font-medium text-slate-600 dark:text-slate-300">{suffix}</span> : null}
        </div>
      )}
    </article>
  );
}
