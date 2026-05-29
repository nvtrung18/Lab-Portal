import { useCallback, useMemo, useState } from 'react';
import type { DragEvent } from 'react';

import { Button, EmptyState, ErrorState, toast } from '../../../shared/components';
import { useReportsByMilestone, useTasksByMilestone, useUpdateTaskStatus, useCreateTask, useResearchGroupMembers } from '../hooks';
import {
  canDragTask,
  canMoveTask,
  formatTaskColumn,
  getTaskDragDisabledReason,
  groupTasksByColumn,
  TASK_COLUMNS,
} from '../taskBoardHelpers';
import type { TaskBoardRole } from '../taskBoardHelpers';
import type { ResearchReport, ResearchTask, ResearchTaskStatus, TaskColumn } from '../types';
import { getStatusClass } from '../utils';
import { TaskCard } from './TaskCard';
import { ReportUploadModal } from './ReportUploadModal';
import { CreateTaskModal } from './CreateTaskModal';

interface TaskBoardProps {
  milestoneId: number;
  readonly?: boolean;
  role?: TaskBoardRole;
  currentUserId?: number | null;
  projectId?: number | null;
  groupId?: number | null;
  labId?: number | null;
}

export function TaskBoard({ milestoneId, readonly = true, role, currentUserId, projectId, groupId, labId }: TaskBoardProps) {
  const [draggedTask, setDraggedTask] = useState<{ taskId: number; sourceStatus: ResearchTaskStatus } | null>(null);
  const [dropColumn, setDropColumn] = useState<TaskColumn | null>(null);
  const [reportTask, setReportTask] = useState<ResearchTask | null>(null);
  const [isCreateTaskOpen, setIsCreateTaskOpen] = useState(false);
  const { data: tasks = [], isError, isLoading, refetch } = useTasksByMilestone(milestoneId);
  const memberView = role === 'STUDENT_MEMBER';

  const { data: groupMembers = [], isLoading: isLoadingMembers } = useResearchGroupMembers(
    role === 'LAB_MANAGER' && groupId ? groupId : null
  );
  const createTaskMutation = useCreateTask(milestoneId, projectId, groupId);
  const { data: reports = [] } = useReportsByMilestone(milestoneId, memberView);
  const updateStatus = useUpdateTaskStatus(milestoneId, projectId);

  const visibleTasks = useMemo(() => {
    if (memberView && currentUserId) {
      return tasks.filter((t) => t.assignedToStudentId === currentUserId);
    }
    return tasks;
  }, [tasks, memberView, currentUserId]);

  const tasksByColumn = useMemo(() => groupTasksByColumn(visibleTasks), [visibleTasks]);
  const latestReportByTaskId = useMemo(() => {
    const latest = new Map<number, ResearchReport>();
    reports.forEach((report) => {
      if (!report.taskId) {
        return;
      }
      const current = latest.get(report.taskId);
      if (!current || report.version > current.version) {
        latest.set(report.taskId, report);
      }
    });
    return latest;
  }, [reports]);
  const canInteract = !readonly && Boolean(role);
  const boardTitle = memberView
    ? 'Nhiệm vụ của tôi'
    : role === 'GROUP_LEADER'
      ? 'Bảng tiến độ nhóm'
      : 'Nhiệm vụ nghiên cứu';

  const handleDragStart = useCallback((event: DragEvent<HTMLElement>, task: ResearchTask) => {
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', String(task.id));
    setDraggedTask({ taskId: task.id, sourceStatus: task.status });
  }, []);

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
        role === 'STUDENT_MEMBER'
          ? 'Bạn không có quyền chuyển nhiệm vụ sang trạng thái này.'
          : 'Không thể chuyển nhiệm vụ sang trạng thái này.',
      );
      return;
    }

    updateStatus.mutate({ taskId: task.id, status: column });
  }

  const handleDragEnd = useCallback(() => {
    setDraggedTask(null);
    setDropColumn(null);
  }, []);

  return (
    <section className="min-w-0 border-t border-slate-200 pt-5" aria-label="Nhiệm vụ nghiên cứu">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
        <div className="min-w-0">
          <h4 className="text-base font-semibold text-slate-950">
            {boardTitle}
          </h4>
          <p className="mt-1 text-sm text-slate-600">
            {memberView
              ? 'Chỉ hiển thị nhiệm vụ được giao cho bạn trong mốc nghiên cứu này.'
              : 'Theo dõi tiến độ nhiệm vụ trong mốc nghiên cứu này.'}
          </p>
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
        <ErrorState className="mt-4" onRetry={() => refetch()}>
          Không thể tải danh sách nhiệm vụ nghiên cứu.
        </ErrorState>
      ) : !tasks.length ? (
        <EmptyState className="mt-4">
          <div className="flex flex-col items-center justify-center text-center py-6 gap-3">
            <p className="text-slate-600 font-medium">
              {memberView
                ? 'Bạn chưa được giao nhiệm vụ nào trong nhóm này.'
                : 'Nhóm chưa có nhiệm vụ nào.'}
            </p>
            {role === 'LAB_MANAGER' && (
              <Button onClick={() => setIsCreateTaskOpen(true)} size="sm">
                Tạo nhiệm vụ
              </Button>
            )}
          </div>
        </EmptyState>
      ) : (
        <div className="mt-4 max-w-full overscroll-x-contain overflow-x-auto pb-2">
          <div className="flex w-max gap-3">
            {TASK_COLUMNS.map((column) => {
              const columnTasks = tasksByColumn.get(column) ?? [];

              return (
                <section
                  key={column}
                  className={`min-h-52 w-[calc(100vw-4.5rem)] max-w-72 shrink-0 rounded-md border p-3 transition sm:w-60 xl:w-64 ${
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
                        reportActionLabel={memberView && task.assignedToStudentId === currentUserId
                          ? latestReportByTaskId.get(task.id)?.status === 'NEEDS_REVISION'
                            ? 'Nộp lại báo cáo'
                            : task.status === 'DOING'
                              ? 'Nộp báo cáo'
                              : null
                          : null}
                        onReportAction={setReportTask}
                        onDragEnd={handleDragEnd}
                        onDragStart={handleDragStart}
                        currentUserId={currentUserId}
                        projectId={projectId}
                        groupId={groupId}
                        role={role}
                        labId={labId}
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
      {memberView && projectId && reportTask ? (
        <ReportUploadModal
          groupId={groupId}
          isOpen
          milestoneId={milestoneId}
          projectId={projectId}
          tasks={[reportTask]}
          title={latestReportByTaskId.get(reportTask.id)?.status === 'NEEDS_REVISION'
            ? 'Nộp lại báo cáo tiến độ'
            : 'Nộp báo cáo tiến độ'}
          onClose={() => setReportTask(null)}
        />
      ) : null}

      {role === 'LAB_MANAGER' && (
        <CreateTaskModal
          isOpen={isCreateTaskOpen}
          groupMembers={groupMembers}
          isLoadingMembers={isLoadingMembers}
          isSubmitting={createTaskMutation.isPending}
          onClose={() => setIsCreateTaskOpen(false)}
          onSubmit={(payload) => {
            createTaskMutation.mutate(payload, {
              onSuccess: () => setIsCreateTaskOpen(false),
            });
          }}
        />
      )}
    </section>
  );
}
