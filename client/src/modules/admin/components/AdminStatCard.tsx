import type { ReactNode } from 'react';

interface AdminStatCardProps {
  title: string;
  value: ReactNode;
  description?: string;
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

export function AdminStatCard({ title, value, description, suffix, loading = false }: AdminStatCardProps) {
  const displayValue = isRenderableValue(value) ? value : 0;
  const shouldShowSuffix = !loading && suffix && isRenderableValue(value);

  return (
    <article className="min-h-32 rounded-lg border border-slate-800 bg-slate-950/60 p-4 shadow-sm">
      <p className="text-sm font-medium text-slate-400">{title}</p>
      {loading ? (
        <div className="mt-4 h-9 w-28 animate-pulse rounded-md bg-slate-800" />
      ) : (
        <div className="mt-3 flex min-w-0 items-baseline gap-2">
          <span className="break-words text-3xl font-semibold tracking-normal text-white">{displayValue}</span>
          {shouldShowSuffix ? <span className="text-sm font-medium text-slate-400">{suffix}</span> : null}
        </div>
      )}
      {description ? <p className="mt-3 text-xs leading-5 text-slate-500">{description}</p> : null}
    </article>
  );
}
