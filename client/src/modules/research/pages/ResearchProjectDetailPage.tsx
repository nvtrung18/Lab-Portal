import { useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';

import { ErrorState, LoadingState } from '../../../shared/components';
import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
import { getManagedLabId } from '../../../shared/utils/membership';
import { useCurrentUser } from '../../user/hooks';
import { DashboardPage, ProjectTaskBoard, ResearchGroupList, TaskProposalPanel } from '../components';
import { useResearchProject, useResearchGroupsByProject } from '../hooks';
import { formatDate, formatPriority, formatProjectStatus, getStatusClass } from '../utils';

type ProjectDetailTab = 'dashboard' | 'tasks' | 'groups' | 'proposals';

const DETAIL_TABS: Array<{ value: ProjectDetailTab; label: string }> = [
  { value: 'tasks', label: 'Task board' },
  { value: 'dashboard', label: 'Tổng quan NCKH' },
  { value: 'groups', label: 'Nhóm nghiên cứu' },
  { value: 'proposals', label: 'Task proposals' },
];

export function ResearchProjectDetailPage() {
  const [activeTab, setActiveTab] = useState<ProjectDetailTab>('dashboard');
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

      {/* Student Group Banner */}
      {isStudent && studentGroup && (
        <div className="flex flex-col gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 shadow-sm sm:flex-row sm:items-center sm:justify-between">
          <div className="min-w-0">
            <div>
              <h4 className="font-semibold text-amber-900">
                Bạn thuộc nhóm nghiên cứu: <span className="font-medium">{studentGroup.name}</span>
              </h4>
              <p className="text-xs text-amber-700">
                Truy cập vào trang chi tiết nhóm của bạn để xem mốc công việc, sản phẩm và nhật ký cá nhân.
              </p>
            </div>
          </div>
          <Link
            to={`/app/research/projects/${project.id}/groups/${studentGroupId}`}
            className="w-fit shrink-0 rounded-lg bg-amber-600 px-4 py-2 text-center text-sm font-semibold text-white transition-colors hover:bg-amber-700 shadow-sm"
          >
            Vào chi tiết nhóm của tôi
          </Link>
        </div>
      )}

      {/* Student Warning If Not In Any Group */}
      {isStudent && !studentGroup && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-center shadow-sm">
          <h4 className="font-semibold text-red-900">{studentGroups.length > 1 ? 'Không thể xác định nhóm hiện tại' : 'Bạn chưa được phân vào nhóm nghiên cứu của đề tài này'}</h4>
          <p className="mt-1 text-sm text-red-700">{studentGroups.length > 1 ? 'Vui lòng chọn nhóm cụ thể trước khi gửi đề xuất.' : 'Vui lòng liên hệ với Lab Manager để được thêm vào nhóm.'}</p>
        </div>
      )}

      <div className="flex gap-1 overflow-x-auto border-b border-slate-200" role="tablist" aria-label="Chi tiết đề tài nghiên cứu">
        {DETAIL_TABS.map((tab) => (
          <button
            className={`whitespace-nowrap border-b-2 px-4 py-3 text-sm font-semibold ${
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
