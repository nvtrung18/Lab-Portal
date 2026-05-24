import { Link, Navigate, useParams } from 'react-router-dom';

import { LAB_MANAGER } from '../../../shared/constants/roles';
import { getManagedLabId } from '../../../shared/utils/membership';
import { useCurrentUser } from '../../user/hooks';
import { ResearchGroupList } from '../components';
import { useResearchProject } from '../hooks';
import { formatDate, formatPriority, formatProjectStatus, getStatusClass } from '../utils';

export function ResearchProjectDetailPage() {
  const { projectId } = useParams();
  const numericProjectId = Number(projectId);
  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const roles = currentUser?.roles.map((role) => role.replace(/^ROLE_/, '')) ?? [];
  const isManager = roles.includes(LAB_MANAGER);
  const managedLabId = getManagedLabId(currentUser);
  const { data: project, isError, isLoading, refetch } = useResearchProject(Number.isFinite(numericProjectId) ? numericProjectId : null);

  if (!Number.isFinite(numericProjectId)) {
    return <Navigate to="/app/research" replace />;
  }

  if (isLoadingUser || isLoading) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="h-6 w-64 animate-pulse rounded bg-slate-200" />
        <div className="mt-6 h-48 animate-pulse rounded bg-slate-100" />
      </section>
    );
  }

  if (!isManager) {
    return <Navigate to="/403" replace />;
  }

  if (isError || !project) {
    return (
      <section className="rounded-lg border border-red-200 bg-red-50 p-6 text-sm text-red-700">
        Khong the tai chi tiet de tai.
        <button className="ml-3 font-semibold underline" type="button" onClick={() => refetch()}>
          Tai lai
        </button>
      </section>
    );
  }

  if (!managedLabId || project.labId !== managedLabId) {
    return <Navigate to="/403" replace />;
  }

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <Link className="text-sm font-semibold text-slate-600 hover:text-slate-950" to="/app/research">
          Quay lai danh sach de tai
        </Link>
        <div className="mt-4 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <h2 className="text-xl font-semibold text-slate-950">
              {project.code ? `${project.code} - ` : ''}
              {project.title}
            </h2>
            <p className="mt-2 text-sm text-slate-600">
              {project.objective || project.description || 'Chua cap nhat muc tieu nghien cuu.'}
            </p>
          </div>
          <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(project.status)}`}>
            {formatProjectStatus(project.status)}
          </span>
        </div>
        <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <dt className="font-semibold text-slate-700">Huong nghien cuu</dt>
            <dd className="mt-1 text-slate-600">{project.researchDirection ?? 'Chua cap nhat'}</dd>
          </div>
          <div>
            <dt className="font-semibold text-slate-700">Uu tien</dt>
            <dd className="mt-1 text-slate-600">{formatPriority(project.priority)}</dd>
          </div>
          <div>
            <dt className="font-semibold text-slate-700">Bat dau</dt>
            <dd className="mt-1 text-slate-600">{formatDate(project.startDate)}</dd>
          </div>
          <div>
            <dt className="font-semibold text-slate-700">Ket thuc du kien</dt>
            <dd className="mt-1 text-slate-600">{formatDate(project.expectedEndDate ?? project.endDate)}</dd>
          </div>
        </dl>
      </div>

      <ResearchGroupList project={project} canCreate={isManager} />
    </section>
  );
}
