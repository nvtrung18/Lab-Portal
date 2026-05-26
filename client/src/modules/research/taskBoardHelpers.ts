import type { RawResearchTask, ResearchTask, ResearchTaskStatus, TaskColumn } from './types';

export const TASK_COLUMNS: TaskColumn[] = ['TODO', 'DOING', 'WAITING_REVIEW', 'DONE'];

const taskStatusLabels: Record<ResearchTaskStatus, string> = {
  TODO: 'Cần làm',
  DOING: 'Đang thực hiện',
  WAITING_REVIEW: 'Chờ duyệt',
  NEEDS_REVISION: 'Cần chỉnh sửa',
  DONE: 'Hoàn thành',
  OVERDUE: 'Quá hạn',
  CANCELLED: 'Đã hủy',
};

const taskColumnLabels: Record<TaskColumn, string> = {
  TODO: 'Cần làm',
  DOING: 'Đang thực hiện',
  WAITING_REVIEW: 'Chờ duyệt',
  DONE: 'Hoàn thành',
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

export function formatTaskStatus(status: ResearchTaskStatus) {
  return taskStatusLabels[status];
}

export function formatTaskColumn(column: TaskColumn) {
  return taskColumnLabels[column];
}

export function getTaskColumn(status: ResearchTaskStatus): TaskColumn {
  switch (status) {
    case 'TODO':
      return 'TODO';
    case 'WAITING_REVIEW':
      return 'WAITING_REVIEW';
    case 'DONE':
    case 'CANCELLED':
      return 'DONE';
    case 'DOING':
    case 'NEEDS_REVISION':
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
