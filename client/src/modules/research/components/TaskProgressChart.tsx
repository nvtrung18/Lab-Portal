import { memo, useMemo } from 'react';

import type { DashboardStats } from '../types';

interface TaskProgressChartProps {
  taskProgress: DashboardStats['taskProgress'];
}

const TASK_STATUS_ITEMS = [
  { key: 'todo', label: 'Cần làm' },
  { key: 'doing', label: 'Đang thực hiện' },
  { key: 'waitingReview', label: 'Chờ duyệt' },
  { key: 'needsRevision', label: 'Cần chỉnh sửa' },
  { key: 'done', label: 'Hoàn thành' },
  { key: 'overdue', label: 'Quá hạn' },
] as const;

export const TaskProgressChart = memo(function TaskProgressChart({ taskProgress }: TaskProgressChartProps) {
  const items = useMemo(
    () =>
      TASK_STATUS_ITEMS.map((item) => ({
        ...item,
        value: safeNumber(taskProgress[item.key]),
      })),
    [taskProgress],
  );
  const total = useMemo(() => items.reduce((sum, item) => sum + item.value, 0), [items]);

  if (total === 0) {
    return <p className="mt-4 text-sm text-slate-600">Chưa có dữ liệu nhiệm vụ.</p>;
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
