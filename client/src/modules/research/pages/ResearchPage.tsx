import { Navigate } from 'react-router-dom';

import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
import { getManagedLabId, getManagedLabName } from '../../../shared/utils/membership';
import { MyResearchGroups, ResearchProjectList } from '../components';
import { useCurrentUser } from '../../user/hooks';

export function ResearchPage() {
  const { data: currentUser, isLoading } = useCurrentUser();
  const roles = currentUser?.roles.map((role) => role.replace(/^ROLE_/, '')) ?? [];
  const isManager = roles.includes(LAB_MANAGER);
  const isStudent = roles.includes(STUDENT);
  const managedLabId = getManagedLabId(currentUser);
  const managedLabName = getManagedLabName(currentUser);

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
    return <MyResearchGroups currentUserId={currentUser?.id} />;
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

      <ResearchProjectList labId={managedLabId} canCreate={isManager} />
    </section>
  );
}
