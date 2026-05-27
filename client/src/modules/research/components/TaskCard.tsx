import { memo } from 'react';
import type { DragEvent } from 'react';

import type { ResearchTask } from '../types';
import type { ResearchReportStatus } from '../types';
import { getStatusClass, formatDate } from '../utils';
import { Button } from '../../../shared/components';

const REPORT_STATUS_LABELS: Record<ResearchReportStatus, string> = {
  SUBMITTED: 'Đã nộp, chờ kiểm tra',
  LEADER_REVIEWED: 'Trưởng nhóm đã kiểm tra',
  NEEDS_REVISION: 'Cần chỉnh sửa',
  APPROVED: 'Báo cáo đã được duyệt',
  REJECTED: 'Báo cáo bị từ chối',
};

interface TaskCardProps {
  task: ResearchTask;
  draggable?: boolean;
  isUpdating?: boolean;
  dragDisabledReason?: string;
  reportStatus?: ResearchReportStatus | null;
  reportActionLabel?: 'Nộp báo cáo' | 'Nộp lại báo cáo' | null;
  onReportAction?: (task: ResearchTask) => void;
  onDragStart?: (event: DragEvent<HTMLElement>, task: ResearchTask) => void;
  onDragEnd?: () => void;
}

export const TaskCard = memo(function TaskCard({
  task,
  draggable = false,
  isUpdating = false,
  dragDisabledReason,
  reportStatus,
  reportActionLabel,
  onReportAction,
  onDragStart,
  onDragEnd,
}: TaskCardProps) {
  return (
    <article
      aria-busy={isUpdating}
      className={`min-w-0 rounded-md border border-slate-200 bg-white p-3 shadow-sm transition ${
        draggable ? 'cursor-grab active:cursor-grabbing' : ''
      } ${isUpdating ? 'opacity-60' : ''}`}
      draggable={draggable && !isUpdating}
      onDragEnd={onDragEnd}
      onDragStart={(event) => onDragStart?.(event, task)}
      title={dragDisabledReason}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h5 className="min-w-0 break-words text-sm font-semibold text-slate-950">{task.title}</h5>
        {task.isOverdue ? (
          <span className="rounded-full bg-red-50 px-2 py-1 text-[11px] font-semibold text-red-700 ring-1 ring-red-200">
            Quá hạn
          </span>
        ) : null}
      </div>

      <p className="mt-2 line-clamp-2 text-xs leading-5 text-slate-600">
        {task.description || 'Chưa cập nhật mô tả nhiệm vụ.'}
      </p>

      <dl className="mt-3 space-y-2 text-xs">
        <div>
          <dt className="font-semibold text-slate-700">Người phụ trách</dt>
          <dd className="mt-1 break-words text-slate-600">
            {task.assigneeName ?? task.assigneeEmail ?? 'Chưa phân công'}
          </dd>
        </div>
        <div>
          <dt className="font-semibold text-slate-700">Hạn hoàn thành</dt>
          <dd className="mt-1 text-slate-600">{formatDate(task.deadline)}</dd>
        </div>
      </dl>

      <div className="mt-3">
        <div className="flex items-center justify-between text-xs">
          <span className="font-semibold text-slate-700">Tỷ lệ hoàn thành</span>
          <span className="font-semibold text-slate-950">{task.progressPercent}%</span>
        </div>
        <div
          aria-label={`Tỷ lệ hoàn thành ${task.progressPercent}%`}
          aria-valuemax={100}
          aria-valuemin={0}
          aria-valuenow={task.progressPercent}
          className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100"
          role="progressbar"
        >
          <div className="h-full rounded-full bg-emerald-600" style={{ width: `${task.progressPercent}%` }} />
        </div>
      </div>

      <span className={`mt-3 inline-flex rounded-full px-2 py-1 text-[11px] font-semibold ring-1 ${getStatusClass(task.status)}`}>
        {task.statusLabel}
      </span>
      <div className="mt-3 rounded-md bg-slate-50 p-2 text-xs">
        <p className="font-semibold text-slate-700">Trạng thái báo cáo</p>
        <p className={`mt-1 font-medium ${reportStatus === 'APPROVED' ? 'text-emerald-700' : 'text-slate-600'}`}>
          {reportStatus ? REPORT_STATUS_LABELS[reportStatus] : 'Chưa nộp báo cáo'}
        </p>
        {reportActionLabel ? (
          <Button className="mt-2" onClick={() => onReportAction?.(task)} size="sm" variant="outline">
            {reportActionLabel}
          </Button>
        ) : null}
      </div>
      {isUpdating ? <p className="mt-2 text-[11px] font-medium text-slate-500">Đang cập nhật...</p> : null}
    </article>
  );
});
