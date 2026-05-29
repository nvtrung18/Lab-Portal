import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/api';
import { Button, EmptyState, ErrorState, LoadingState, toast } from '../../../shared/components';
import { useMyResearchTasks, useReportsByTask } from '../hooks';
import { updateTaskStatus } from '../api';
import type { ResearchReportStatus, ResearchTask, ResearchTaskStatus, TaskColumn } from '../types';
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
      queryClient.invalidateQueries({ queryKey: queryKeys.research.myGroupMilestones(groupId) });
      if (projectId) {
        queryClient.invalidateQueries({ queryKey: queryKeys.research.projectStats(projectId) });
      }
      toast.success('Đã bắt đầu thực hiện nhiệm vụ.');
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
  const latestReportStatus = latestReport?.status ?? task.latestReportStatus ?? null;
  const hasLatestReport = Boolean(latestReport ?? task.latestReportStatus);
  const assignedToCurrentUser = currentUserId != null && task.assignedToStudentId === currentUserId;
  const action = getMyTaskAction(task.status, latestReportStatus, hasLatestReport, assignedToCurrentUser);
  const statusBadge = getMyTaskStatusBadge(task.status, latestReportStatus);
  const uploadTitle = action === 'REPLACE'
    ? 'Cập nhật báo cáo tiến độ'
    : action === 'RESUBMIT'
    ? 'Nộp lại báo cáo tiến độ'
    : 'Nộp báo cáo tiến độ';

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
            Hạn hoàn thành: {formatDate(task.deadline)} · Trạng thái: {statusBadge.taskLabel}
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <span className="text-xs font-semibold text-slate-700">Trạng thái báo cáo mới nhất:</span>
            <ReportStatusBadge status={latestReportStatus} />
          </div>
          {statusBadge.reportLabel ? (
            <p className="mt-2 text-xs font-medium text-slate-600">{statusBadge.reportLabel}</p>
          ) : null}
          {latestReportStatus === 'SUBMITTED' && (
            <p className="mt-2 text-xs italic text-blue-600 font-medium">
              Báo cáo đang chờ kiểm tra. Bạn có thể cập nhật phiên bản này trước khi được xử lý.
            </p>
          )}
          {latestReportStatus === 'LEADER_REVIEWED' && (
            <p className="mt-2 text-xs italic text-amber-600 font-medium">
              Báo cáo đã được trưởng nhóm chấp nhận và đang chờ quản lý duyệt.
            </p>
          )}
        </div>

        <div className="flex flex-wrap gap-2 sm:flex-col sm:items-end shrink-0">
          {action === 'START' ? (
            <Button
              onClick={() => updateStatus.mutate({ taskId: task.id, status: 'DOING' })}
              size="sm"
              loading={updateStatus.isPending}
            >
              Bắt đầu thực hiện
            </Button>
          ) : null}
          {action === 'SUBMIT' || action === 'RESUBMIT' || action === 'REPLACE' ? (
            <Button onClick={() => setIsUploadOpen(true)} size="sm" variant={action === 'REPLACE' ? 'outline' : 'primary'}>
              {action === 'REPLACE' ? 'Cập nhật báo cáo' : action === 'RESUBMIT' ? 'Nộp lại báo cáo' : 'Nộp báo cáo'}
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
        title={uploadTitle}
        mode={action === 'REPLACE' ? 'replace' : action === 'RESUBMIT' ? 'resubmit' : 'create'}
        reportId={action === 'REPLACE' ? latestReport?.id : null}
        initialValues={action === 'REPLACE' ? latestReport : null}
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

type MyTaskAction = 'START' | 'SUBMIT' | 'RESUBMIT' | 'REPLACE' | 'NONE';

const RESUBMIT_REPORT_STATUSES = new Set<ResearchReportStatus>([
  'NEEDS_REVISION',
  'LEADER_REJECTED',
  'MANAGER_REJECTED',
]);

const BLOCKED_REPORT_STATUSES = new Set<ResearchReportStatus>([
  'LEADER_REVIEWED',
  'APPROVED',
]);

const TASK_STATUS_LABELS: Record<ResearchTaskStatus, string> = {
  TODO: 'Cần làm',
  DOING: 'Đang thực hiện',
  WAITING_REVIEW: 'Chờ trưởng nhóm kiểm tra',
  NEEDS_REVISION: 'Cần chỉnh sửa',
  DONE: 'Hoàn thành',
  OVERDUE: 'Đang thực hiện',
  CANCELLED: 'Đã hủy',
};

const REPORT_STATUS_HELPERS: Record<ResearchReportStatus, string> = {
  SUBMITTED: 'Chờ trưởng nhóm kiểm tra',
  LEADER_REVIEWED: 'Chờ quản lý duyệt',
  NEEDS_REVISION: 'Cần nộp lại báo cáo',
  LEADER_REJECTED: 'Trưởng nhóm đã từ chối',
  APPROVED: 'Đã hoàn thành',
  MANAGER_REJECTED: 'Quản lý đã từ chối',
};

function getMyTaskAction(
  taskStatus: ResearchTaskStatus,
  latestReportStatus: ResearchReportStatus | null,
  hasLatestReport: boolean,
  assignedToCurrentUser: boolean,
): MyTaskAction {
  if (!assignedToCurrentUser) {
    return 'NONE';
  }
  if (taskStatus === 'TODO') {
    return 'START';
  }
  if (taskStatus === 'DONE' || latestReportStatus === 'APPROVED') {
    return 'NONE';
  }
  if (latestReportStatus === 'SUBMITTED') {
    return 'REPLACE';
  }
  if (latestReportStatus && RESUBMIT_REPORT_STATUSES.has(latestReportStatus)) {
    return 'RESUBMIT';
  }
  if (latestReportStatus && BLOCKED_REPORT_STATUSES.has(latestReportStatus)) {
    return 'NONE';
  }
  if ((taskStatus === 'DOING' || taskStatus === 'NEEDS_REVISION') && !hasLatestReport) {
    return 'SUBMIT';
  }
  return 'NONE';
}

function getMyTaskStatusBadge(
  taskStatus: ResearchTaskStatus,
  latestReportStatus: ResearchReportStatus | null,
) {
  if (taskStatus === 'DONE' || latestReportStatus === 'APPROVED') {
    return { taskLabel: 'Hoàn thành', reportLabel: REPORT_STATUS_HELPERS.APPROVED };
  }
  if (latestReportStatus) {
    return {
      taskLabel: TASK_STATUS_LABELS[taskStatus] ?? 'Cần làm',
      reportLabel: REPORT_STATUS_HELPERS[latestReportStatus],
    };
  }
  return {
    taskLabel: TASK_STATUS_LABELS[taskStatus] ?? 'Cần làm',
    reportLabel: null,
  };
}
