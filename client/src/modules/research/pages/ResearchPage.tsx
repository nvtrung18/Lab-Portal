import { useEffect, useMemo, useState } from 'react';
import { Navigate } from 'react-router-dom';

import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
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
        Bạn chưa được phân công quản lý phòng thí nghiệm nào.
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <h2 className="text-xl font-semibold text-slate-950">Nghiên cứu khoa học</h2>
            <p className="mt-2 text-sm text-slate-600">
              Quản lý đề tài nghiên cứu trong PTN.
            </p>
          </div>
          <div className="rounded-md bg-slate-100 px-3 py-2 text-sm font-medium text-slate-700">
            {managedLabName ?? `PTN #${managedLabId}`}
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
    <section className="space-y-6">
      <header className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold text-slate-950">Nghiên cứu khoa học</h2>
        <p className="mt-2 text-sm text-slate-600">
          Theo dõi đề tài và nhóm nghiên cứu trong các PTN bạn tham gia.
        </p>
      </header>

      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <label className="block max-w-sm text-sm font-semibold text-slate-700">
          PTN đang chọn
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
        <p className="mt-3 text-sm text-slate-600">Chỉ hiển thị dữ liệu NCKH của PTN bạn đang chọn.</p>
      </section>

      <div className="flex gap-1 border-b border-slate-200" role="tablist" aria-label="Nghiên cứu khoa học">
        <button
          className={`border-b-2 px-4 py-3 text-sm font-semibold ${
            tab === 'projects' ? 'border-slate-900 text-slate-950' : 'border-transparent text-slate-600'
          }`}
          type="button"
          role="tab"
          aria-selected={tab === 'projects'}
          onClick={() => setTab('projects')}
        >
          NCKH trong PTN
        </button>
        <button
          className={`border-b-2 px-4 py-3 text-sm font-semibold ${
            tab === 'groups' ? 'border-slate-900 text-slate-950' : 'border-transparent text-slate-600'
          }`}
          type="button"
          role="tab"
          aria-selected={tab === 'groups'}
          onClick={() => setTab('groups')}
        >
          Nhóm của tôi
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
