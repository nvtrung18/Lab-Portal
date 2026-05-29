import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { Button, EmptyState, ErrorState, LoadingState, toast } from '../../../shared/components';
import { useMyResearchTasks, useReportsByTask } from '../hooks';
import { updateTaskStatus } from '../api';
import type { ResearchTask, TaskColumn } from '../types';
import { formatDate, getApiErrorMessage } from '../utils';
import { ReportStatusBadge } from './ReportStatusBadge';
import { ReportUploadModal } from './ReportUploadModal';
import { ReportHistoryModal } from './ReportHistoryModal';

interface MyResearchTasksProps {
  groupId: number;
  projectId: number;
  currentUserId?: number | null;
  emptyMessage?: string;
}

export function MyResearchTasks({
  groupId,
  projectId,
  currentUserId,
  emptyMessage = 'Bạn chưa được giao nhiệm vụ nào trong nhóm này.',
}: MyResearchTasksProps) {
  const { data: tasks = [], error, isLoading, isError, refetch } = useMyResearchTasks(groupId);
  const errorMessage = getApiErrorMessage(error, {
    fallback: 'Không thể tải nhiệm vụ của bạn.',
    forbidden: 'Bạn không có quyền xem nhiệm vụ trong nhóm này.',
  });

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
          {errorMessage}
        </ErrorState>
      ) : !tasks.length ? (
        <EmptyState className="mt-5">{emptyMessage}</EmptyState>
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
  const [isHistoryOpen, setIsHistoryOpen] = useState(false);
  const [defaultOpenLatestReview, setDefaultOpenLatestReview] = useState(false);
  const queryClient = useQueryClient();

  const updateStatus = useMutation({
    mutationFn: ({ taskId, status }: { taskId: number; status: TaskColumn }) =>
      updateTaskStatus(taskId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.research.myTasks(groupId) });
      if (projectId) {
        queryClient.invalidateQueries({ queryKey: queryKeys.research.projectStats(projectId) });
      }
      toast.success('Đã cập nhật trạng thái nhiệm vụ.');
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, { fallback: 'Không thể cập nhật trạng thái nhiệm vụ.' }));
    },
  });

  const { data: reports = [] } = useReportsByTask(task.id);
  const latestReport = [...reports].sort((first, second) => (
    second.version - first.version ||
    (second.createdAt ?? '').localeCompare(first.createdAt ?? '')
  ))[0];
  const canSubmit = !latestReport || latestReport.status === 'NEEDS_REVISION';
  const submitLabel = latestReport?.status === 'NEEDS_REVISION' ? 'Nộp lại báo cáo' : 'Nộp báo cáo';

  return (
    <article className="rounded-md border border-slate-200 p-4">
      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
        <div className="min-w-0 flex-1">
          {task.milestoneTitle ? (
            <p className="text-xs text-slate-500 mb-1">
              Mốc: <span className="font-semibold text-slate-700">{task.milestoneTitle}</span>
            </p>
          ) : null}
          <h4 className="font-semibold text-slate-950">{task.title}</h4>
          <p className="mt-1 text-sm text-slate-600">{task.description || 'Chưa cập nhật mô tả nhiệm vụ.'}</p>
          <p className="mt-2 text-xs text-slate-500">
            Hạn hoàn thành: {formatDate(task.deadline)} · Trạng thái: {task.statusLabel}
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <span className="text-xs font-semibold text-slate-700">Trạng thái báo cáo mới nhất:</span>
            <ReportStatusBadge status={task.latestReportStatus ?? latestReport?.status} />
          </div>
        </div>

        <div className="flex flex-wrap gap-2 sm:flex-col sm:items-end shrink-0">
          {/* Status transitions */}
          {task.status === 'TODO' && (
            <Button
              onClick={() => updateStatus.mutate({ taskId: task.id, status: 'DOING' })}
              size="sm"
              loading={updateStatus.isPending}
            >
              Bắt đầu thực hiện
            </Button>
          )}
          {task.status === 'DOING' && (
            <Button
              onClick={() => updateStatus.mutate({ taskId: task.id, status: 'WAITING_REVIEW' })}
              size="sm"
              loading={updateStatus.isPending}
              variant="secondary"
            >
              Yêu cầu đánh giá
            </Button>
          )}
          {task.status === 'NEEDS_REVISION' && (
            <Button
              onClick={() => updateStatus.mutate({ taskId: task.id, status: 'DOING' })}
              size="sm"
              loading={updateStatus.isPending}
            >
              Tiếp tục thực hiện
            </Button>
          )}

          {/* Submit/Resubmit report */}
          {canSubmit ? (
            <Button onClick={() => setIsUploadOpen(true)} size="sm" variant="outline">
              {submitLabel}
            </Button>
          ) : null}
        </div>
      </div>

      <div className="mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-3">
        {reports.length > 0 ? (
          <>
            <Button
              onClick={() => {
                setIsHistoryOpen(true);
                setDefaultOpenLatestReview(false);
              }}
              size="sm"
              variant="outline"
            >
              Xem lịch sử báo cáo
            </Button>
            <Button
              onClick={() => {
                setIsHistoryOpen(true);
                setDefaultOpenLatestReview(true);
              }}
              size="sm"
              variant="outline"
            >
              Xem góp ý
            </Button>
          </>
        ) : null}
      </div>

      <ReportUploadModal
        groupId={groupId}
        isOpen={isUploadOpen}
        milestoneId={task.milestoneId}
        projectId={projectId}
        tasks={[task]}
        title={submitLabel === 'Nộp lại báo cáo' ? 'Nộp lại báo cáo tiến độ' : 'Nộp báo cáo tiến độ'}
        onClose={() => setIsUploadOpen(false)}
      />

      {reports.length > 0 && (
        <ReportHistoryModal
          isOpen={isHistoryOpen}
          onClose={() => setIsHistoryOpen(false)}
          taskId={task.id}
          taskTitle={task.title}
          currentUserId={currentUserId}
          role="STUDENT_MEMBER"
          groupId={groupId}
          defaultOpenLatestReview={defaultOpenLatestReview}
        />
      )}
    </article>
  );
}
