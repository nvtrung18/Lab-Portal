import { useEffect, useMemo, useState } from 'react';
import { Navigate } from 'react-router-dom';

import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
import { useI18n } from '../../../shared/i18n';
import {
  getActiveMemberships,
  getManagedLabId,
  getManagedLabName,
  getMembershipLabId,
  getMembershipLabName,
} from '../../../shared/utils/membership';
import { MyResearchGroups, ResearchProjectList } from '../components';
import { useCurrentUser } from '../../user/hooks';

export function ResearchPage() {
  const { t } = useI18n();
  const { data: currentUser, isLoading } = useCurrentUser();
  const roles = currentUser?.roles.map((role) => role.replace(/^ROLE_/, '')) ?? [];
  const isManager = roles.includes(LAB_MANAGER);
  const isStudent = roles.includes(STUDENT);
  const managedLabId = getManagedLabId(currentUser);
  const managedLabName = getManagedLabName(currentUser);
  const activeMemberships = useMemo(() => getActiveMemberships(currentUser), [currentUser]);

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="h-6 w-52 animate-pulse rounded bg-slate-200" />
        <div className="mt-6 h-40 animate-pulse rounded bg-slate-100" />
      </section>
    );
  }

  if (!isManager && !isStudent) {
    return <Navigate to="/403" replace />;
  }

  if (isStudent && !isManager) {
    return <StudentResearchView currentUserId={currentUser?.id} activeMemberships={activeMemberships} />;
  }

  if (!managedLabId) {
    return (
      <section className="rounded-lg border border-amber-200 bg-white p-6 text-sm text-amber-700 shadow-sm">
        {t('layout.unassignedLab')}
      </section>
    );
  }

  return (
    <section className="min-w-0 space-y-6">
      <div className="app-page-heading rounded-lg border border-slate-200 bg-white p-4 sm:p-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <h2 className="text-xl font-semibold text-slate-950">{t('research.title')}</h2>
          </div>
          <div className="max-w-full break-words rounded-md bg-slate-100 px-3 py-2 text-sm font-medium text-slate-700">
            {managedLabName ?? t('research.labNumber', undefined, { id: managedLabId })}
          </div>
        </div>
      </div>

      <ResearchProjectList labId={managedLabId} canCreate={isManager} mode="manager" />
    </section>
  );
}

interface StudentResearchViewProps {
  currentUserId?: number | null;
  activeMemberships: ReturnType<typeof getActiveMemberships>;
}

function StudentResearchView({ currentUserId, activeMemberships }: StudentResearchViewProps) {
  const { t } = useI18n();
  const [tab, setTab] = useState<'projects' | 'groups'>('projects');
  const [selectedLabId, setSelectedLabId] = useState<number | null>(
    activeMemberships[0] ? getMembershipLabId(activeMemberships[0]) : null,
  );

  useEffect(() => {
    if (!activeMemberships.some((membership) => getMembershipLabId(membership) === selectedLabId)) {
      setSelectedLabId(activeMemberships[0] ? getMembershipLabId(activeMemberships[0]) : null);
    }
  }, [activeMemberships, selectedLabId]);

  if (!activeMemberships.length || !selectedLabId) {
    return <Navigate to="/app/labs" replace />;
  }

  return (
    <section className="min-w-0 space-y-6">
      <header className="app-page-heading rounded-lg border border-slate-200 bg-white p-4 sm:p-6">
        <h2 className="text-xl font-semibold text-slate-950">{t('research.title')}</h2>
      </header>

      <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
        <label className="block max-w-sm text-sm font-semibold text-slate-700">
          {t('research.selectedLab')}
          <select
            className="mt-2 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-normal text-slate-900"
            value={selectedLabId}
            onChange={(event) => setSelectedLabId(Number(event.target.value))}
          >
            {activeMemberships.map((membership) => {
              const labId = getMembershipLabId(membership);
              return labId ? (
                <option key={labId} value={labId}>
                  {getMembershipLabName(membership)}
                </option>
              ) : null;
            })}
          </select>
        </label>
      </section>

      <div className="flex max-w-full gap-1 overflow-x-auto overscroll-x-contain" role="tablist" aria-label={t('research.title')}>
        <button
          className={`shrink-0 whitespace-nowrap border-b-2 px-4 py-3 text-sm font-semibold ${
            tab === 'projects' ? 'border-slate-900 text-slate-950' : 'border-transparent text-slate-600'
          }`}
          type="button"
          role="tab"
          aria-selected={tab === 'projects'}
          onClick={() => setTab('projects')}
        >
          {t('research.tab.projects')}
        </button>
        <button
          className={`shrink-0 whitespace-nowrap border-b-2 px-4 py-3 text-sm font-semibold ${
            tab === 'groups' ? 'border-slate-900 text-slate-950' : 'border-transparent text-slate-600'
          }`}
          type="button"
          role="tab"
          aria-selected={tab === 'groups'}
          onClick={() => setTab('groups')}
        >
          {t('research.tab.groups')}
        </button>
      </div>

      {tab === 'projects' ? (
        <ResearchProjectList labId={selectedLabId} canCreate={false} mode="student" />
      ) : (
        <MyResearchGroups labId={selectedLabId} currentUserId={currentUserId} />
      )}
    </section>
  );
}
