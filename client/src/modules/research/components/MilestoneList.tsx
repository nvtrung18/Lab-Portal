import { useEffect, useMemo, useState } from 'react';

import { Button, EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { useCreateMilestone, useMilestonesByProject, useResearchEligibleStudents, useUpdateMilestone } from '../hooks';
import type { ResearchMilestone } from '../types';
import type { TaskBoardRole } from '../taskBoardHelpers';
import { formatDate, formatMilestoneStatus, getStatusClass, isMilestoneOverdue } from '../utils';
import { CreateMilestoneModal } from './CreateMilestoneModal';
import { EditMilestoneModal } from './EditMilestoneModal';
import { MilestoneDetailModal } from './MilestoneDetailModal';

interface MilestoneListProps {
  projectId: number;
  labId?: number | null;
  canCreate: boolean;
  showTaskBoard?: boolean;
  taskBoardRole?: TaskBoardRole;
  taskBoardCurrentUserId?: number | null;
  groupId?: number | null;
  emptyMessage?: string;
  title?: string;
  description?: string;
}

export function MilestoneList({
  projectId,
  labId,
  canCreate,
  showTaskBoard = canCreate,
  taskBoardRole = canCreate ? 'LAB_MANAGER' : undefined,
  taskBoardCurrentUserId,
  groupId,
  emptyMessage = 'Chưa có mốc nghiên cứu nào.',
  title = 'Mốc nghiên cứu',
  description = 'Các giai đoạn chính cần hoàn thành trong đề tài.',
}: MilestoneListProps) {
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [detailMilestoneId, setDetailMilestoneId] = useState<number | null>(null);
  const [editingMilestone, setEditingMilestone] = useState<ResearchMilestone | null>(null);
  const { data: milestones = [], isError, isLoading, refetch } = useMilestonesByProject(projectId);
  const { data: students = [], isLoading: isLoadingStudents } = useResearchEligibleStudents(
    canCreate ? labId : null,
  );
  const createMilestone = useCreateMilestone(projectId);
  const updateMilestone = useUpdateMilestone(projectId);

  useEffect(() => {
    setIsCreateOpen(false);
    setDetailMilestoneId(null);
    setEditingMilestone(null);
  }, [projectId]);

  const sortedMilestones = useMemo(
    () =>
      [...milestones].sort((left, right) => {
        if (!left.deadline) return right.deadline ? 1 : 0;
        if (!right.deadline) return -1;
        return left.deadline.localeCompare(right.deadline);
      }),
    [milestones],
  );

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">{title}</h3>
          <p className="mt-1 text-sm text-slate-600">{description}</p>
        </div>
        {canCreate ? (
          <Button onClick={() => setIsCreateOpen(true)}>
            Tạo mốc nghiên cứu
          </Button>
        ) : null}
      </div>

      {isLoading ? (
        <LoadingState className="mt-5">Đang tải danh sách mốc nghiên cứu...</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          Không thể tải danh sách mốc nghiên cứu.
        </ErrorState>
      ) : !milestones.length ? (
        <EmptyState className="mt-5">
          {emptyMessage}
        </EmptyState>
      ) : (
        <ol className="mt-5 space-y-3">
          {sortedMilestones.map((milestone) => {
            const overdue = isMilestoneOverdue(milestone.deadline, milestone.status);
            const displayedStatus = overdue ? 'OVERDUE' : milestone.status;

            return (
              <li key={milestone.id} className="rounded-md border border-slate-200 p-4">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0 flex-1">
                    <h4 className="text-base font-semibold text-slate-950">{milestone.title}</h4>
                    <p className="mt-2 text-sm text-slate-600">
                      {milestone.description || 'Chưa cập nhật mô tả công việc.'}
                    </p>
                    <dl className="mt-3 grid gap-3 text-sm text-slate-600 sm:grid-cols-2">
                      <div>
                        <dt className="font-semibold text-slate-700">Hạn hoàn thành</dt>
                        <dd className="mt-1">{formatDate(milestone.deadline)}</dd>
                      </div>
                      <div>
                        <dt className="font-semibold text-slate-700">Ngày tạo</dt>
                        <dd className="mt-1">{formatDate(milestone.createdAt)}</dd>
                      </div>
                    </dl>
                    <div className="mt-3 max-w-sm">
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
                    <div className="mt-4 flex flex-wrap gap-2">
                      <Button onClick={() => setDetailMilestoneId(milestone.id)} size="sm" variant="outline">
                        Xem chi tiết
                      </Button>
                      {canCreate ? (
                        <Button onClick={() => setEditingMilestone(milestone)} size="sm" variant="outline">
                          Sửa mốc
                        </Button>
                      ) : null}
                    </div>
                  </div>
                  <span className={`w-fit shrink-0 rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(displayedStatus)}`}>
                    {formatMilestoneStatus(displayedStatus)}
                  </span>
                </div>
              </li>
            );
          })}
        </ol>
      )}

      {canCreate ? (
        <>
          <CreateMilestoneModal
            isOpen={isCreateOpen}
            projectId={projectId}
            students={students}
            isLoadingStudents={isLoadingStudents}
            isSubmitting={createMilestone.isPending}
            onClose={() => setIsCreateOpen(false)}
            onSubmit={(payload) => createMilestone.mutate(payload, { onSuccess: () => setIsCreateOpen(false) })}
          />
          <EditMilestoneModal
            milestone={editingMilestone}
            students={students}
            isLoadingStudents={isLoadingStudents}
            isSubmitting={updateMilestone.isPending}
            onClose={() => setEditingMilestone(null)}
            onSubmit={(payload) => {
              if (!editingMilestone) {
                return;
              }
              updateMilestone.mutate(
                { milestoneId: editingMilestone.id, payload },
                { onSuccess: () => setEditingMilestone(null) },
              );
            }}
          />
        </>
      ) : null}

      <MilestoneDetailModal
        milestoneId={detailMilestoneId}
        showTaskBoard={showTaskBoard}
        taskBoardReadonly={!taskBoardRole}
        taskBoardRole={taskBoardRole}
        taskBoardCurrentUserId={taskBoardCurrentUserId}
        groupId={groupId}
        onClose={() => setDetailMilestoneId(null)}
      />
    </section>
  );
}
