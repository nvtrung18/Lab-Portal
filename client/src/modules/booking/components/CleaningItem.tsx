import type { CleaningTask } from '../api';
import { formatCleaningStatus, getCleaningStatusClass } from '../utils';
import { formatDateTime } from '../../penalty/utils';
import { ConfirmCleaningButton } from './ConfirmCleaningButton';

interface CleaningItemProps {
  task: CleaningTask;
}

export function CleaningItem({ task }: CleaningItemProps) {
  return (
    <article className="rounded-md border border-slate-200 p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h4 className="text-sm font-semibold text-slate-950">{task.labName ?? 'PTN'}</h4>
          <p className="mt-1 text-sm text-slate-600">
            {formatDateTime(task.startTime)} - {formatDateTime(task.endTime)}
          </p>
        </div>
        <span
          className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getCleaningStatusClass(task.status)}`}
        >
          {formatCleaningStatus(task.status)}
        </span>
      </div>

      <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
        <div>
          <dt className="font-semibold text-slate-700">PTN</dt>
          <dd className="mt-1 text-slate-600">{task.labName ?? 'Chưa cập nhật'}</dd>
        </div>
        <div>
          <dt className="font-semibold text-slate-700">Trạng thái</dt>
          <dd className="mt-1 text-slate-600">{formatCleaningStatus(task.status)}</dd>
        </div>
        <div>
          <dt className="font-semibold text-slate-700">Ngày phân công</dt>
          <dd className="mt-1 text-slate-600">{formatDateTime(task.startedAt ?? task.assignedAt ?? task.createdAt)}</dd>
        </div>
        <div>
          <dt className="font-semibold text-slate-700">Ngày hoàn thành</dt>
          <dd className="mt-1 text-slate-600">{task.completedAt ? formatDateTime(task.completedAt) : 'Chưa hoàn thành'}</dd>
        </div>
      </dl>

      <div className="mt-4">
        <ConfirmCleaningButton taskId={task.id} status={task.status} />
      </div>
    </article>
  );
}
