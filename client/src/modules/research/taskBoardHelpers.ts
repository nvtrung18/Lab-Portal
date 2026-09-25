import type { RawResearchTask, ResearchTask, ResearchTaskStatus, TaskColumn } from './types';
import { getStoredLocale, translate, type Locale } from '../../shared/i18n';

export type TaskBoardRole = 'LAB_MANAGER' | 'GROUP_LEADER' | 'STUDENT_MEMBER';

export const TASK_COLUMNS: TaskColumn[] = ['TODO', 'DOING', 'WAITING_REVIEW', 'NEEDS_REVISION', 'DONE'];

const taskStatusKeys: Record<ResearchTaskStatus, string> = {
  TODO: 'research.task.status.todo',
  DOING: 'research.task.status.doing',
  WAITING_REVIEW: 'research.task.status.waitingReview',
  NEEDS_REVISION: 'research.task.status.needsRevision',
  DONE: 'research.task.status.done',
  OVERDUE: 'research.task.status.overdue',
  CANCELLED: 'research.task.status.cancelled',
};

const taskColumnKeys: Record<TaskColumn, string> = {
  TODO: 'research.task.status.todo',
  DOING: 'research.task.status.doing',
  WAITING_REVIEW: 'research.task.status.waitingReview',
  NEEDS_REVISION: 'research.task.status.needsRevision',
  DONE: 'research.task.status.done',
};

function normalizeStatus(status?: RawResearchTask['status']): ResearchTaskStatus {
  if (status === 'IN_PROGRESS') {
    return 'DOING';
  }
  if (status === 'REVIEW') {
    return 'WAITING_REVIEW';
  }
  return status ?? 'TODO';
}

export function formatTaskStatus(status: ResearchTaskStatus, locale: Locale = getStoredLocale()) {
  return translate(taskStatusKeys[status], locale, status);
}

export function formatTaskColumn(column: TaskColumn, locale: Locale = getStoredLocale()) {
  return translate(taskColumnKeys[column], locale, column);
}

export function getTaskColumn(status: ResearchTaskStatus): TaskColumn {
  switch (status) {
    case 'TODO':
      return 'TODO';
    case 'WAITING_REVIEW':
      return 'WAITING_REVIEW';
    case 'NEEDS_REVISION':
      return 'NEEDS_REVISION';
    case 'DONE':
    case 'CANCELLED':
      return 'DONE';
    case 'DOING':
    case 'OVERDUE':
      return 'DOING';
  }
}

export function isTaskOverdue(task: Pick<RawResearchTask, 'deadline' | 'status'>) {
  const status = normalizeStatus(task.status);
  if (status === 'OVERDUE') {
    return true;
  }
  if (!task.deadline || status === 'DONE' || status === 'CANCELLED') {
    return false;
  }

  const now = new Date();
  const today = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
  ].join('-');
  return task.deadline < today;
}

export function normalizeTask(raw: RawResearchTask): ResearchTask {
  const status = normalizeStatus(raw.status);

  return {
    id: raw.id,
    milestoneId: raw.milestoneId,
    projectId: raw.projectId ?? null,
    title: raw.title,
    description: raw.description ?? null,
    assignedToStudentId: raw.assignedToStudentId ?? raw.assigneeId ?? null,
    assigneeName: raw.assignedToStudentName ?? null,
    assigneeEmail: raw.assignedToStudentEmail ?? null,
    deadline: raw.deadline ?? null,
    status,
    statusLabel: formatTaskStatus(status),
    column: getTaskColumn(status),
    progressPercent: Math.min(100, Math.max(0, raw.progressPercent ?? 0)),
    isOverdue: isTaskOverdue({ deadline: raw.deadline, status }),
    milestoneTitle: raw.milestoneTitle ?? raw.milestone_title ?? null,
    latestReportStatus: raw.latestReportStatus ?? raw.latest_report_status ?? null,
    createdAt: raw.createdAt ?? null,
    updatedAt: raw.updatedAt ?? null,
  };
}

export function groupTasksByColumn(tasks: ResearchTask[]) {
  const grouped = new Map<TaskColumn, ResearchTask[]>(
    TASK_COLUMNS.map((column) => [column, []]),
  );

  tasks.forEach((task) => grouped.get(task.column)?.push(task));
  return grouped;
}

const managerStatusTransitions: Partial<Record<ResearchTaskStatus, TaskColumn[]>> = {
  TODO: ['DOING'],
  DOING: ['WAITING_REVIEW'],
  WAITING_REVIEW: ['DONE', 'NEEDS_REVISION'],
  NEEDS_REVISION: ['DOING'],
  OVERDUE: ['DOING', 'WAITING_REVIEW', 'NEEDS_REVISION', 'DONE'],
};

const studentStatusTransitions: Partial<Record<ResearchTaskStatus, TaskColumn[]>> = {
  TODO: ['DOING'],
  DOING: ['WAITING_REVIEW'],
  NEEDS_REVISION: ['DOING'],
};

interface CanMoveTaskParams {
  role?: TaskBoardRole;
  currentUserId?: number | null;
  task: ResearchTask;
  fromStatus: ResearchTaskStatus;
  toStatus: TaskColumn;
}

export function canMoveTask({ role, currentUserId, task, fromStatus, toStatus }: CanMoveTaskParams) {
  if (fromStatus === toStatus) {
    return false;
  }
  if (role === 'LAB_MANAGER') {
    return managerStatusTransitions[fromStatus]?.includes(toStatus) ?? false;
  }
  if (role === 'GROUP_LEADER') {
    return studentStatusTransitions[fromStatus]?.includes(toStatus) ?? false;
  }
  if (role === 'STUDENT_MEMBER') {
    return currentUserId != null
      && task.assignedToStudentId === currentUserId
      && (studentStatusTransitions[fromStatus]?.includes(toStatus) ?? false);
  }
  return false;
}

export function canDragTask(task: ResearchTask, role?: TaskBoardRole, currentUserId?: number | null) {
  if (role === 'LAB_MANAGER') {
    return task.status !== 'DONE' && task.status !== 'CANCELLED';
  }
  if (role === 'GROUP_LEADER') {
    return Boolean(studentStatusTransitions[task.status]?.length);
  }
  if (role === 'STUDENT_MEMBER') {
    return currentUserId != null
      && task.assignedToStudentId === currentUserId
      && Boolean(studentStatusTransitions[task.status]?.length);
  }
  return false;
}

export function getTaskDragDisabledReason(
  task: ResearchTask,
  role?: TaskBoardRole,
  currentUserId?: number | null,
  locale: Locale = getStoredLocale(),
) {
  if (role === 'STUDENT_MEMBER') {
    if (currentUserId == null || task.assignedToStudentId !== currentUserId) {
      return translate('research.task.drag.onlyAssigned', locale);
    }
    if (!canDragTask(task, role, currentUserId)) {
      return translate('research.task.drag.statusLocked', locale);
    }
  }
  if ((role === 'LAB_MANAGER' || role === 'GROUP_LEADER') && !canDragTask(task, role, currentUserId)) {
    return translate('research.task.drag.finished', locale);
  }
  return undefined;
}

function updateTaskStatusLocally(task: ResearchTask, status: TaskColumn): ResearchTask {
  const progressPercent = status === 'DONE'
    ? 100
    : status === 'WAITING_REVIEW'
      ? 90
      : status === 'DOING'
      ? Math.max(10, task.progressPercent)
      : task.progressPercent;

  return {
    ...task,
    status,
    statusLabel: formatTaskStatus(status),
    column: getTaskColumn(status),
    progressPercent,
    isOverdue: isTaskOverdue({ deadline: task.deadline, status }),
  };
}

export function updateTaskStatusInCache(tasks: ResearchTask[], taskId: number, newStatus: TaskColumn) {
  return tasks.map((task) => (
    task.id === taskId ? updateTaskStatusLocally(task, newStatus) : task
  ));
}
