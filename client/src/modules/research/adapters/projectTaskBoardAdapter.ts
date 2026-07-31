import type { ProjectTaskBoardResponse, TaskBacklogPageResponse, TaskResponse, TaskStatus } from '../types';

export type ProjectTaskColumnModel = {
  status: TaskStatus;
  label: string;
  badgeClassName: string;
  tasks: TaskResponse[];
};

export type ProjectTaskBacklogModel = {
  tasks: TaskResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

const STATUS_METADATA: Record<TaskStatus, Omit<ProjectTaskColumnModel, 'status' | 'tasks'>> = {
  BACKLOG: { label: 'Backlog', badgeClassName: 'bg-slate-100 text-slate-700 ring-slate-200' },
  TODO: { label: 'To do', badgeClassName: 'bg-sky-50 text-sky-700 ring-sky-200' },
  IN_PROGRESS: { label: 'In progress', badgeClassName: 'bg-amber-50 text-amber-800 ring-amber-200' },
  IN_REVIEW: { label: 'In review', badgeClassName: 'bg-violet-50 text-violet-700 ring-violet-200' },
  NEEDS_REVISION: { label: 'Needs revision', badgeClassName: 'bg-orange-50 text-orange-800 ring-orange-200' },
  DONE: { label: 'Done', badgeClassName: 'bg-emerald-50 text-emerald-700 ring-emerald-200' },
  BLOCKED: { label: 'Blocked', badgeClassName: 'bg-red-50 text-red-700 ring-red-200' },
  CANCELLED: { label: 'Cancelled', badgeClassName: 'bg-slate-100 text-slate-600 ring-slate-200' },
};

export function adaptProjectTaskBoard(response: ProjectTaskBoardResponse): ProjectTaskColumnModel[] {
  if (!Array.isArray(response.columns)) throw new Error('Invalid task board response.');

  return response.columns.map((column) => {
    const metadata = STATUS_METADATA[column.status];
    if (!metadata || !Array.isArray(column.tasks)) {
      throw new Error('The task board returned an unsupported status.');
    }
    column.tasks.forEach(assertSupportedTask);
    return { status: column.status, tasks: column.tasks, ...metadata };
  });
}

export function adaptProjectTaskBacklog(response: TaskBacklogPageResponse): ProjectTaskBacklogModel {
  if (!Array.isArray(response.content) || !isValidPageMetadata(response)) {
    throw new Error('Invalid task backlog response.');
  }
  response.content.forEach(assertSupportedTask);
  return {
    tasks: response.content,
    page: response.page,
    size: response.size,
    totalElements: response.totalElements,
    totalPages: response.totalPages,
  };
}

function assertSupportedTask(task: TaskResponse) {
  if (!STATUS_METADATA[task.status]) {
    throw new Error('The task board returned an unsupported status.');
  }
}

function isValidPageMetadata(response: TaskBacklogPageResponse) {
  return [response.page, response.size, response.totalElements, response.totalPages]
    .every((value) => Number.isSafeInteger(value) && value >= 0) && response.size >= 1;
}
