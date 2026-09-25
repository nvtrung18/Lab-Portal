import { useState } from 'react';
import { Link } from 'react-router-dom';

import { Button, EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import {
  useCreateResearchGroup,
  useResearchEligibleStudents,
  useResearchGroupsByProject,
  useUpdateResearchGroup,
  useMilestonesByGroup,
  useGroupReports,
  useProductsByGroup,
  useProjectDashboardStats,
} from '../hooks';
import type { ResearchGroup, ResearchProject } from '../types';
import { formatGroupStatus, getStatusClass } from '../utils';
import { CreateResearchGroupModal } from './CreateResearchGroupModal';
import { EditResearchGroupModal } from './EditResearchGroupModal';

interface ResearchGroupListProps {
  project: ResearchProject;
  canCreate: boolean;
}

export function ResearchGroupList({ project, canCreate }: ResearchGroupListProps) {
  const { locale, t } = useI18n();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [editingGroup, setEditingGroup] = useState<ResearchGroup | null>(null);
  const { data: groups = [], isError, isLoading, refetch } = useResearchGroupsByProject(project.id);
  const { data: stats } = useProjectDashboardStats(project.id);
  const { data: students = [], isLoading: isLoadingStudents } = useResearchEligibleStudents(
    canCreate ? project.labId : null,
  );
  const createGroup = useCreateResearchGroup(project.id, project.labId);
  const updateGroup = useUpdateResearchGroup(project.id, project.labId);

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">{t('research.group.listTitle')}</h3>
        </div>
        {canCreate ? (
          <Button onClick={() => setIsCreateOpen(true)}>
            {t('research.group.createTitle')}
          </Button>
        ) : null}
      </div>

      {isLoading ? (
        <LoadingState className="mt-5">{t('common.loading')}</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          {t('research.group.loadFailed')}
        </ErrorState>
      ) : !groups.length ? (
        <EmptyState className="mt-5">
          {t('research.group.noProjectGroups')}
        </EmptyState>
      ) : (
        <div className="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {groups.map((group) => (
            <GroupCard
              key={group.id}
              group={group}
              projectId={project.id}
              stats={stats}
              canCreate={canCreate}
              locale={locale}
              t={t}
              onEdit={() => setEditingGroup(group)}
            />
          ))}
        </div>
      )}

      {canCreate ? (
        <>
          <CreateResearchGroupModal
            isOpen={isCreateOpen}
            project={project}
            students={students}
            isLoadingStudents={isLoadingStudents}
            isSubmitting={createGroup.isPending}
            onClose={() => setIsCreateOpen(false)}
            onSubmit={(payload) => createGroup.mutate(payload, { onSuccess: () => setIsCreateOpen(false) })}
          />
          <EditResearchGroupModal
            group={editingGroup}
            students={students}
            isLoadingStudents={isLoadingStudents}
            isSubmitting={updateGroup.isPending}
            onClose={() => setEditingGroup(null)}
            onSubmit={(payload) => {
              if (!editingGroup) {
                return;
              }
              updateGroup.mutate(
                { groupId: editingGroup.id, payload },
                { onSuccess: () => setEditingGroup(null) },
              );
            }}
          />
        </>
      ) : null}
    </section>
  );
}

function GroupCard({
  group,
  projectId,
  stats,
  canCreate,
  locale,
  t,
  onEdit,
}: {
  group: ResearchGroup;
  projectId: number;
  stats?: any;
  canCreate: boolean;
  locale: 'vi' | 'en';
  t: ReturnType<typeof useI18n>['t'];
  onEdit: () => void;
}) {
  const { data: milestones = [] } = useMilestonesByGroup(group.id);
  const { data: reports = [] } = useGroupReports(group.id);
  const { data: products = [] } = useProductsByGroup(group.id);

  const groupStats = stats?.groupProgress?.find((g: any) => g.groupId === group.id);
  const taskCompletionRate = groupStats?.taskCompletionRate ?? 0;
  
  const pendingReportsCount = reports.filter(
    (r: any) => r.status === 'SUBMITTED' || r.status === 'LEADER_REVIEWED'
  ).length;

  return (
    <article className="flex flex-col justify-between rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition-all hover:border-blue-500 hover:shadow-md">
      <div>
        <div className="flex items-start justify-between gap-3">
          <div>
            <h4 className="text-base font-medium text-slate-900 line-clamp-1">{group.name}</h4>
            <p className="mt-1 text-xs text-slate-500 line-clamp-2">
              {group.objective || t('research.group.noObjectiveText')}
            </p>
          </div>
          <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ${getStatusClass(group.status)}`}>
            {formatGroupStatus(group.status, locale)}
          </span>
        </div>

        <div className="mt-4 grid grid-cols-2 gap-3 border-y border-slate-100 py-3 text-xs">
          <div>
            <span className="block font-medium text-slate-500">{t('research.group.leader')}</span>
            <span className="mt-0.5 block font-semibold text-slate-850 truncate">
              {group.leaderName || t('common.notAssigned')}
            </span>
          </div>
          <div>
            <span className="block font-medium text-slate-500">{t('research.group.membersLabel')}</span>
            <span className="mt-0.5 block font-semibold text-slate-850">
              {group.memberCount ?? group.members?.length ?? 0} {t('research.group.students')}
            </span>
          </div>
          <div>
            <span className="block font-medium text-slate-500">{t('research.group.workMilestones')}</span>
            <span className="mt-0.5 block font-semibold text-slate-850">
              {milestones.length} {t('research.milestone.title').toLowerCase()}
            </span>
          </div>
          <div>
            <span className="block font-medium text-slate-500">{t('research.group.productsSubmitted')}</span>
            <span className="mt-0.5 block font-semibold text-slate-850">
              {products.length} {t('research.product.title').toLowerCase()}
            </span>
          </div>
          <div>
            <span className="block font-medium text-slate-500">{t('research.group.pendingReports')}</span>
            <span className={`mt-0.5 block font-semibold ${pendingReportsCount > 0 ? 'text-orange-600 font-medium' : 'text-slate-800'}`}>
              {pendingReportsCount} {t('research.report.submitted').toLowerCase()}
            </span>
          </div>
          <div>
            <span className="block font-medium text-slate-500">{t('research.group.averageScore')}</span>
            <span className="mt-0.5 block font-semibold text-slate-850">
              {groupStats?.averageEvaluationScore != null
                ? groupStats.averageEvaluationScore.toFixed(1)
                : t('research.group.noScore')}
            </span>
          </div>
        </div>

        <div className="mt-3 max-w-full">
          <div className="flex items-center justify-between text-xs">
            <span className="font-medium text-slate-500">{t('research.group.taskProgress')}</span>
            <span className="font-medium text-slate-800">{taskCompletionRate}%</span>
          </div>
          <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
            <div
              className="h-full rounded-full bg-emerald-500 transition-all duration-300"
              style={{ width: `${taskCompletionRate}%` }}
            />
          </div>
        </div>
      </div>

      <div className="mt-5 flex gap-2 pt-2">
        <Link
          className="flex-1 rounded-lg bg-slate-900 py-2 text-center text-xs font-semibold text-white transition-colors hover:bg-slate-800"
          to={`/app/research/projects/${projectId}/groups/${group.id}`}
        >
          {t('research.group.viewDetails')}
        </Link>
        {canCreate ? (
          <Button onClick={onEdit} size="sm" variant="outline">
            {t('research.group.edit')}
          </Button>
        ) : null}
      </div>
    </article>
  );
}
