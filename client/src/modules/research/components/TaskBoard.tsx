import { useMemo, useState } from 'react';
import type { DragEvent } from 'react';

import { toast } from '../../../shared/components';
import { useTasksByMilestone, useUpdateTaskStatus } from '../hooks';
import {
  canDragTask,
  canMoveTask,
  formatTaskColumn,
  getTaskDragDisabledReason,
  groupTasksByColumn,
  TASK_COLUMNS,
} from '../taskBoardHelpers';
import type { TaskBoardRole } from '../taskBoardHelpers';
import type { ResearchTask, ResearchTaskStatus, TaskColumn } from '../types';
import { getStatusClass } from '../utils';
import { TaskCard } from './TaskCard';

interface TaskBoardProps {
  milestoneId: number;
  readonly?: boolean;
  role?: TaskBoardRole;
  currentUserId?: number | null;
}

export function TaskBoard({ milestoneId, readonly = true, role, currentUserId }: TaskBoardProps) {
  const [draggedTask, setDraggedTask] = useState<{ taskId: number; sourceStatus: ResearchTaskStatus } | null>(null);
  const [dropColumn, setDropColumn] = useState<TaskColumn | null>(null);
  const { data: tasks = [], isError, isLoading, refetch } = useTasksByMilestone(milestoneId);
  const updateStatus = useUpdateTaskStatus(milestoneId);
  const tasksByColumn = useMemo(() => groupTasksByColumn(tasks), [tasks]);
  const canInteract = !readonly && Boolean(role);

  function handleDragStart(event: DragEvent<HTMLElement>, task: ResearchTask) {
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', String(task.id));
    setDraggedTask({ taskId: task.id, sourceStatus: task.status });
  }

  function handleDragOver(event: DragEvent<HTMLElement>, column: TaskColumn) {
    if (!canInteract || updateStatus.isPending) {
      return;
    }
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
    setDropColumn(column);
  }

  function handleDrop(event: DragEvent<HTMLElement>, column: TaskColumn) {
    event.preventDefault();
    if (!canInteract || updateStatus.isPending) {
      return;
    }

    const taskId = draggedTask?.taskId ?? Number(event.dataTransfer.getData('text/plain'));
    const task = tasks.find((item) => item.id === taskId);
    const sourceStatus = draggedTask?.sourceStatus ?? task?.status;
    setDraggedTask(null);
    setDropColumn(null);

    if (!task || !sourceStatus || sourceStatus === column) {
      return;
    }
    if (!canMoveTask({
      role,
      currentUserId,
      task,
      fromStatus: sourceStatus,
      toStatus: column,
    })) {
      toast.error(
        role === 'STUDENT'
          ? 'Bạn không có quyền chuyển nhiệm vụ sang trạng thái này.'
          : 'Không thể chuyển nhiệm vụ sang trạng thái này.',
      );
      return;
    }

    updateStatus.mutate({ taskId: task.id, status: column });
  }

  function handleDragEnd() {
    setDraggedTask(null);
    setDropColumn(null);
  }

  return (
    <section className="border-t border-slate-200 pt-5" aria-label="Nhiệm vụ nghiên cứu">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h4 className="text-base font-semibold text-slate-950">Nhiệm vụ nghiên cứu</h4>
          <p className="mt-1 text-sm text-slate-600">Theo dõi tiến độ nhiệm vụ trong mốc nghiên cứu này.</p>
        </div>
        {!canInteract ? (
          <span className="shrink-0 rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600">
            Chỉ xem
          </span>
        ) : (
          <span className="shrink-0 rounded-full bg-blue-50 px-3 py-1 text-xs font-semibold text-blue-700">
            Kéo thả để cập nhật
          </span>
        )}
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
          <div className="grid min-w-[1120px] grid-cols-5 gap-3">
            {TASK_COLUMNS.map((column) => {
              const columnTasks = tasksByColumn.get(column) ?? [];

              return (
                <section
                  key={column}
                  className={`min-h-52 rounded-md border p-3 transition ${
                    canInteract && dropColumn === column
                      ? 'border-blue-300 bg-blue-50'
                      : 'border-transparent bg-slate-50'
                  }`}
                  onDragOver={(event) => handleDragOver(event, column)}
                  onDrop={(event) => handleDrop(event, column)}
                >
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
                      <TaskCard
                        key={task.id}
                        task={task}
                        draggable={canInteract && !updateStatus.isPending && canDragTask(task, role, currentUserId)}
                        dragDisabledReason={canInteract
                          ? getTaskDragDisabledReason(task, role, currentUserId)
                          : undefined}
                        isUpdating={updateStatus.isPending && updateStatus.variables?.taskId === task.id}
                        onDragEnd={handleDragEnd}
                        onDragStart={(event) => handleDragStart(event, task)}
                      />
                    ))}
                    {!columnTasks.length && canInteract ? (
                      <p className="rounded-md border border-dashed border-slate-200 px-2 py-6 text-center text-xs text-slate-400">
                        Thả nhiệm vụ vào đây
                      </p>
                    ) : null}
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
