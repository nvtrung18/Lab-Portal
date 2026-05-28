import { memo } from 'react';

interface StatCardProps {
  title: string;
  value: number | string;
  suffix?: string;
  description?: string;
  loading?: boolean;
}

export const StatCard = memo(function StatCard({ title, value, suffix, description, loading = false }: StatCardProps) {
  return (
    <article className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-sm font-medium text-slate-600">{title}</p>
      {loading ? (
        <div className="mt-3 h-8 w-24 animate-pulse rounded bg-slate-200" />
      ) : (
        <p className="mt-3 text-2xl font-semibold text-slate-950">
          {value}
          {suffix ? <span className="ml-1 text-base font-semibold text-slate-500">{suffix}</span> : null}
        </p>
      )}
      {description ? <p className="mt-2 text-sm text-slate-500">{description}</p> : null}
    </article>
  );
});
