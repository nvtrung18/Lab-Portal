import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { Button, EmptyState, ErrorState, LoadingState, toast } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { useMyResearchTasks, useReportsByTask } from '../hooks';
import { updateTaskStatus } from '../api';
import type { ResearchReportStatus, ResearchTask, ResearchTaskStatus, TaskColumn } from '../types';
import { formatDate, getApiErrorMessage } from '../utils';
import { ReportStatusBadge } from './ReportStatusBadge';
import { ReportUploadModal } from './ReportUploadModal';
import { ReportHistoryModal } from './ReportHistoryModal';

interface MyResearchTasksProps {
  groupId: number;
  projectId: number;
  currentUserId?: number | null;
  emptyMessage?: string;
}

export function MyResearchTasks({
  groupId,
  projectId,
  currentUserId,
  emptyMessage,
}: MyResearchTasksProps) {
  const { t } = useI18n();
  const { data: tasks = [], error, isLoading, isError, refetch } = useMyResearchTasks(groupId);
  const errorMessage = getApiErrorMessage(error, {
    fallback: t('research.task.loadFailed'),
    forbidden: t('research.task.forbidden'),
  });

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <h3 className="text-lg font-semibold text-slate-950">{t('research.task.myTitle')}</h3>

      {isLoading ? (
        <LoadingState className="mt-5">{t('research.task.loading')}</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          {errorMessage}
        </ErrorState>
      ) : !tasks.length ? (
        <EmptyState className="mt-5">{emptyMessage ?? t('research.task.empty')}</EmptyState>
      ) : (
        <div className="mt-5 space-y-4">
          {tasks.map((task) => (
            <MyTaskReportCard
              currentUserId={currentUserId}
              groupId={groupId}
              key={task.id}
              projectId={projectId}
              task={task}
            />
          ))}
        </div>
      )}
    </section>
  );
}

function MyTaskReportCard({
  groupId,
  projectId,
  currentUserId,
  task,
}: {
  groupId: number;
  projectId: number;
  currentUserId?: number | null;
  task: ResearchTask;
}) {
  const { locale, t } = useI18n();
  const [isUploadOpen, setIsUploadOpen] = useState(false);
  const [isHistoryOpen, setIsHistoryOpen] = useState(false);
  const [defaultOpenLatestReview, setDefaultOpenLatestReview] = useState(false);
  const queryClient = useQueryClient();

  const updateStatus = useMutation({
    mutationFn: ({ taskId, status }: { taskId: number; status: TaskColumn }) =>
      updateTaskStatus(taskId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.research.myTasks(groupId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.research.myGroupMilestones(groupId) });
      if (projectId) {
        queryClient.invalidateQueries({ queryKey: queryKeys.research.projectStats(projectId) });
      }
      toast.success(t('research.task.started'));
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, { fallback: t('common.loadError') }));
    },
  });

  const { data: reports = [] } = useReportsByTask(task.id);
  const latestReport = [...reports].sort((first, second) => (
    second.version - first.version ||
    (second.createdAt ?? '').localeCompare(first.createdAt ?? '')
  ))[0];
  const latestReportStatus = latestReport?.status ?? task.latestReportStatus ?? null;
  const hasLatestReport = Boolean(latestReport ?? task.latestReportStatus);
  const assignedToCurrentUser = currentUserId != null && task.assignedToStudentId === currentUserId;
  const action = getMyTaskAction(task.status, latestReportStatus, hasLatestReport, assignedToCurrentUser);
  const statusBadge = getMyTaskStatusBadge(task.status, latestReportStatus, t);
  const uploadTitle = action === 'REPLACE'
    ? t('research.task.updateReport')
    : action === 'RESUBMIT'
    ? t('research.task.resubmitReport')
    : t('research.task.submitReport');

  return (
    <article className="rounded-md border border-slate-200 p-4">
      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
        <div className="min-w-0 flex-1">
          {task.milestoneTitle ? (
            <p className="text-xs text-slate-500 mb-1">
              {t('research.task.milestonePrefix')}: <span className="font-semibold text-slate-700">{task.milestoneTitle}</span>
            </p>
          ) : null}
          <h4 className="font-semibold text-slate-950">{task.title}</h4>
          {task.description ? <p className="mt-1 text-sm text-slate-600">{task.description}</p> : null}
          <p className="mt-2 text-xs text-slate-500">
            {t('research.task.deadline')}: {formatDate(task.deadline, locale)} · {t('research.task.status')}: {statusBadge.taskLabel}
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <span className="text-xs font-semibold text-slate-700">{t('research.task.latestReportStatus')}:</span>
            <ReportStatusBadge status={latestReportStatus} />
          </div>
          {statusBadge.reportLabel ? (
            <p className="mt-2 text-xs font-medium text-slate-600">{statusBadge.reportLabel}</p>
          ) : null}
          {latestReportStatus === 'SUBMITTED' && (
            <p className="mt-2 text-xs italic text-blue-600 font-medium">
              {t('research.task.submittedPending')}
            </p>
          )}
          {latestReportStatus === 'LEADER_REVIEWED' && (
            <p className="mt-2 text-xs italic text-amber-600 font-medium">
              {t('research.task.leaderReviewedPending')}
            </p>
          )}
        </div>

        <div className="flex flex-wrap gap-2 sm:flex-col sm:items-end shrink-0">
          {action === 'START' ? (
            <Button
              onClick={() => updateStatus.mutate({ taskId: task.id, status: 'DOING' })}
              size="sm"
              loading={updateStatus.isPending}
            >
              {t('research.task.start')}
            </Button>
          ) : null}
          {action === 'SUBMIT' || action === 'RESUBMIT' || action === 'REPLACE' ? (
            <Button onClick={() => setIsUploadOpen(true)} size="sm" variant={action === 'REPLACE' ? 'outline' : 'primary'}>
              {action === 'REPLACE' ? t('research.task.updateReport') : action === 'RESUBMIT' ? t('research.task.resubmitReport') : t('research.task.submitReport')}
            </Button>
          ) : null}
        </div>
      </div>

      <div className="mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-3">
        {reports.length > 0 ? (
          <>
            <Button
              onClick={() => {
                setIsHistoryOpen(true);
                setDefaultOpenLatestReview(false);
              }}
              size="sm"
              variant="outline"
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
            >
              {t('research.task.comments')}
            </Button>
          </>
        ) : null}
      </div>

      <ReportUploadModal
        groupId={groupId}
        isOpen={isUploadOpen}
        milestoneId={task.milestoneId}
        projectId={projectId}
        tasks={[task]}
        title={uploadTitle}
        mode={action === 'REPLACE' ? 'replace' : action === 'RESUBMIT' ? 'resubmit' : 'create'}
        reportId={action === 'REPLACE' ? latestReport?.id : null}
        initialValues={action === 'REPLACE' ? latestReport : null}
        onClose={() => setIsUploadOpen(false)}
      />

      {reports.length > 0 && (
        <ReportHistoryModal
          isOpen={isHistoryOpen}
          onClose={() => setIsHistoryOpen(false)}
          taskId={task.id}
          taskTitle={task.title}
          currentUserId={currentUserId}
          role="STUDENT_MEMBER"
          groupId={groupId}
          defaultOpenLatestReview={defaultOpenLatestReview}
        />
      )}
    </article>
  );
}

type MyTaskAction = 'START' | 'SUBMIT' | 'RESUBMIT' | 'REPLACE' | 'NONE';

const RESUBMIT_REPORT_STATUSES = new Set<ResearchReportStatus>([
  'NEEDS_REVISION',
  'LEADER_REJECTED',
  'MANAGER_REJECTED',
]);

const BLOCKED_REPORT_STATUSES = new Set<ResearchReportStatus>([
  'LEADER_REVIEWED',
  'APPROVED',
]);

function getMyTaskAction(
  taskStatus: ResearchTaskStatus,
  latestReportStatus: ResearchReportStatus | null,
  hasLatestReport: boolean,
  assignedToCurrentUser: boolean,
): MyTaskAction {
  if (!assignedToCurrentUser) {
    return 'NONE';
  }
  if (taskStatus === 'TODO') {
    return 'START';
  }
  if (taskStatus === 'DONE' || latestReportStatus === 'APPROVED') {
    return 'NONE';
  }
  if (latestReportStatus === 'SUBMITTED') {
    return 'REPLACE';
  }
  if (latestReportStatus && RESUBMIT_REPORT_STATUSES.has(latestReportStatus)) {
    return 'RESUBMIT';
  }
  if (latestReportStatus && BLOCKED_REPORT_STATUSES.has(latestReportStatus)) {
    return 'NONE';
  }
  if ((taskStatus === 'DOING' || taskStatus === 'NEEDS_REVISION') && !hasLatestReport) {
    return 'SUBMIT';
  }
  return 'NONE';
}

function getMyTaskStatusBadge(
  taskStatus: ResearchTaskStatus,
  latestReportStatus: ResearchReportStatus | null,
  t: ReturnType<typeof useI18n>['t'],
) {
  if (taskStatus === 'DONE' || latestReportStatus === 'APPROVED') {
    return { taskLabel: t('research.task.status.done'), reportLabel: t('research.task.reportStatus.approved') };
  }
  if (latestReportStatus) {
    return {
      taskLabel: getTaskStatusLabel(taskStatus, t),
      reportLabel: getReportStatusLabel(latestReportStatus, t),
    };
  }
  return {
    taskLabel: getTaskStatusLabel(taskStatus, t),
    reportLabel: null,
  };
}

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

function getReportStatusLabel(status: ResearchReportStatus, t: ReturnType<typeof useI18n>['t']) {
  return t(`research.task.reportStatus.${{
    SUBMITTED: 'submitted',
    LEADER_REVIEWED: 'leaderReviewed',
    NEEDS_REVISION: 'needsRevision',
    LEADER_REJECTED: 'leaderRejected',
    APPROVED: 'approved',
    MANAGER_REJECTED: 'managerRejected',
  }[status]}`);
}
