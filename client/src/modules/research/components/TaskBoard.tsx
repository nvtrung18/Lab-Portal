import { useMemo } from 'react';

import { useTasksByMilestone } from '../hooks';
import { formatTaskColumn, groupTasksByColumn, TASK_COLUMNS } from '../taskBoardHelpers';
import { getStatusClass } from '../utils';
import { TaskCard } from './TaskCard';

interface TaskBoardProps {
  milestoneId: number;
  readonly?: boolean;
}

export function TaskBoard({ milestoneId, readonly = true }: TaskBoardProps) {
  const { data: tasks = [], isError, isLoading, refetch } = useTasksByMilestone(milestoneId);
  const tasksByColumn = useMemo(() => groupTasksByColumn(tasks), [tasks]);

  return (
    <section className="border-t border-slate-200 pt-5" aria-label="Nhiệm vụ nghiên cứu">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h4 className="text-base font-semibold text-slate-950">Nhiệm vụ nghiên cứu</h4>
          <p className="mt-1 text-sm text-slate-600">Theo dõi tiến độ nhiệm vụ trong mốc nghiên cứu này.</p>
        </div>
        {readonly ? (
          <span className="shrink-0 rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600">
            Chỉ xem
          </span>
        ) : null}
      </div>

      {isLoading ? (
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3].map((item) => (
            <div key={item} className="h-32 animate-pulse rounded-md bg-slate-100" />
          ))}
        </div>
      ) : isError ? (
        <div className="mt-4 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          Không thể tải danh sách nhiệm vụ nghiên cứu.
          <button className="ml-3 font-semibold underline" type="button" onClick={() => refetch()}>
            Tải lại
          </button>
        </div>
      ) : !tasks.length ? (
        <div className="mt-4 rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
          Mốc nghiên cứu này chưa có nhiệm vụ nào.
        </div>
      ) : (
        <div className="mt-4 overflow-x-auto pb-2">
          <div className="grid min-w-[920px] grid-cols-4 gap-3">
            {TASK_COLUMNS.map((column) => {
              const columnTasks = tasksByColumn.get(column) ?? [];

              return (
                <section key={column} className="rounded-md bg-slate-50 p-3">
                  <div className="mb-3 flex items-center justify-between gap-2">
                    <span className={`rounded-full px-2 py-1 text-xs font-semibold ring-1 ${getStatusClass(column)}`}>
                      {formatTaskColumn(column)}
                    </span>
                    <span className="rounded-full bg-white px-2 py-1 text-xs font-semibold text-slate-600">
                      {columnTasks.length}
                    </span>
                  </div>
                  <div className="space-y-3">
                    {columnTasks.map((task) => (
                      <TaskCard key={task.id} task={task} />
                    ))}
                  </div>
                </section>
              );
            })}
          </div>
        </div>
      )}
    </section>
  );
}
