import { useMemo, useState } from 'react';

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

const FILTERS = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PENDING', label: 'Chưa phân công' },
  { value: 'ASSIGNED', label: 'Đã phân công' },
  { value: 'DONE', label: 'Đã hoàn thành' },
];

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
        Bạn chưa được phân công quản lý PTN nào.
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold text-slate-950">Vệ sinh PTN</h2>
        <p className="mt-2 text-sm text-slate-600">
          Quản lý và phân công nhiệm vụ vệ sinh cho các ca sử dụng PTN.
        </p>
        <p className="mt-3 text-sm font-medium text-slate-800">
          PTN: {managedLabName ?? `#${managedLabId}`}
        </p>
      </div>

      <div className="flex gap-2 overflow-x-auto">
        {FILTERS.map((filter) => (
          <button
            key={filter.value}
            className={[
              'whitespace-nowrap rounded-md px-3 py-2 text-sm font-semibold transition',
              statusFilter === filter.value
                ? 'bg-slate-900 text-white'
                : 'border border-slate-200 bg-white text-slate-700 hover:bg-slate-100',
            ].join(' ')}
            type="button"
            onClick={() => setStatusFilter(filter.value)}
          >
            {filter.label}
          </button>
        ))}
      </div>

      {isError ? (
        <div className="rounded-lg border border-red-200 bg-white p-6 text-sm text-red-700 shadow-sm">
          Không thể tải danh sách nhiệm vụ vệ sinh.
          <button className="ml-3 font-semibold underline" type="button" onClick={() => refetch()}>
            Tải lại
          </button>
        </div>
      ) : !filteredTasks.length ? (
        <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600 shadow-sm">
          Hiện chưa có ca sử dụng nào cần phân công vệ sinh.
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {filteredTasks.map((task) => (
            <article
              key={task.id ?? `slot-${task.slotId}-pending`}
              className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
            >
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <h3 className="text-base font-semibold text-slate-950">{task.labName ?? 'PTN'}</h3>
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
                  <dt className="font-semibold text-slate-700">Số sinh viên tham gia</dt>
                  <dd className="mt-1 text-slate-600">{task.participantCount ?? 0}</dd>
                </div>
                <div>
                  <dt className="font-semibold text-slate-700">Sinh viên được phân công</dt>
                  <dd className="mt-1 text-slate-600">
                    {task.staffName || task.staffEmail || 'Chưa phân công'}
                  </dd>
                </div>
              </dl>

              <div className="mt-5 flex flex-wrap gap-2">
                {canAssign(task) ? (
                  <button
                    className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white"
                    type="button"
                    onClick={() => setAssignTask(task)}
                  >
                    Phân công vệ sinh
                  </button>
                ) : null}
                {canCancel(task) ? (
                  <button
                    className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700 disabled:opacity-60"
                    disabled={cancelTask.isPending}
                    type="button"
                    onClick={() => {
                      if (task.id) {
                        cancelTask.mutate(task.id);
                      }
                    }}
                  >
                    Hủy nhiệm vụ
                  </button>
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
