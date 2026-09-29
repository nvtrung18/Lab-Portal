import { memo, useMemo, useState } from 'react';
import type { DragEvent } from 'react';

import type { ResearchTask, ResearchTaskStatus, TaskColumn } from '../types';
import { getStatusClass, formatDate } from '../utils';
import { Button } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { useReportsByTask } from '../hooks';
import { ReportHistoryModal } from './ReportHistoryModal';
import { LatestReportSummary } from './LatestReportSummary';
import { formatTaskColumn, type TaskBoardRole } from '../taskBoardHelpers';

interface TaskCardProps {
  task: ResearchTask;
  draggable?: boolean;
  isUpdating?: boolean;
  isStatusChangeDisabled?: boolean;
  statusTargets?: TaskColumn[];
  onStatusChange?: (status: TaskColumn) => void;
  dragDisabledReason?: string;
  reportActionLabel?: string | null;
  onReportAction?: (task: ResearchTask) => void;
  onDragStart?: (event: DragEvent<HTMLElement>, task: ResearchTask) => void;
  onDragEnd?: () => void;
  currentUserId?: number | null;
  projectId?: number | null;
  groupId?: number | null;
  role?: TaskBoardRole;
  labId?: number | null;
}

export const TaskCard = memo(function TaskCard({
  task,
  draggable = false,
  isUpdating = false,
  isStatusChangeDisabled = false,
  statusTargets = [],
  onStatusChange,
  dragDisabledReason,
  reportActionLabel,
  onReportAction,
  onDragStart,
  onDragEnd,
  currentUserId,
  projectId,
  groupId,
  role,
  labId,
}: TaskCardProps) {
  const { locale, t } = useI18n();
  const [isHistoryOpen, setIsHistoryOpen] = useState(false);
  const [defaultOpenLatestReview, setDefaultOpenLatestReview] = useState(false);
  const { data: reports = [] } = useReportsByTask(task.id);

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
            {t('research.task.overdue')}
          </span>
        ) : null}
      </div>

      {task.description ? <p className="mt-2 line-clamp-2 text-xs leading-5 text-slate-600">{task.description}</p> : null}

      <dl className="mt-3 space-y-2 text-xs">
        <div>
          <dt className="font-semibold text-slate-700">{t('research.task.assigneeLabel')}</dt>
          <dd className="mt-1 break-words text-slate-600">
            {task.assigneeName ?? task.assigneeEmail ?? t('common.notAssigned')}
          </dd>
        </div>
        <div>
          <dt className="font-semibold text-slate-700">{t('research.task.deadline')}</dt>
          <dd className="mt-1 text-slate-600">{formatDate(task.deadline, locale)}</dd>
        </div>
      </dl>

      <div className="mt-3">
        <div className="flex items-center justify-between text-xs">
          <span className="font-semibold text-slate-700">{t('research.task.progress')}</span>
          <span className="font-semibold text-slate-950">{task.progressPercent}%</span>
        </div>
        <div
          aria-label={`${t('research.task.progress')} ${task.progressPercent}%`}
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
        {getTaskStatusLabel(task.status, t)}
      </span>

      {statusTargets.length > 0 ? (
        <label className="mt-3 block text-xs font-semibold text-slate-700">
          {t('research.task.changeStatus')}
          <select
            className="mt-1 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-normal text-slate-900"
            disabled={isStatusChangeDisabled}
            value=""
            onChange={(event) => onStatusChange?.(event.target.value as TaskColumn)}
          >
            <option value="" disabled>{t('research.task.changeStatus')}</option>
            {statusTargets.map((status) => (
              <option key={status} value={status}>{formatTaskColumn(status, locale)}</option>
            ))}
          </select>
        </label>
      ) : null}
      
      <div className="mt-3 rounded-md bg-slate-50 p-2.5 text-xs space-y-2">
        <LatestReportSummary reports={reports} />

        <div className="flex flex-wrap gap-2 pt-1 border-t border-slate-200/50">
          {reportActionLabel ? (
            <Button onClick={() => onReportAction?.(task)} size="sm" variant="outline">
              {reportActionLabel}
            </Button>
          ) : null}
          {reports.length > 0 ? (
            <>
              <Button
                onClick={() => {
                  setIsHistoryOpen(true);
                  setDefaultOpenLatestReview(false);
                }}
                size="sm"
                variant="outline"
                type="button"
              >
                {t('research.task.history')}
              </Button>
              <Button
                onClick={() => {
                  setIsHistoryOpen(true);
                  setDefaultOpenLatestReview(true);
                }}
                size="sm"
                variant="outline"
                type="button"
              >
                {t('research.task.comments')}
              </Button>
            </>
          ) : null}
        </div>
      </div>
      
      {isUpdating ? <p className="mt-2 text-[11px] font-medium text-slate-500">{t('research.task.updating')}</p> : null}

      {reports.length > 0 && (
        <ReportHistoryModal
          isOpen={isHistoryOpen}
          onClose={() => setIsHistoryOpen(false)}
          taskId={task.id}
          taskTitle={task.title}
          currentUserId={currentUserId}
          role={role}
          labId={labId}
          groupId={groupId}
          defaultOpenLatestReview={defaultOpenLatestReview}
        />
      )}
    </article>
  );
});

function getTaskStatusLabel(status: ResearchTaskStatus, t: ReturnType<typeof useI18n>['t']) {
  return t(`research.task.status.${{
    TODO: 'todo',
    DOING: 'doing',
    WAITING_REVIEW: 'waitingReview',
    NEEDS_REVISION: 'needsRevision',
    DONE: 'done',
    OVERDUE: 'overdue',
    CANCELLED: 'cancelled',
  }[status]}`);
}
