import { useEffect, useState } from 'react';
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom';
import axios from 'axios';

import { EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
import { useI18n } from '../../../shared/i18n';
import { getManagedLabId } from '../../../shared/utils/membership';
import { useCurrentUser } from '../../user/hooks';
import {
  MilestoneList,
  TaskBoard,
  GroupReportsTab,
  ProductPage,
  EvaluationPage,
  LogPage,
  MyResearchTasks,
} from '../components';
import {
  useResearchProject,
  useResearchGroup,
  useMilestonesByGroup,
  useMyMilestonesByGroup,
  useGroupTasks,
  useGroupReports,
  useMyGroupReports,
  useProductsByGroup,
  useProjectDashboardStats,
} from '../hooks';
import type { TaskBoardRole } from '../taskBoardHelpers';
import type { ResearchGroup, ResearchProject } from '../types';
import { formatDate, formatGroupStatus, formatProjectStatus, getApiErrorMessage } from '../utils';
import { formatTaskStatus } from '../taskBoardHelpers';

type GroupDetailTab =
  | 'overview'
  | 'members'
  | 'project'
  | 'milestones'
  | 'tasks'
  | 'reports'
  | 'products'
  | 'evaluations'
  | 'logs';

const GROUP_DETAIL_MANAGER_TABS: Array<{ value: GroupDetailTab; labelKey: string }> = [
  { value: 'overview', labelKey: 'research.groupTab.overview' },
  { value: 'members', labelKey: 'research.groupTab.members' },
  { value: 'project', labelKey: 'research.groupTab.project' },
  { value: 'milestones', labelKey: 'research.groupTab.milestones' },
  { value: 'tasks', labelKey: 'research.groupTab.tasks' },
  { value: 'reports', labelKey: 'research.groupTab.reports' },
  { value: 'products', labelKey: 'research.groupTab.products' },
  { value: 'evaluations', labelKey: 'research.groupTab.evaluations' },
  { value: 'logs', labelKey: 'research.groupTab.logs' },
];

const GROUP_DETAIL_STUDENT_TABS = (isLeader: boolean): Array<{ value: GroupDetailTab; labelKey: string }> => [
  { value: 'overview', labelKey: 'research.groupTab.info' },
  { value: 'members', labelKey: 'research.groupTab.members' },
  { value: 'project', labelKey: 'research.groupTab.project' },
  { value: 'milestones', labelKey: 'research.groupTab.milestones' },
  { value: 'tasks', labelKey: 'research.groupTab.tasks' },
  { value: 'reports', labelKey: 'research.groupTab.reports' },
  { value: 'products', labelKey: 'research.groupTab.products' },
  { value: 'evaluations', labelKey: 'research.groupTab.evaluations' },
  ...(isLeader ? [{ value: 'logs', labelKey: 'research.groupTab.studentLogs' } as const] : []),
];

const GROUP_DETAIL_TAB_KEYS = GROUP_DETAIL_MANAGER_TABS.map((tab) => tab.value);

function isGroupDetailTab(value: string | null): value is GroupDetailTab {
  return GROUP_DETAIL_TAB_KEYS.includes(value as GroupDetailTab);
}

export function ResearchGroupDetailPage() {
  const { locale, t } = useI18n();
  const [searchParams, setSearchParams] = useSearchParams();
  const { projectId, groupId } = useParams();
  const numericProjectId = Number(projectId);
  const numericGroupId = Number(groupId);

  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const roles = currentUser?.roles.map((role) => role.replace(/^ROLE_/, '')) ?? [];
  const isManager = roles.includes(LAB_MANAGER);
  const isStudent = roles.includes(STUDENT);
  const managedLabId = getManagedLabId(currentUser);

  const { data: project, isError: isProjectError, error: projectError, isLoading: isLoadingProject, refetch: refetchProject } = useResearchProject(
    Number.isFinite(numericProjectId) ? numericProjectId : null
  );
  const { data: group, isError: isGroupError, error: groupError, isLoading: isLoadingGroup, refetch: refetchGroup } = useResearchGroup(
    Number.isFinite(numericGroupId) ? numericGroupId : null
  );
  const userInGroup = group?.members?.find((m) => m.userId === currentUser?.id);
  const groupRole = group?.myGroupRole || group?.myRole || userInGroup?.role;
  const isLeader = groupRole === 'LEADER';
  const isMember = groupRole === 'MEMBER';
  const tabs = isManager
    ? GROUP_DETAIL_MANAGER_TABS
    : GROUP_DETAIL_STUDENT_TABS(isLeader);
  const tabParam = searchParams.get('tab');
  const requestedTab = isGroupDetailTab(tabParam) ? tabParam : 'overview';
  const activeTab = tabs.some((tab) => tab.value === requestedTab) ? requestedTab : 'overview';

  function handleTabChange(nextTab: GroupDetailTab) {
    setSearchParams((currentParams) => {
      const nextParams = new URLSearchParams(currentParams);
      nextParams.set('tab', nextTab);
      return nextParams;
    }, { replace: true });
  }

  const { data: stats } = useProjectDashboardStats(
    project?.id && activeTab === 'overview' ? project.id : null
  );

  if (!Number.isFinite(numericProjectId) || !Number.isFinite(numericGroupId)) {
    return <Navigate to="/app/research" replace />;
  }

  if (isLoadingUser || isLoadingProject || isLoadingGroup) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <LoadingState>{t('common.loading')}</LoadingState>
      </section>
    );
  }

  if (!isManager && !isStudent) {
    return <Navigate to="/403" replace />;
  }

  if (isProjectError || isGroupError || !project || !group) {
    let errorMessage = t('common.loadError');
    if (isGroupError && axios.isAxiosError(groupError)) {
      const status = groupError.response?.status;
      if (status === 403) {
        errorMessage = t('research.forbidden');
      } else if (status === 404) {
        errorMessage = t('common.notFound');
      }
    } else if (isProjectError && axios.isAxiosError(projectError)) {
      const status = projectError.response?.status;
      if (status === 403) {
        errorMessage = t('research.forbidden');
      } else if (status === 404) {
        errorMessage = t('common.notFound');
      }
    }

    return (
      <section className="rounded-lg border border-red-200 bg-white p-6 shadow-sm">
        <ErrorState onRetry={() => { refetchProject(); refetchGroup(); }}>
          {errorMessage}
        </ErrorState>
      </section>
    );
  }

  if (isManager && (!managedLabId || project.labId !== managedLabId)) {
    return <Navigate to="/403" replace />;
  }

  if (group.projectId !== project.id && project.groupId !== group.id) {
    return <Navigate to="/app/research" replace />;
  }

  // Security & Role check: currentUser must belong to the group
  const canViewGroupManagement = isManager || isLeader;
  const canViewMyWork = isLeader || isMember;
  const canViewGroupProducts = canViewGroupManagement || isMember;

  if (!isManager && !canViewMyWork) {
    return <Navigate to="/403" replace />;
  }

  // Resolve roles for subcomponents
  const taskBoardRole: TaskBoardRole | undefined = isManager
    ? 'LAB_MANAGER'
    : isLeader
    ? 'GROUP_LEADER'
    : isMember
    ? 'STUDENT_MEMBER'
    : undefined;

  return (
    <section className="min-w-0 space-y-6">
      {/* Premium Header */}
      <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-4">
          {isStudent ? (
            <Link
              className="inline-flex min-h-11 items-center rounded-md border border-slate-200 bg-white px-3.5 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 transition duration-150"
              to="/app/research"
            >
              {t('common.backToList')}
            </Link>
          ) : (
            <Link
              className="inline-flex min-h-11 items-center rounded-md border border-slate-200 bg-white px-3.5 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 transition duration-150"
              to={`/app/research/projects/${project.id}`}
            >
              {t('common.back')}
            </Link>
          )}
          <span className="min-w-0 break-words text-xs font-semibold text-slate-500 tracking-normal dark:text-slate-400">{t('research.groupTab.project')}: {project.title}</span>
        </div>

        <div className="mt-4 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2.5">
              <h2 className="min-w-0 break-words text-2xl font-medium text-slate-950">{group.name}</h2>
              <span className="rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-medium text-blue-700 ring-1 ring-blue-100">
                {t('research.groupTab.info')}
              </span>
            </div>
            <p className="mt-2 max-w-4xl break-words text-sm leading-relaxed text-slate-600">
              {group.objective || t('common.notUpdated')}
            </p>
          </div>
        </div>

        <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-5 border-t border-slate-100 pt-5">
          <div className="min-w-0 flex-1">
            <dt className="font-semibold text-slate-500 text-xs tracking-normal dark:text-slate-400">{t('research.groupTab.project')}</dt>
            <dd className="mt-1 text-slate-800 font-medium truncate leading-snug" title={project.title}>
              {project.title}
            </dd>
          </div>
          <div>
            <dt className="font-semibold text-slate-500 text-xs tracking-normal dark:text-slate-400">{t('research.role.leader')}</dt>
            <dd className="mt-1 text-slate-800 font-medium">{group.leaderName || t('common.notUpdated')}</dd>
          </div>
          {isStudent ? (
            <div>
              <dt className="font-semibold text-slate-500 text-xs tracking-normal dark:text-slate-400">{t('research.role.member')}</dt>
              <dd className="mt-1">
                <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ${
                  isLeader ? 'bg-blue-50 text-blue-700 ring-blue-100' : 'bg-slate-50 text-slate-700 ring-slate-100'
                }`}>
                  {isLeader ? t('research.role.leader') : t('research.role.member')}
                </span>
              </dd>
            </div>
          ) : (
            <div>
              <dt className="font-semibold text-slate-500 text-xs tracking-normal dark:text-slate-400">{t('research.groupTab.members')}</dt>
              <dd className="mt-1 text-slate-800 font-medium">{group.members?.length ?? 0}</dd>
            </div>
          )}
          <div>
            <dt className="font-semibold text-slate-500 text-xs tracking-normal dark:text-slate-400">{t('slot.statusLabel')}</dt>
            <dd className="mt-1">
              <span className="inline-flex rounded-full bg-slate-50 px-2.5 py-0.5 text-xs font-medium text-slate-700 ring-1 ring-slate-100">
                {formatGroupStatus(group.status, locale)}
              </span>
            </dd>
          </div>
          <div>
            <dt className="font-semibold text-slate-500 text-xs tracking-normal dark:text-slate-400">{t('common.submittedAt')}</dt>
            <dd className="mt-1 text-slate-800 font-medium">{formatDate(group.createdAt, locale)}</dd>
          </div>
        </dl>
      </div>

      {/* Tabs list */}
      <div className="research-detail-tabs gap-1" role="tablist" aria-label={t('research.groupTab.info')}>
        {tabs.map((tab) => (
          <button
            className={`shrink-0 whitespace-nowrap border-b-2 px-4 py-3 text-sm font-semibold transition-all ${
              activeTab === tab.value
                ? 'border-blue-600 text-blue-600 font-medium'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
            key={tab.value}
            type="button"
            role="tab"
            aria-selected={activeTab === tab.value}
            onClick={() => handleTabChange(tab.value)}
          >
            {t(tab.labelKey)}
          </button>
        ))}
      </div>

      {/* Renders Tabs content */}
      {activeTab === 'overview' ? (
        <GroupOverviewTab
          project={project}
          group={group}
          isManager={isManager}
          isStudent={isStudent}
          isLeader={isLeader}
          stats={stats}
        />
      ) : null}

      {activeTab === 'members' ? (
        <GroupMembersTab group={group} isLeader={isLeader} />
      ) : null}

      {activeTab === 'project' ? (
        <GroupProjectTab project={project} />
      ) : null}



      {activeTab === 'milestones' ? (
        isStudent && isLeader ? (
          <LeaderMilestonesWrapper
            projectId={project.id}
            labId={project.labId}
            groupId={group.id}
          />
        ) : (
          <MilestoneList
            key={`milestones-${group.id}`}
            projectId={project.id}
            labId={project.labId}
            canCreate={isManager}
            groupId={group.id}
            groupRole={isManager ? null : 'MEMBER'}
            emptyMessage={t('research.group.noMilestones')}
          />
        )
      ) : null}

      {activeTab === 'tasks' ? (
        isStudent && isLeader ? (
          <LeaderTasksWrapper
            projectId={project.id}
            groupId={group.id}
            currentUserId={currentUser?.id}
            taskBoardRole={taskBoardRole}
            labId={project.labId}
          />
        ) : isStudent && isMember ? (
          <MyResearchTasks
            key={`my-tasks-${group.id}`}
            groupId={group.id}
            projectId={project.id}
            currentUserId={currentUser?.id}
          />
        ) : (
          <GroupTasksTab
            projectId={project.id}
            groupId={group.id}
            taskBoardRole={taskBoardRole}
            currentUserId={currentUser?.id}
            labId={project.labId}
          />
        )
      ) : null}

      {activeTab === 'reports' ? (
        <GroupReportsTab
          groupId={group.id}
          projectId={project.id}
          currentUserId={currentUser?.id}
          role={isManager ? 'LAB_MANAGER' : isLeader ? 'GROUP_LEADER' : 'STUDENT_MEMBER'}
          labId={project.labId}
        />
      ) : null}

      {activeTab === 'products' && canViewGroupProducts ? (
        <ProductPage
          key={`products-${group.id}`}
          currentUserId={currentUser?.id}
          projectId={project.id}
          groupId={group.id}
          role={isManager ? LAB_MANAGER : STUDENT}
          groupRole={isLeader ? 'LEADER' : 'MEMBER'}
        />
      ) : null}

      {activeTab === 'evaluations' && (isManager || canViewMyWork) ? (
        <EvaluationPage
          key={`evaluation-${group.id}`}
          currentUserId={currentUser?.id}
          projectId={project.id}
          groupId={group.id}
          role={isManager ? LAB_MANAGER : STUDENT}
        />
      ) : null}

      {activeTab === 'logs' && canViewGroupManagement ? (
        <LogPage
          key={`logs-${group.id}`}
          currentUser={currentUser}
          projectId={project.id}
          groupId={group.id}
          role={isManager ? LAB_MANAGER : STUDENT}
          groupRole={isLeader ? 'LEADER' : 'MEMBER'}
        />
      ) : null}
    </section>
  );
}

// 1. Group Overview Tab Subcomponent
function GroupOverviewTab({
  project,
  group,
  isManager,
  isStudent,
  isLeader,
  stats,
}: {
  project: any;
  group: any;
  isManager: boolean;
  isStudent: boolean;
  isLeader: boolean;
  stats?: any;
}) {
  const { locale, t } = useI18n();
  const canViewGroupManagement = isManager || isLeader;
  const { data: groupMilestones = [] } = useMilestonesByGroup(canViewGroupManagement ? group.id : null);
  const { data: myMilestones = [] } = useMyMilestonesByGroup(!canViewGroupManagement && isStudent ? group.id : null);
  const { data: groupReports = [] } = useGroupReports(canViewGroupManagement ? group.id : null);
  const { data: myReports = [] } = useMyGroupReports(!canViewGroupManagement && isStudent ? group.id : null);
  const { data: products = [] } = useProductsByGroup(canViewGroupManagement ? group.id : null);
  const milestones = canViewGroupManagement ? groupMilestones : myMilestones;
  const reports = canViewGroupManagement ? groupReports : myReports;

  const groupStats = stats?.groupProgress?.find((g: any) => g.groupId === group.id);
  const taskCompletionRate = groupStats?.taskCompletionRate ?? 0;

  const completedMilestones = milestones.filter((m: any) => m.status === 'COMPLETED').length;

  return (
    <div className="space-y-6">
      {/* Aggregate Cards Grid */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatItem
          title={t('research.milestone.completed')}
          value={`${completedMilestones}/${milestones.length}`}
        />
        <StatItem
          title={t('research.group.progress')}
          value={`${taskCompletionRate}%`}
        />
        <StatItem
          title={t('research.groupTab.reports')}
          value={reports.length}
        />
        <StatItem
          title={t('research.groupTab.products')}
          value={products.length}
        />
      </div>

      {isStudent && (
        <div className="min-w-0 space-y-4 rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
          <h3 className="text-base font-medium text-slate-900 border-b border-slate-100 pb-3">{t('research.groupTab.info')}</h3>
          <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 text-sm">
            <div className="bg-slate-50/50 rounded-lg p-3.5 border border-slate-100 space-y-0.5 dark:border-slate-800 dark:bg-slate-950/40">
              <dt className="font-semibold text-slate-500 text-xs tracking-normal dark:text-slate-400">{t('research.group.name')}</dt>
              <dd className="break-words font-medium text-slate-900 text-base">{group.name}</dd>
            </div>
            <div className="bg-slate-50/50 rounded-lg p-3.5 border border-slate-100 space-y-0.5 dark:border-slate-800 dark:bg-slate-950/40">
              <dt className="font-semibold text-slate-500 text-xs tracking-normal dark:text-slate-400">{t('research.group.leader')}</dt>
              <dd className="font-medium text-slate-800">{group.leaderName || t('lab.notAssigned')}</dd>
            </div>
            <div className="bg-slate-50/50 rounded-lg p-3.5 border border-slate-100 space-y-0.5 dark:border-slate-800 dark:bg-slate-950/40">
              <dt className="font-semibold text-slate-500 text-xs tracking-normal dark:text-slate-400">{t('research.group.myRole')}</dt>
              <dd className="font-medium text-slate-800">
                <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ${
                  isLeader ? 'bg-blue-50 text-blue-700 ring-blue-100' : 'bg-slate-50 text-slate-700 ring-slate-100'
                }`}>
                  {isLeader ? t('research.role.leader') : t('research.role.member')}
                </span>
              </dd>
            </div>
            <div className="bg-slate-50/50 rounded-lg p-3.5 border border-slate-100 space-y-0.5 dark:border-slate-800 dark:bg-slate-950/40">
              <dt className="font-semibold text-slate-500 text-xs tracking-normal dark:text-slate-400">{t('research.group.status')}</dt>
              <dd>
                <span className="inline-flex rounded-full bg-slate-50 px-2.5 py-0.5 text-xs font-medium text-slate-700 ring-1 ring-slate-100">
                  {formatGroupStatus(group.status, locale)}
                </span>
              </dd>
            </div>
            <div className="bg-slate-50/50 rounded-lg p-3.5 border border-slate-100 space-y-0.5 dark:border-slate-800 dark:bg-slate-950/40">
              <dt className="font-semibold text-slate-500 text-xs tracking-normal dark:text-slate-400">{t('research.group.createdAt')}</dt>
              <dd className="font-medium text-slate-800">{formatDate(group.createdAt, locale)}</dd>
            </div>
          </dl>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Plan / Descriptions Card */}
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm lg:col-span-2 space-y-4">
          <div>
            <h3 className="text-base font-medium text-slate-900">{t('research.group.plan')}</h3>
            <p className="mt-2 break-words text-sm text-slate-600 leading-relaxed whitespace-pre-line">
              {group.plan || t('research.group.noPlan')}
            </p>
          </div>
          <div className="border-t border-slate-100 pt-4">
            <h3 className="text-base font-medium text-slate-900">{t('research.group.description')}</h3>
            <p className="mt-2 break-words text-sm text-slate-600 leading-relaxed">
              {group.description || t('research.group.noDescription')}
            </p>
          </div>
        </div>

        {/* Members List Card */}
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-base font-medium text-slate-900">{t('research.group.membersCount', undefined, { count: group.members?.length ?? 0 })}</h3>
          <div className="mt-4 divide-y divide-slate-100">
            {group.members?.map((member: any) => (
              <div key={member.id} className="flex flex-col items-start gap-2 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <span className="block break-words font-semibold text-slate-900">{member.fullName || member.email}</span>
                  <span className="block break-all text-xs text-slate-500">{member.email}</span>
                </div>
                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ring-1 ${
                  member.role === 'LEADER'
                    ? 'bg-blue-50 text-blue-700 ring-blue-100'
                    : 'bg-slate-50 text-slate-700 ring-slate-100'
                }`}>
                  {member.role === 'LEADER' ? t('research.role.leader') : t('research.role.member')}
                </span>
              </div>
            ))}
            {!group.members?.length && (
              <p className="py-3 text-sm text-slate-500 text-center">{t('research.group.noMembers')}</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

// 2. Group Members Tab Subcomponent
function GroupMembersTab({ group, isLeader }: { group: ResearchGroup; isLeader: boolean }) {
  const { locale, t } = useI18n();
  return (
    <div className="min-w-0 space-y-6 rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
      <div>
        <h3 className="text-lg font-medium text-slate-950">{t('research.groupTab.members')}</h3>
      </div>

      <div className="space-y-3 md:hidden">
        {group.members?.map((member) => (
          <article className="min-w-0 rounded-lg border border-slate-200 p-4 text-sm" key={member.id}>
            <h4 className="break-words font-semibold text-slate-900">{member.fullName || t('common.notUpdated')}</h4>
            <p className="mt-1 break-all text-slate-600">{member.email}</p>
            <dl className="mt-3 space-y-2">
              <div><dt className="text-slate-500">{t('research.group.role')}</dt><dd className="mt-1 text-slate-900">{member.role === 'LEADER' ? t('research.role.leader') : t('research.role.member')}</dd></div>
              <div><dt className="text-slate-500">{t('research.group.joinedAt')}</dt><dd className="mt-1 text-slate-900">{formatDate(member.joinedAt || group.createdAt, locale)}</dd></div>
            </dl>
          </article>
        ))}
        {!group.members?.length ? <EmptyState>{t('research.group.noMembersInGroup')}</EmptyState> : null}
      </div>

      <div aria-label={t('research.group.members')} role="region" tabIndex={0} className="hidden min-w-0 max-w-full overflow-x-auto overscroll-x-contain md:block">
        <table className="w-full min-w-[40rem] text-left text-sm border-collapse">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold tracking-normal text-slate-600 dark:border-slate-800 dark:bg-slate-800/60 dark:text-slate-300">
              <th className="px-6 py-3 font-semibold">{t('research.group.fullName')}</th>
              <th className="px-6 py-3 font-semibold">{t('common.email')}</th>
              <th className="px-6 py-3 font-semibold">{t('research.group.role')}</th>
              <th className="px-6 py-3 font-semibold">{t('research.group.joinedAt')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {group.members?.map((member) => (
              <tr key={member.id} className="hover:bg-slate-50/55 transition duration-150">
                <td className="px-6 py-4 font-semibold text-slate-900">
                  {member.fullName || t('common.notUpdated')}
                </td>
                <td className="break-all px-6 py-4 text-slate-600">{member.email}</td>
                <td className="px-6 py-4">
                  <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ${
                    member.role === 'LEADER'
                      ? 'bg-blue-50 text-blue-700 ring-blue-100'
                      : 'bg-slate-50 text-slate-700 ring-slate-100'
                  }`}>
                    {member.role === 'LEADER' ? t('research.role.leader') : t('research.role.member')}
                  </span>
                </td>
                <td className="px-6 py-4 text-slate-500">
                  {formatDate(member.joinedAt || group.createdAt, locale)}
                </td>
              </tr>
            ))}
            {!group.members?.length && (
              <tr>
                <td colSpan={4} className="px-6 py-8 text-center text-slate-500">
                  {t('research.group.noMembersInGroup')}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// 3. Group Project Tab Subcomponent
function GroupProjectTab({ project }: { project: ResearchProject }) {
  const { locale, t } = useI18n();

  function getProjectStatusClass(status: ResearchProject['status']) {
    switch (status) {
      case 'ONGOING':
        return 'bg-emerald-50 text-emerald-700 ring-emerald-100 dark:bg-emerald-950/60 dark:text-emerald-300 dark:ring-emerald-500/30';
      case 'COMPLETED':
        return 'bg-blue-50 text-blue-700 ring-blue-100 dark:bg-blue-950/60 dark:text-blue-300 dark:ring-blue-500/30';
      case 'WAITING_REVIEW':
        return 'bg-amber-50 text-amber-700 ring-amber-100 dark:bg-amber-950/60 dark:text-amber-300 dark:ring-amber-500/30';
      case 'DRAFT':
      case 'PLANNED':
        return 'bg-slate-50 text-slate-700 ring-slate-100 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700';
      default:
        return 'bg-red-50 text-red-700 ring-red-100 dark:bg-red-950/60 dark:text-red-300 dark:ring-red-500/30';
    }
  }

  return (
    <div className="min-w-0 space-y-6 rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-100 pb-4">
        <div>
          <h3 className="text-lg font-medium text-slate-950">{t('research.group.projectInfo')}</h3>
        </div>
        <span className={`inline-flex shrink-0 rounded-full px-3 py-1 text-xs font-medium ring-1 ${getProjectStatusClass(project.status)}`}>
          {formatProjectStatus(project.status, locale)}
        </span>
      </div>

      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        <div className="rounded-lg border border-slate-100 bg-slate-50/50 p-4 space-y-1 dark:border-slate-800 dark:bg-slate-950/40">
          <span className="block text-xs font-semibold text-slate-500 tracking-normal dark:text-slate-400">{t('research.group.projectName')}</span>
          <span className="block break-words font-medium text-slate-900 text-sm leading-snug">{project.title}</span>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50/50 p-4 space-y-1 dark:border-slate-800 dark:bg-slate-950/40">
          <span className="block text-xs font-semibold text-slate-500 tracking-normal dark:text-slate-400">{t('research.group.topic')}</span>
          <span className="block font-semibold text-slate-800 text-sm">{project.researchDirection || t('common.notUpdated')}</span>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50/50 p-4 space-y-1 dark:border-slate-800 dark:bg-slate-950/40">
          <span className="block text-xs font-semibold text-slate-500 tracking-normal dark:text-slate-400">{t('research.group.codeManager')}</span>
          <span className="block font-semibold text-slate-800 text-sm">
            {project.code || t('common.notUpdated')} — {project.managerName || t('lab.notAssigned')}
          </span>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50/50 p-4 space-y-1 dark:border-slate-800 dark:bg-slate-950/40">
          <span className="block text-xs font-semibold text-slate-500 tracking-normal dark:text-slate-400">{t('research.project.startDate')}</span>
          <span className="block font-semibold text-slate-800 text-sm">{formatDate(project.startDate, locale)}</span>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50/50 p-4 space-y-1 dark:border-slate-800 dark:bg-slate-950/40 col-span-1 md:col-span-2">
          <span className="block text-xs font-semibold text-slate-500 tracking-normal dark:text-slate-400">{t('research.project.expectedEndDate')}</span>
          <span className="block font-semibold text-slate-800 text-sm">{formatDate(project.expectedEndDate, locale)}</span>
        </div>
      </div>

      <div className="border-t border-slate-100 pt-5 space-y-5">
        <div>
          <h4 className="text-sm font-medium text-slate-900 tracking-normal dark:text-slate-100">{t('research.group.details')}</h4>
          <p className="mt-2 break-words text-sm text-slate-600 leading-relaxed whitespace-pre-line">
            {project.description || t('research.group.noProjectDescription')}
          </p>
        </div>
        <div>
          <h4 className="text-sm font-medium text-slate-900 tracking-normal dark:text-slate-100">{t('research.group.objective')}</h4>
          <p className="mt-2 break-words text-sm text-slate-600 leading-relaxed whitespace-pre-line">
            {project.objective || t('research.group.noProjectObjective')}
          </p>
        </div>
        <div>
          <h4 className="text-sm font-medium text-slate-900 tracking-normal dark:text-slate-100">{t('research.group.requiredProducts')}</h4>
          <p className="mt-2 break-words text-sm text-slate-600 leading-relaxed whitespace-pre-line bg-blue-50/30 rounded-lg p-4 border border-blue-100/50 dark:border-blue-900/50 dark:bg-blue-950/30">
            {project.requiredProducts || t('research.group.noRequiredProducts')}
          </p>
        </div>
        <div>
          <h4 className="text-sm font-medium text-slate-900 tracking-normal dark:text-slate-100">{t('research.group.evaluationCriteria')}</h4>
          <p className="mt-2 break-words text-sm text-slate-600 leading-relaxed whitespace-pre-line bg-emerald-50/20 rounded-lg p-4 border border-emerald-100/30 dark:border-emerald-900/50 dark:bg-emerald-950/30">
            {project.evaluationCriteria || t('research.group.noEvaluationCriteria')}
          </p>
        </div>
      </div>
    </div>
  );
}

function GroupTaskList({ groupId }: { groupId: number }) {
  const { locale, t } = useI18n();
  const { data: tasks = [], error, isLoading, isError, refetch } = useGroupTasks(groupId);
  const errorMessage = getApiErrorMessage(error, {
    fallback: t('common.loadError'),
    forbidden: t('research.forbidden'),
  });

  return (
    <section className="min-w-0 rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
      <div>
        <h3 className="text-lg font-semibold text-slate-950">{t('research.group.taskBoard')}</h3>
      </div>

      {isLoading ? (
        <LoadingState className="mt-5">{t('research.group.tasksLoading')}</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          {errorMessage}
        </ErrorState>
      ) : !tasks.length ? (
        <EmptyState className="mt-5">{t('research.group.noTasks')}</EmptyState>
      ) : (
        <div aria-label={t('research.group.taskBoard')} role="region" tabIndex={0} className="mt-5 min-w-0 max-w-full overflow-x-auto overscroll-x-contain">
          <table className="w-full min-w-[42rem] text-left text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold tracking-normal text-slate-600 dark:border-slate-800 dark:bg-slate-800/60 dark:text-slate-300">
                <th className="px-4 py-3 font-semibold">{t('research.group.task')}</th>
                <th className="px-4 py-3 font-semibold">{t('research.group.assignee')}</th>
                <th className="px-4 py-3 font-semibold">{t('research.group.deadline')}</th>
                <th className="px-4 py-3 font-semibold">{t('research.group.progress')}</th>
                <th className="px-4 py-3 font-semibold">{t('research.group.statusLabel')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {tasks.map((task) => (
                <tr key={task.id} className="align-top hover:bg-slate-50/60">
                  <td className="px-4 py-3">
                    <p className="font-semibold text-slate-950">{task.title}</p>
                    {task.description ? (
                      <p className="mt-1 text-xs text-slate-500">{task.description}</p>
                    ) : null}
                  </td>
                  <td className="px-4 py-3 text-slate-700">
                    {task.assigneeName || task.assigneeEmail || t('lab.notAssigned')}
                  </td>
                  <td className="px-4 py-3 text-slate-600">{formatDate(task.deadline, locale)}</td>
                  <td className="px-4 py-3">
                    <div className="min-w-32">
                      <div className="flex items-center justify-between text-xs font-semibold text-slate-600">
                        <span>{task.progressPercent}%</span>
                      </div>
                      <div className="mt-1 h-2 overflow-hidden rounded-full bg-slate-100">
                        <div
                          className="h-full rounded-full bg-emerald-600"
                          style={{ width: `${Math.min(100, Math.max(0, task.progressPercent))}%` }}
                        />
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span className="inline-flex rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-semibold text-slate-700">
                      {formatTaskStatus(task.status, locale)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

// 4. Group Tasks Tab Subcomponent (requires Milestone selector)
interface GroupTasksTabProps {
  projectId: number;
  groupId: number;
  taskBoardRole?: TaskBoardRole;
  currentUserId?: number | null;
  labId?: number | null;
}

function GroupTasksTab({
  projectId,
  groupId,
  taskBoardRole,
  currentUserId,
  labId,
}: GroupTasksTabProps) {
  const { t } = useI18n();
  const { data: milestones = [], isLoading, isError, error, refetch } = useMilestonesByGroup(groupId);
  const [selectedMilestoneId, setSelectedMilestoneId] = useState<number | null>(null);

  useEffect(() => {
    if (milestones.length > 0 && selectedMilestoneId === null) {
      setSelectedMilestoneId(milestones[0].id);
    }
  }, [milestones, selectedMilestoneId]);

  const errorMessage = getApiErrorMessage(error, {
    fallback: t('common.loadError'),
    forbidden: t('research.forbidden'),
  });

  if (isLoading) {
    return <LoadingState className="py-8">{t('common.loading')}</LoadingState>;
  }

  if (isError) {
    return (
      <ErrorState onRetry={refetch} className="py-8">
        {errorMessage}
      </ErrorState>
    );
  }

  if (milestones.length === 0) {
    return (
      <EmptyState className="py-12">
        {t('research.group.noMilestonesInstruction')}
      </EmptyState>
    );
  }

  const selectedMilestone = milestones.find((m) => m.id === selectedMilestoneId) || milestones[0];

  return (
    <div className="space-y-6">
      {/* Milestone Selector */}
      <div className="flex min-w-0 flex-col justify-between gap-4 rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:flex-row sm:items-center sm:p-5">
        <div>
          <h3 className="text-base font-medium text-slate-900">{t('research.group.tasksByMilestone')}</h3>
        </div>
        <div className="min-w-0 w-full sm:w-72">
          <label className="sr-only" htmlFor="research-milestone-select">{t('research.group.tasksByMilestone')}</label>
          <select
            id="research-milestone-select"
            value={selectedMilestoneId || ''}
            onChange={(e) => setSelectedMilestoneId(Number(e.target.value))}
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-800 focus:border-blue-500 focus:outline-none"
          >
            {milestones.map((m) => (
              <option key={m.id} value={m.id}>
                {m.title}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Task Board */}
      {selectedMilestoneId && (
        <div className="min-w-0 space-y-4 rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
          <div className="border-b border-slate-100 pb-4">
            <h4 className="break-words text-base font-medium text-slate-900">{selectedMilestone.title}</h4>
            {selectedMilestone.description ? <p className="mt-1 break-words text-sm text-slate-600">{selectedMilestone.description}</p> : null}
          </div>
          <TaskBoard
            projectId={projectId}
            groupId={groupId}
            milestoneId={selectedMilestoneId}
            readonly={!taskBoardRole}
            role={taskBoardRole}
            currentUserId={currentUserId}
            labId={labId}
          />
        </div>
      )}
    </div>
  );
}

// 5. Leader Tasks Wrapper
interface LeaderTasksWrapperProps {
  projectId: number;
  groupId: number;
  currentUserId?: number | null;
  taskBoardRole?: TaskBoardRole;
  labId?: number | null;
}

function LeaderTasksWrapper({
  projectId,
  groupId,
  currentUserId,
  taskBoardRole,
  labId,
}: LeaderTasksWrapperProps) {
  const { t } = useI18n();
  const [subTab, setSubTab] = useState<'me' | 'group'>('me');

  return (
    <div className="space-y-6">
      {/* Sub-tab selection */}
      <div className="flex max-w-full gap-1.5 overflow-x-auto rounded-md bg-slate-100 p-1 overscroll-x-contain sm:w-fit">
        <button
          onClick={() => setSubTab('me')}
          className={`shrink-0 whitespace-nowrap rounded-md px-4 py-1.5 text-xs font-semibold transition ${
            subTab === 'me'
              ? 'bg-white text-blue-700 shadow-sm font-medium'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          {t('research.group.myTasks')}
        </button>
        <button
          onClick={() => setSubTab('group')}
          className={`shrink-0 whitespace-nowrap rounded-md px-4 py-1.5 text-xs font-semibold transition ${
            subTab === 'group'
              ? 'bg-white text-blue-700 shadow-sm font-medium'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          {t('research.group.groupProgress')}
        </button>
      </div>

      {subTab === 'me' ? (
        <MyResearchTasks
          key={`my-tasks-me-${groupId}`}
          groupId={groupId}
          projectId={projectId}
          currentUserId={currentUserId}
        />
      ) : (
        <GroupTasksTab
          projectId={projectId}
          groupId={groupId}
          taskBoardRole={taskBoardRole}
          currentUserId={currentUserId}
          labId={labId}
        />
      )}
    </div>
  );
}

// 6. Leader Milestones Wrapper
interface LeaderMilestonesWrapperProps {
  projectId: number;
  labId?: number | null;
  groupId: number;
}

function LeaderMilestonesWrapper({
  projectId,
  labId,
  groupId,
}: LeaderMilestonesWrapperProps) {
  const { t } = useI18n();
  const [subTab, setSubTab] = useState<'me' | 'group'>('me');

  return (
    <div className="space-y-6">
      {/* Sub-tab selection */}
      <div className="flex max-w-full gap-1.5 overflow-x-auto rounded-md bg-slate-100 p-1 overscroll-x-contain sm:w-fit">
        <button
          onClick={() => setSubTab('me')}
          className={`shrink-0 whitespace-nowrap rounded-md px-4 py-1.5 text-xs font-semibold transition ${
            subTab === 'me'
              ? 'bg-white text-blue-700 shadow-sm font-medium'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          {t('research.group.myMilestones')}
        </button>
        <button
          onClick={() => setSubTab('group')}
          className={`shrink-0 whitespace-nowrap rounded-md px-4 py-1.5 text-xs font-semibold transition ${
            subTab === 'group'
              ? 'bg-white text-blue-700 shadow-sm font-medium'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          {t('research.group.groupMilestones')}
        </button>
      </div>

      {subTab === 'me' ? (
        <MilestoneList
          key={`my-milestones-me-${groupId}`}
          projectId={projectId}
          labId={labId}
          canCreate={false}
          groupId={groupId}
          groupRole="MEMBER"
          emptyMessage={t('research.group.myMilestonesEmpty')}
        />
      ) : (
        <MilestoneList
          key={`group-milestones-all-${groupId}`}
          projectId={projectId}
          labId={labId}
          canCreate={false}
          groupId={groupId}
          groupRole="LEADER"
          emptyMessage={t('research.group.noMilestones')}
        />
      )}
    </div>
  );
}


function StatItem({
  title,
  value,
}: {
  title: string;
  value: string | number;
}) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div>
        <span className="block text-xs font-semibold text-slate-500 tracking-normal dark:text-slate-400">
          {title}
        </span>
        <span className="block mt-1 text-2xl font-medium text-slate-900 leading-none">
          {value}
        </span>
      </div>
    </div>
  );
}
