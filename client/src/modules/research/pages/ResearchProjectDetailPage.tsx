import { useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';

import { ErrorState, LoadingState } from '../../../shared/components';
import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
import { useI18n } from '../../../shared/i18n';
import { getManagedLabId } from '../../../shared/utils/membership';
import { useCurrentUser } from '../../user/hooks';
import { DashboardPage, ProjectTaskBoard, ResearchGroupList, TaskProposalPanel } from '../components';
import { useResearchProject, useResearchGroupsByProject } from '../hooks';
import { formatDate, formatPriority, formatProjectStatus, getStatusClass } from '../utils';

type ProjectDetailTab = 'dashboard' | 'tasks' | 'groups' | 'proposals';

export function ResearchProjectDetailPage() {
  const { locale, t } = useI18n();
  const [activeTab, setActiveTab] = useState<ProjectDetailTab>('dashboard');
  const detailTabs: Array<{ value: ProjectDetailTab; label: string }> = [
    { value: 'tasks', label: t('research.tab.taskBoard') },
    { value: 'dashboard', label: t('research.tab.overview') },
    { value: 'groups', label: t('research.tab.projectGroups') },
    { value: 'proposals', label: t('research.tab.proposals') },
  ];
  const { projectId } = useParams();
  const numericProjectId = Number(projectId);
  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const roles = currentUser?.roles.map((role) => role.replace(/^ROLE_/, '')) ?? [];
  const isManager = roles.includes(LAB_MANAGER);
  const isStudent = roles.includes(STUDENT);
  const managedLabId = getManagedLabId(currentUser);
  const { data: project, isError, isLoading, refetch } = useResearchProject(Number.isFinite(numericProjectId) ? numericProjectId : null);
  const { data: groups = [], isLoading: isLoadingGroups } = useResearchGroupsByProject(Number.isFinite(numericProjectId) ? numericProjectId : null);

  if (!Number.isFinite(numericProjectId)) {
    return <Navigate to="/app/research" replace />;
  }

  if (isLoadingUser || isLoading || isLoadingGroups) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <LoadingState />
      </section>
    );
  }

  if (!isManager && !isStudent) {
    return <Navigate to="/403" replace />;
  }

  if (isError || !project) {
    return (
      <section className="rounded-lg border border-red-200 bg-white p-6 shadow-sm">
        <ErrorState onRetry={() => refetch()} />
      </section>
    );
  }

  if (isManager && (!managedLabId || project.labId !== managedLabId)) {
    return <Navigate to="/403" replace />;
  }

  // Resolve student group in this project
  const studentGroups = groups.filter((g) => g.members?.some((m) => m.userId === currentUser?.id));
  const studentGroup = studentGroups.length === 1 ? studentGroups[0] : undefined;
  const studentGroupId = studentGroup?.id ?? null;
  const proposalGroupScope = groups.flatMap((group) => {
    const memberRole = group.members?.find((member) => member.userId === currentUser?.id)?.role
      ?? group.myRole
      ?? group.myGroupRole;
    return memberRole ? [{ groupId: group.id, role: memberRole }] : [];
  });

  return (
    <section className="min-w-0 space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
        <Link
          className="inline-flex min-h-11 items-center rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
          to="/app/research"
        >
          {t('common.backToList')}
        </Link>
        <div className="mt-4 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <h2 className="break-words text-xl font-semibold text-slate-950">
              {project.code ? `${project.code} - ` : ''}
              {project.title}
            </h2>
            <p className="mt-2 break-words text-sm text-slate-600">
              {project.objective || project.description || t('common.notUpdated')}
            </p>
          </div>
          <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(project.status)}`}>
            {formatProjectStatus(project.status, locale)}
          </span>
        </div>
        <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <dt className="font-semibold text-slate-700">{t('research.project.researchDirection')}</dt>
            <dd className="mt-1 text-slate-600">{project.researchDirection ?? t('common.notUpdated')}</dd>
          </div>
          <div>
            <dt className="font-semibold text-slate-700">{t('research.project.priority')}</dt>
            <dd className="mt-1 text-slate-600">{formatPriority(project.priority, locale)}</dd>
          </div>
          <div>
            <dt className="font-semibold text-slate-700">{t('research.project.startDate')}</dt>
            <dd className="mt-1 text-slate-600">{formatDate(project.startDate, locale)}</dd>
          </div>
          <div>
            <dt className="font-semibold text-slate-700">{t('research.project.expectedEndDate')}</dt>
            <dd className="mt-1 text-slate-600">{formatDate(project.expectedEndDate ?? project.endDate, locale)}</dd>
          </div>
        </dl>
      </div>

      {/* Student Group Banner */}
      {isStudent && studentGroup && (
        <div className="flex flex-col gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 shadow-sm sm:flex-row sm:items-center sm:justify-between">
          <div className="min-w-0">
            <div>
              <h4 className="font-semibold text-amber-900">
                {t('research.project.studentGroup')}: <span className="font-medium">{studentGroup.name}</span>
              </h4>
            </div>
          </div>
          <Link
            to={`/app/research/projects/${project.id}/groups/${studentGroupId}`}
            className="inline-flex min-h-11 max-w-full items-center justify-center rounded-lg bg-amber-600 px-4 py-2 text-center text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-700"
          >
            {t('research.project.openGroup')}
          </Link>
        </div>
      )}

      {/* Student Warning If Not In Any Group */}
      {isStudent && !studentGroup && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-center shadow-sm">
          <h4 className="font-semibold text-red-900">{studentGroups.length > 1 ? t('research.project.groupAmbiguous') : t('research.project.groupMissing')}</h4>
          <p className="mt-1 text-sm text-red-700">{studentGroups.length > 1 ? t('research.project.chooseGroup') : t('research.project.contactManager')}</p>
        </div>
      )}

      <div className="research-detail-tabs research-project-tabs gap-1" role="tablist" aria-label={t('research.title')}>
        {detailTabs.map((tab) => (
          <button
            className={`shrink-0 whitespace-nowrap border-b-2 px-4 py-3 text-sm font-semibold ${
              activeTab === tab.value ? 'border-slate-900 text-slate-950' : 'border-transparent text-slate-600'
            }`}
            key={tab.value}
            type="button"
            role="tab"
            aria-selected={activeTab === tab.value}
            onClick={() => setActiveTab(tab.value)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab Contents */}
      {activeTab === 'dashboard' ? (
        <DashboardPage
          key={`dashboard-${project.id}`}
          currentUser={currentUser}
          projectId={project.id}
          role={isManager ? LAB_MANAGER : STUDENT}
          groupRole={null}
        />
      ) : null}

      {activeTab === 'tasks' ? <ProjectTaskBoard projectId={project.id} /> : null}

      {activeTab === 'groups' ? (
        <ResearchGroupList key={`groups-${project.id}`} project={project} canCreate={isManager} />
      ) : null}

      {activeTab === 'proposals' ? (
        <TaskProposalPanel
          projectId={project.id}
          groupId={studentGroupId}
          groupScope={proposalGroupScope}
          canSubmit={isStudent}
        />
      ) : null}
    </section>
  );
}
