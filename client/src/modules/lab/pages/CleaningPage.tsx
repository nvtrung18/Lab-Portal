import { useMemo, useState } from 'react';

import { Button, EmptyState, ErrorState } from '../../../shared/components';
import { getManagedLabId, getManagedLabName } from '../../../shared/utils/membership';
import type { CleaningTask } from '../../booking/api';
import { AssignCleaningModal } from '../../booking/components';
import { useCancelCleaningTask, useLabCleaningTasks } from '../../booking/hooks';
import {
  formatCleaningStatus,
  getCleaningStatusClass,
  isUsableSlot,
} from '../../booking/utils';
import { formatDateTime } from '../../penalty/utils';
import { useCurrentUser } from '../../user/hooks';
import { useI18n } from '../../../shared/i18n';

function isDoneStatus(status: string) {
  return status === 'DONE' || status === 'COMPLETED';
}

function canAssign(task: CleaningTask) {
  return !isDoneStatus(task.status);
}

function canCancel(task: CleaningTask) {
  return Boolean(task.id) && task.status !== 'CANCELLED' && !isDoneStatus(task.status);
}

export function CleaningPage() {
  const { t } = useI18n();
  const filters = [
    { value: 'ALL', label: t('common.all') },
    { value: 'PENDING', label: t('cleaning.pending') },
    { value: 'ASSIGNED', label: t('cleaning.assigned') },
    { value: 'DONE', label: t('cleaning.done') },
  ];
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [assignTask, setAssignTask] = useState<CleaningTask | null>(null);
  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const managedLabId = getManagedLabId(currentUser);
  const managedLabName = getManagedLabName(currentUser);
  const { data: tasks = [], isLoading, isError, refetch } = useLabCleaningTasks(managedLabId);
  const cancelTask = useCancelCleaningTask(managedLabId);

  const visibleTasks = useMemo(() => tasks.filter(isUsableSlot), [tasks]);

  const filteredTasks = useMemo(() => {
    if (statusFilter === 'ALL') {
      return visibleTasks;
    }
    if (statusFilter === 'DONE') {
      return visibleTasks.filter((task) => isDoneStatus(task.status));
    }
    return visibleTasks.filter((task) => task.status === statusFilter);
  }, [statusFilter, visibleTasks]);

  if (isLoadingUser || isLoading) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="h-6 w-44 animate-pulse rounded bg-slate-200" />
        <div className="mt-6 grid gap-4 md:grid-cols-2">
          <div className="h-36 animate-pulse rounded bg-slate-100" />
          <div className="h-36 animate-pulse rounded bg-slate-100" />
        </div>
      </section>
    );
  }

  if (!managedLabId) {
    return (
      <section className="rounded-lg border border-amber-200 bg-white p-6 text-sm text-amber-700 shadow-sm">
        {t('layout.unassignedLab')}
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-950 dark:text-white">{t('cleaning.title')}</h1>
        <p className="mt-3 text-sm font-medium text-slate-800">
          {t('lab.label')}: {managedLabName ?? `#${managedLabId}`}
        </p>
      </div>

      <div className="flex max-w-full gap-2 overscroll-x-contain overflow-x-auto pb-1">
        {filters.map((filter) => (
          <Button
            key={filter.value}
            size="sm"
            variant={statusFilter === filter.value ? 'primary' : 'outline'}
            onClick={() => setStatusFilter(filter.value)}
          >
            {filter.label}
          </Button>
        ))}
      </div>

      {isError ? (
        <ErrorState onRetry={() => refetch()}>
          {t('cleaning.loadFailed')}
        </ErrorState>
      ) : !filteredTasks.length ? (
        <EmptyState>
          {t('cleaning.empty')}
        </EmptyState>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {filteredTasks.map((task) => (
            <article
              key={task.id ?? `slot-${task.slotId}-pending`}
              className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
            >
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <h3 className="text-base font-semibold text-slate-950 dark:text-white">{task.labName ?? t('lab.shortName')}</h3>
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
                  <dt className="font-semibold text-slate-700">{t('cleaning.participants')}</dt>
                  <dd className="mt-1 text-slate-600">{task.participantCount ?? 0}</dd>
                </div>
                <div>
                  <dt className="font-semibold text-slate-700">{t('cleaning.assignedStudent')}</dt>
                  <dd className="mt-1 text-slate-600">
                    {task.staffName || task.staffEmail || t('lab.notAssigned')}
                  </dd>
                </div>
              </dl>

              <div className="mt-5 flex flex-wrap gap-2">
                {canAssign(task) ? (
                  <Button onClick={() => setAssignTask(task)} size="sm">
                    {t('cleaning.assign')}
                  </Button>
                ) : null}
                {canCancel(task) ? (
                  <Button
                    disabled={cancelTask.isPending}
                    loading={cancelTask.isPending}
                    loadingText={t('cleaning.cancelling')}
                    size="sm"
                    variant="danger"
                    onClick={() => {
                      if (task.id) {
                        cancelTask.mutate(task.id);
                      }
                    }}
                  >
                    {t('cleaning.cancel')}
                  </Button>
                ) : null}
              </div>
            </article>
          ))}
        </div>
      )}

      <AssignCleaningModal
        labId={managedLabId}
        task={assignTask}
        isOpen={Boolean(assignTask)}
        onClose={() => setAssignTask(null)}
      />
    </section>
  );
}
