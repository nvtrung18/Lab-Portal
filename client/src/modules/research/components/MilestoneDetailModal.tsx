import { ErrorState, LoadingState, Modal } from '../../../shared/components';
import { useMilestone } from '../hooks';
import type { TaskBoardRole } from '../taskBoardHelpers';
import { formatDate, formatMilestoneStatus, getStatusClass, isMilestoneOverdue } from '../utils';
import { ManagerReportsList } from './ManagerReportsList';
import { TaskBoard } from './TaskBoard';

interface MilestoneDetailModalProps {
  milestoneId: number | null;
  showTaskBoard?: boolean;
  taskBoardReadonly?: boolean;
  taskBoardRole?: TaskBoardRole;
  taskBoardCurrentUserId?: number | null;
  groupId?: number | null;
  onClose: () => void;
}

export function MilestoneDetailModal({
  milestoneId,
  showTaskBoard = false,
  taskBoardReadonly = true,
  taskBoardRole,
  taskBoardCurrentUserId,
  groupId,
  onClose,
}: MilestoneDetailModalProps) {
  const { data: milestone, isError, isLoading, refetch } = useMilestone(milestoneId);

  if (!milestoneId) {
    return null;
  }

  const displayedStatus = milestone && isMilestoneOverdue(milestone.deadline, milestone.status)
    ? 'OVERDUE'
    : milestone?.status;

  return (
    <Modal onClose={onClose} size="full" title="Chi tiết mốc nghiên cứu">
        {isLoading ? (
          <LoadingState>Đang tải chi tiết mốc nghiên cứu...</LoadingState>
        ) : isError || !milestone ? (
          <ErrorState onRetry={() => refetch()}>
            Không thể tải chi tiết mốc nghiên cứu.
          </ErrorState>
        ) : (
          <div className="space-y-6">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <h4 className="text-base font-semibold text-slate-950">{milestone.title}</h4>
                <p className="mt-1 text-sm text-slate-600">
                  Đề tài nghiên cứu: {milestone.projectTitle ?? `#${milestone.projectId}`}
                </p>
              </div>
              <span className={`w-fit shrink-0 rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(displayedStatus)}`}>
                {formatMilestoneStatus(displayedStatus)}
              </span>
            </div>

            <dl className="grid gap-4 text-sm sm:grid-cols-2">
              <Detail label="Mô tả công việc" value={milestone.description ?? 'Chưa cập nhật'} />
              <Detail label="Người phụ trách" value={milestone.assignedToStudentName ?? 'Chưa phân công'} />
              <Detail label="Hạn hoàn thành" value={formatDate(milestone.deadline)} />
              <Detail label="Trạng thái" value={formatMilestoneStatus(displayedStatus)} />
              <Detail label="Ngày tạo" value={formatDate(milestone.createdAt)} />
              <Detail label="Ngày cập nhật" value={formatDate(milestone.updatedAt)} />
            </dl>

            <div>
              <div className="flex items-center justify-between text-sm">
                <span className="font-semibold text-slate-700">Tỷ lệ hoàn thành</span>
                <span className="font-semibold text-slate-950">{milestone.progressPercent}%</span>
              </div>
              <div
                aria-label={`Tỷ lệ hoàn thành ${milestone.progressPercent}%`}
                aria-valuemax={100}
                aria-valuemin={0}
                aria-valuenow={milestone.progressPercent}
                className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100"
                role="progressbar"
              >
                <div
                  className="h-full rounded-full bg-emerald-600"
                  style={{ width: `${Math.min(100, Math.max(0, milestone.progressPercent))}%` }}
                />
              </div>
            </div>

            <dl className="grid gap-4 text-sm sm:grid-cols-2">
              <div>
                <dt className="font-semibold text-slate-700">File/link minh chứng</dt>
                <dd className="mt-1 text-slate-600">
                  {milestone.evidenceUrl ? (
                    <a
                      className="font-medium text-blue-700 underline hover:text-blue-800"
                      href={milestone.evidenceUrl}
                      rel="noreferrer"
                      target="_blank"
                    >
                      Mở minh chứng
                    </a>
                  ) : (
                    'Chưa cập nhật'
                  )}
                </dd>
              </div>
              <Detail label="Nhận xét của quản lý PTN" value={milestone.managerComment ?? 'Chưa cập nhật'} />
            </dl>

            {showTaskBoard ? (
              <>
                <TaskBoard
                  groupId={groupId}
                  milestoneId={milestone.id}
                  projectId={milestone.projectId}
                  readonly={taskBoardReadonly}
                  role={taskBoardRole}
                  currentUserId={taskBoardCurrentUserId}
                />
              </>
            ) : null}

            {taskBoardRole === 'LAB_MANAGER' ? (
              <ManagerReportsList milestoneId={milestone.id} />
            ) : null}
          </div>
        )}
    </Modal>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="font-semibold text-slate-700">{label}</dt>
      <dd className="mt-1 whitespace-pre-wrap text-slate-600">{value}</dd>
    </div>
  );
}
