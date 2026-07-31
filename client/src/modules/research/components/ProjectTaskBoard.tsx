import { useEffect, useMemo, useState } from 'react';
import axios from 'axios';

import { EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import {
  adaptProjectTaskBacklog,
  adaptProjectTaskBoard,
  type ProjectTaskBacklogModel,
} from '../adapters/projectTaskBoardAdapter';
import { useProjectTaskBacklog, useProjectTaskBoard } from '../hooks';
import type { TaskResponse } from '../types';
import { formatDate } from '../utils';

const BACKLOG_PAGE_SIZE = 20;

export function ProjectTaskBoard({ projectId }: { projectId: number }) {
  const boardQuery = useProjectTaskBoard(projectId);
  const [page, setPage] = useState(0);
  useEffect(() => {
    setPage(0);
  }, [projectId]);
  const backlogQuery = useProjectTaskBacklog(projectId, page, BACKLOG_PAGE_SIZE);
  const adaptedBoard = useMemo(() => {
    if (!boardQuery.data) return { columns: [], error: undefined };
    try {
      return { columns: adaptProjectTaskBoard(boardQuery.data), error: undefined };
    } catch (error) {
      return { columns: [], error };
    }
  }, [boardQuery.data]);
  const adaptedBacklog = useMemo(() => {
    if (!backlogQuery.data) return { data: undefined, error: undefined };
    try {
      return { data: adaptProjectTaskBacklog(backlogQuery.data), error: undefined };
    } catch (error) {
      return { data: undefined, error };
    }
  }, [backlogQuery.data]);

  return (
    <section className="space-y-6" aria-labelledby="project-task-board-title">
      <div>
        <h2 id="project-task-board-title" className="text-xl font-semibold text-slate-950">Task board</h2>
        <p className="mt-1 text-sm text-slate-600">Tasks are shown only for the project scope returned by the server. This board is read-only.</p>
      </div>
      <BoardPanel columns={adaptedBoard.columns} error={adaptedBoard.error ?? boardQuery.error} isFetching={boardQuery.isFetching} isLoading={boardQuery.isLoading} onRetry={() => boardQuery.refetch()} />
      <BacklogPanel data={adaptedBacklog.data} error={adaptedBacklog.error ?? backlogQuery.error} isFetching={backlogQuery.isFetching} isLoading={backlogQuery.isLoading} onNext={() => setPage((current) => current + 1)} onPrevious={() => setPage((current) => Math.max(0, current - 1))} onRetry={() => backlogQuery.refetch()} />
    </section>
  );
}

function BoardPanel({ columns, error, isFetching, isLoading, onRetry }: { columns: ReturnType<typeof adaptProjectTaskBoard>; error: unknown; isFetching: boolean; isLoading: boolean; onRetry: () => void }) {
  if (isLoading) return <BoardSkeleton />;
  if (error) return <QueryError error={error} onRetry={onRetry} />;
  if (!columns.length) return <EmptyState>No task columns are available for this scope.</EmptyState>;
  return <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="board-columns-title">
    <div className="flex items-center justify-between gap-3"><h3 id="board-columns-title" className="text-base font-semibold text-slate-950">Tasks by status</h3>{isFetching ? <span className="text-xs text-slate-500" role="status">Refreshing...</span> : null}</div>
    <div className="mt-4 grid auto-cols-[minmax(17rem,1fr)] grid-flow-col gap-4 overflow-x-auto pb-2 lg:grid-flow-row lg:grid-cols-2 xl:grid-cols-3">
      {columns.map((column) => <section key={column.status} className="min-w-0 rounded-md border border-slate-200 bg-slate-50 p-4" aria-labelledby={`task-column-${column.status}`}>
        <div className="flex items-center justify-between gap-3"><h4 id={`task-column-${column.status}`} className="font-semibold text-slate-900">{column.label}</h4><span className={`rounded-full px-2 py-1 text-xs font-semibold ring-1 ${column.badgeClassName}`}>{column.tasks.length}</span></div>
        {!column.tasks.length ? <p className="mt-4 text-sm text-slate-600">No tasks in this column.</p> : null}
        <div className="mt-4 space-y-3">{column.tasks.map((task) => <ProjectTaskCard key={task.id} task={task} />)}</div>
      </section>)}
    </div>
  </section>;
}

function BacklogPanel({ data, error, isFetching, isLoading, onNext, onPrevious, onRetry }: { data: ProjectTaskBacklogModel | undefined; error: unknown; isFetching: boolean; isLoading: boolean; onNext: () => void; onPrevious: () => void; onRetry: () => void }) {
  if (isLoading) return <BoardSkeleton />;
  if (error) return <QueryError error={error} onRetry={onRetry} />;
  if (!data) return null;
  const isFirstPage = data.page <= 0;
  const isLastPage = data.totalPages === 0 || data.page >= data.totalPages - 1;
  return <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="task-backlog-title">
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><div><h3 id="task-backlog-title" className="text-base font-semibold text-slate-950">Backlog</h3><p className="mt-1 text-sm text-slate-600">{data.totalElements} tasks in the current scope.</p></div>{isFetching ? <span className="text-xs text-slate-500" role="status">Refreshing...</span> : null}</div>
    {!data.tasks.length ? <EmptyState className="mt-4">No backlog tasks are available on this page.</EmptyState> : <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">{data.tasks.map((task) => <ProjectTaskCard key={task.id} task={task} />)}</div>}
    <nav className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-4" aria-label="Backlog pagination"><p className="text-sm text-slate-600">Page {data.totalPages ? data.page + 1 : 0} of {data.totalPages}</p><div className="flex gap-2"><button type="button" className="rounded-md border border-slate-300 px-3 py-2 text-sm font-semibold text-slate-700 disabled:cursor-not-allowed disabled:opacity-50" disabled={isFirstPage || isFetching} onClick={onPrevious}>Previous</button><button type="button" className="rounded-md border border-slate-300 px-3 py-2 text-sm font-semibold text-slate-700 disabled:cursor-not-allowed disabled:opacity-50" disabled={isLastPage || isFetching} onClick={onNext}>Next</button></div></nav>
  </section>;
}

function ProjectTaskCard({ task }: { task: TaskResponse }) {
  const progress = Math.min(100, Math.max(0, task.progressPercent));
  return <article className="rounded-md border border-slate-200 bg-white p-3 shadow-sm"><h5 className="break-words text-sm font-semibold text-slate-950">{task.title}</h5><p className="mt-2 line-clamp-2 text-xs leading-5 text-slate-600">{task.description || 'No task description has been provided.'}</p><dl className="mt-3 grid gap-2 text-xs sm:grid-cols-2"><div><dt className="font-semibold text-slate-700">Assignee</dt><dd className="mt-1 break-words text-slate-600">{task.assignedToStudentName ?? task.assignedToStudentEmail ?? 'Unassigned'}</dd></div><div><dt className="font-semibold text-slate-700">Due date</dt><dd className="mt-1 text-slate-600">{formatDate(task.dueDate ?? task.deadline)}</dd></div></dl>{task.blockedReason ? <p className="mt-3 rounded bg-red-50 p-2 text-xs text-red-800"><span className="font-semibold">Blocked:</span> {task.blockedReason}</p> : null}<div className="mt-3"><div className="flex justify-between text-xs text-slate-700"><span className="font-semibold">Progress</span><span>{progress}%</span></div><div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-slate-100" role="progressbar" aria-label={`Progress for ${task.title}`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress}><div className="h-full rounded-full bg-emerald-600" style={{ width: `${progress}%` }} /></div></div></article>;
}

function QueryError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  const status = axios.isAxiosError(error) ? error.response?.status : undefined;
  const isAccessError = status === 403 || status === 404;
  const errorMessage = error instanceof Error ? error.message : undefined;
  return <ErrorState onRetry={isAccessError ? undefined : onRetry}>{isAccessError ? 'Task data is unavailable for this project.' : errorMessage ?? 'Unable to load task data. Please try again.'}</ErrorState>;
}

function BoardSkeleton() {
  return <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm" aria-busy="true" aria-label="Loading task data"><LoadingState /><div className="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-3">{Array.from({ length: 3 }).map((_, index) => <div key={index} className="h-48 animate-pulse rounded-md bg-slate-100" />)}</div></section>;
}
