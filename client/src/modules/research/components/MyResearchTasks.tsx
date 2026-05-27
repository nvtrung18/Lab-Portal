import { useState } from 'react';

import { Button, EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { useMyResearchTasks, useReportsByTask } from '../hooks';
import type { ResearchTask } from '../types';
import { formatDate } from '../utils';
import { ReportList } from './ReportList';
import { ReportStatusBadge } from './ReportStatusBadge';
import { ReportUploadModal } from './ReportUploadModal';

interface MyResearchTasksProps {
  groupId: number;
  projectId: number;
  currentUserId?: number | null;
}

export function MyResearchTasks({ groupId, projectId, currentUserId }: MyResearchTasksProps) {
  const { data: tasks = [], isLoading, isError, refetch } = useMyResearchTasks(groupId);

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <h3 className="text-lg font-semibold text-slate-950">Nhiệm vụ của tôi</h3>
      <p className="mt-1 text-sm text-slate-600">
        Nộp báo cáo cho từng nhiệm vụ được phân công và theo dõi lịch sử các phiên bản đã nộp.
      </p>

      {isLoading ? (
        <LoadingState className="mt-5">Đang tải nhiệm vụ của bạn...</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          Không thể tải nhiệm vụ của bạn.
        </ErrorState>
      ) : !tasks.length ? (
        <EmptyState className="mt-5">Bạn chưa có nhiệm vụ nào được giao trong nhóm này.</EmptyState>
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
  const [isUploadOpen, setIsUploadOpen] = useState(false);
  const { data: reports = [] } = useReportsByTask(task.id);
  const latestReport = [...reports].sort((first, second) => (
    second.version - first.version
    || (second.createdAt ?? '').localeCompare(first.createdAt ?? '')
  ))[0];
  const canSubmit = !latestReport || latestReport.status === 'NEEDS_REVISION';
  const submitLabel = latestReport?.status === 'NEEDS_REVISION' ? 'Nộp lại báo cáo' : 'Nộp báo cáo';

  return (
    <article className="rounded-md border border-slate-200 p-4">
      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
        <div>
          <h4 className="font-semibold text-slate-950">{task.title}</h4>
          <p className="mt-1 text-sm text-slate-600">{task.description || 'Chưa cập nhật mô tả nhiệm vụ.'}</p>
          <p className="mt-2 text-xs text-slate-500">
            Hạn hoàn thành: {formatDate(task.deadline)} · Trạng thái: {task.statusLabel}
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <span className="text-xs font-semibold text-slate-700">Trạng thái báo cáo:</span>
            <ReportStatusBadge status={latestReport?.status} />
          </div>
        </div>
        {canSubmit ? (
          <Button onClick={() => setIsUploadOpen(true)} size="sm">
            {submitLabel}
          </Button>
        ) : null}
      </div>

      <ReportList currentUserId={currentUserId} taskId={task.id} />

      <ReportUploadModal
        groupId={groupId}
        isOpen={isUploadOpen}
        milestoneId={task.milestoneId}
        projectId={projectId}
        tasks={[task]}
        title={submitLabel === 'Nộp lại báo cáo' ? 'Nộp lại báo cáo tiến độ' : 'Nộp báo cáo tiến độ'}
        onClose={() => setIsUploadOpen(false)}
      />
    </article>
  );
}
