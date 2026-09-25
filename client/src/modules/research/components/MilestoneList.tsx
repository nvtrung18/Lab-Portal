import { useEffect, useMemo, useState } from 'react';

import { Button, EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { useCreateMilestone, useMilestonesByProject, useMilestonesByGroup, useMyMilestonesByGroup, useResearchEligibleStudents, useUpdateMilestone, useResearchGroupMembers } from '../hooks';
import type { ResearchMilestone } from '../types';
import type { TaskBoardRole } from '../taskBoardHelpers';
import { formatDate, formatMilestoneStatus, getApiErrorMessage, getStatusClass, isMilestoneOverdue } from '../utils';
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
  groupRole?: 'LEADER' | 'MEMBER' | null;
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
  groupRole,
  emptyMessage,
  title,
  description,
}: MilestoneListProps) {
  const { locale, t } = useI18n();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [detailMilestoneId, setDetailMilestoneId] = useState<number | null>(null);
  const [editingMilestone, setEditingMilestone] = useState<ResearchMilestone | null>(null);
  const isMember = groupRole === 'MEMBER';
  const { data: projectMilestones = [], error: projectError, isError: isProjectError, isLoading: isProjectLoading, refetch: refetchProject } = useMilestonesByProject(!groupId ? projectId : null);
  const { data: allGroupMilestones = [], error: allGroupError, isError: isAllGroupError, isLoading: isAllGroupLoading, refetch: refetchAllGroup } = useMilestonesByGroup(!isMember ? groupId : null);
  const { data: myGroupMilestones = [], error: myGroupError, isError: isMyGroupError, isLoading: isMyGroupLoading, refetch: refetchMyGroup } = useMyMilestonesByGroup(isMember ? groupId : null);

  const milestones = groupId ? (isMember ? myGroupMilestones : allGroupMilestones) : projectMilestones;
  const isLoading = groupId ? (isMember ? isMyGroupLoading : isAllGroupLoading) : isProjectLoading;
  const isError = groupId ? (isMember ? isMyGroupError : isAllGroupError) : isProjectError;
  const error = groupId ? (isMember ? myGroupError : allGroupError) : projectError;
  const refetch = groupId ? (isMember ? refetchMyGroup : refetchAllGroup) : refetchProject;
  const errorMessage = getApiErrorMessage(error, {
    fallback: t('research.milestone.loadFailed'),
    forbidden: t('research.milestone.forbidden'),
  });

  const resolvedTitle = isMember ? t('research.milestone.myTitle') : title ?? t('research.milestone.title');
  const resolvedEmptyMessage = isMember ? t('research.milestone.memberEmpty') : emptyMessage ?? t('research.milestone.empty');

  const { data: students = [], isLoading: isLoadingStudents } = useResearchEligibleStudents(
    canCreate && !groupId ? labId : null,
  );
  const { data: groupMembers = [], isLoading: isLoadingMembers } = useResearchGroupMembers(
    canCreate && groupId ? groupId : null,
  );
  const resolvedStudents = useMemo(() => {
    if (groupId) {
      return groupMembers.map((m) => ({
        id: m.id,
        userId: m.userId,
        fullName: m.fullName,
        email: m.email ?? '',
        labId: labId ?? 0,
        labName: '',
        role: m.role,
        status: 'ACTIVE',
      }));
    }
    return students;
  }, [groupId, groupMembers, students, labId]);
  const resolvedLoadingStudents = groupId ? isLoadingMembers : isLoadingStudents;

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
          <h3 className="text-lg font-semibold text-slate-950">{resolvedTitle}</h3>
        </div>
        {canCreate ? (
          <Button onClick={() => setIsCreateOpen(true)}>
            {t('research.milestone.createTitle')}
          </Button>
        ) : null}
      </div>

      {isLoading ? (
        <LoadingState className="mt-5">{t('research.milestone.loading')}</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          {errorMessage}
        </ErrorState>
      ) : !milestones.length ? (
        <EmptyState className="mt-5">
          <div className="flex flex-col items-center justify-center text-center py-6 gap-3">
            <p className="text-slate-600 font-medium">
              {resolvedEmptyMessage}
            </p>
            {canCreate && (
              <Button onClick={() => setIsCreateOpen(true)} size="sm">
                {t('research.milestone.createTitle')}
              </Button>
            )}
          </div>
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
                    {milestone.description ? <p className="mt-2 text-sm text-slate-600">{milestone.description}</p> : null}
                    <dl className="mt-3 grid gap-3 text-sm text-slate-600 sm:grid-cols-2">
                      <div>
                        <dt className="font-semibold text-slate-700">{t('research.milestone.dueDate')}</dt>
                        <dd className="mt-1">{formatDate(milestone.deadline, locale)}</dd>
                      </div>
                      <div>
                        <dt className="font-semibold text-slate-700">{t('research.group.createdAt')}</dt>
                        <dd className="mt-1">{formatDate(milestone.createdAt, locale)}</dd>
                      </div>
                      {isMember ? (
                        <>
                          <div>
                            <dt className="font-semibold text-slate-700">{t('research.milestone.myTaskCount')}</dt>
                            <dd className="mt-1 font-medium text-slate-950">{milestone.myTaskCount ?? 0}</dd>
                          </div>
                          <div>
                            <dt className="font-semibold text-slate-700">{t('research.milestone.myCompletedTaskCount')}</dt>
                            <dd className="mt-1 font-medium text-emerald-700">{milestone.myCompletedTaskCount ?? 0}</dd>
                          </div>
                        </>
                      ) : null}
                    </dl>
                    <div className="mt-3 max-w-sm">
                      <div className="flex items-center justify-between text-sm">
                        <span className="font-semibold text-slate-700">{t('research.group.progress')}</span>
                        <span className="font-semibold text-slate-950">{milestone.progressPercent}%</span>
                      </div>
                      <div
                        aria-label={`${t('research.group.progress')} ${milestone.progressPercent}%`}
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
                        {t('research.milestone.viewDetails')}
                      </Button>
                      {canCreate ? (
                        <Button onClick={() => setEditingMilestone(milestone)} size="sm" variant="outline">
                          {t('research.milestone.edit')}
                        </Button>
                      ) : null}
                    </div>
                  </div>
                  <span className={`w-fit shrink-0 rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(displayedStatus)}`}>
                    {formatMilestoneStatus(displayedStatus, locale)}
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
            students={resolvedStudents}
            isLoadingStudents={resolvedLoadingStudents}
            isSubmitting={createMilestone.isPending}
            onClose={() => setIsCreateOpen(false)}
            onSubmit={(payload) => createMilestone.mutate({ ...payload, groupId }, { onSuccess: () => setIsCreateOpen(false) })}
          />
          <EditMilestoneModal
            milestone={editingMilestone}
            students={resolvedStudents}
            isLoadingStudents={resolvedLoadingStudents}
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
        labId={labId}
        onClose={() => setDetailMilestoneId(null)}
      />
    </section>
  );
}
