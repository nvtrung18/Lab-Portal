import { useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';

import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
import { getManagedLabId } from '../../../shared/utils/membership';
import { useCurrentUser } from '../../user/hooks';
import { MilestoneList, ResearchGroupList } from '../components';
import { useResearchProject } from '../hooks';
import { formatDate, formatPriority, formatProjectStatus, getStatusClass } from '../utils';

export function ResearchProjectDetailPage() {
  const [managerTab, setManagerTab] = useState<'groups' | 'milestones'>('groups');
  const { projectId } = useParams();
  const numericProjectId = Number(projectId);
  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const roles = currentUser?.roles.map((role) => role.replace(/^ROLE_/, '')) ?? [];
  const isManager = roles.includes(LAB_MANAGER);
  const isStudent = roles.includes(STUDENT);
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

  if (!isManager && !isStudent) {
    return <Navigate to="/403" replace />;
  }

  if (isError || !project) {
    return (
      <section className="rounded-lg border border-red-200 bg-red-50 p-6 text-sm text-red-700">
        Không thể tải chi tiết đề tài.
        <button className="ml-3 font-semibold underline" type="button" onClick={() => refetch()}>
          Tải lại
        </button>
      </section>
    );
  }

  if (isManager && (!managedLabId || project.labId !== managedLabId)) {
    return <Navigate to="/403" replace />;
  }

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <Link
          className="inline-flex rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
          to="/app/research"
        >
          Quay lại danh sách đề tài
        </Link>
        <div className="mt-4 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <h2 className="text-xl font-semibold text-slate-950">
              {project.code ? `${project.code} - ` : ''}
              {project.title}
            </h2>
            <p className="mt-2 text-sm text-slate-600">
              {project.objective || project.description || 'Chưa cập nhật mục tiêu nghiên cứu.'}
            </p>
          </div>
          <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(project.status)}`}>
            {formatProjectStatus(project.status)}
          </span>
        </div>
        <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <dt className="font-semibold text-slate-700">Hướng nghiên cứu</dt>
            <dd className="mt-1 text-slate-600">{project.researchDirection ?? 'Chưa cập nhật'}</dd>
          </div>
          <div>
            <dt className="font-semibold text-slate-700">Mức độ ưu tiên</dt>
            <dd className="mt-1 text-slate-600">{formatPriority(project.priority)}</dd>
          </div>
          <div>
            <dt className="font-semibold text-slate-700">Ngày bắt đầu</dt>
            <dd className="mt-1 text-slate-600">{formatDate(project.startDate)}</dd>
          </div>
          <div>
            <dt className="font-semibold text-slate-700">Ngày kết thúc dự kiến</dt>
            <dd className="mt-1 text-slate-600">{formatDate(project.expectedEndDate ?? project.endDate)}</dd>
          </div>
        </dl>
      </div>

      {isManager ? (
        <>
          <div className="flex gap-1 border-b border-slate-200" role="tablist" aria-label="Chi tiết đề tài nghiên cứu">
            <button
              className={`border-b-2 px-4 py-3 text-sm font-semibold ${
                managerTab === 'groups' ? 'border-slate-900 text-slate-950' : 'border-transparent text-slate-600'
              }`}
              type="button"
              role="tab"
              aria-selected={managerTab === 'groups'}
              onClick={() => setManagerTab('groups')}
            >
              Nhóm nghiên cứu
            </button>
            <button
              className={`border-b-2 px-4 py-3 text-sm font-semibold ${
                managerTab === 'milestones' ? 'border-slate-900 text-slate-950' : 'border-transparent text-slate-600'
              }`}
              type="button"
              role="tab"
              aria-selected={managerTab === 'milestones'}
              onClick={() => setManagerTab('milestones')}
            >
              Mốc nghiên cứu
            </button>
          </div>

          {managerTab === 'groups' ? (
            <ResearchGroupList project={project} canCreate />
          ) : (
            <MilestoneList
              projectId={project.id}
              labId={project.labId}
              canCreate
              emptyMessage="Đề tài này chưa có mốc nghiên cứu nào."
            />
          )}
        </>
      ) : (
        <MilestoneList
          projectId={project.id}
          canCreate={false}
          emptyMessage="Đề tài này chưa có mốc nghiên cứu nào."
        />
      )}
    </section>
  );
}
