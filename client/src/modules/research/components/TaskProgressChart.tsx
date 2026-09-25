import { memo, useMemo } from 'react';

import { useI18n } from '../../../shared/i18n';
import type { DashboardStats } from '../types';

interface TaskProgressChartProps {
  taskProgress: DashboardStats['taskProgress'];
}

export const TaskProgressChart = memo(function TaskProgressChart({ taskProgress }: TaskProgressChartProps) {
  const { t } = useI18n();
  const items = useMemo(
    () => (['todo', 'doing', 'waitingReview', 'needsRevision', 'done', 'overdue'] as const).map((key) => ({
        key,
        label: t(`research.task.status.${key}`),
        value: safeNumber(taskProgress[key]),
      })),
    [taskProgress, t],
  );
  const total = useMemo(() => items.reduce((sum, item) => sum + item.value, 0), [items]);

  if (total === 0) {
    return <p className="mt-4 text-sm text-slate-600">{t('common.empty')}</p>;
  }

  return (
    <div className="mt-4 space-y-3">
      {items.map((item) => (
        <div key={item.key}>
          <div className="flex items-center justify-between gap-3 text-sm">
            <span className="font-medium text-slate-700">{item.label}</span>
            <span className="text-slate-500">{item.value}</span>
          </div>
          <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100">
            <div className="h-full rounded-full bg-emerald-500" style={{ width: `${getPercent(item.value, total)}%` }} />
          </div>
        </div>
      ))}
    </div>
  );
});

function getPercent(value: number, total: number) {
  const safeTotal = safeNumber(total);
  if (safeTotal === 0) {
    return 0;
  }
  return Math.max(0, Math.min(100, (safeNumber(value) / safeTotal) * 100));
}

function safeNumber(value: number | null | undefined) {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}
