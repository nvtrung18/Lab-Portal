import { useEffect, useState, useMemo } from 'react';
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom';
import axios from 'axios';

import { EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
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
  useResearchGroupsByProject,
  useMilestonesByGroup,
  useMyMilestonesByGroup,
  useGroupTasks,
  useGroupReports,
  useMyGroupReports,
  useProductsByGroup,
  useProjectDashboardStats,
} from '../hooks';
import type { TaskBoardRole } from '../taskBoardHelpers';
import { formatDate, getApiErrorMessage } from '../utils';

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

const GROUP_DETAIL_MANAGER_TABS: Array<{ value: GroupDetailTab; label: string }> = [
  { value: 'overview', label: 'Tổng quan nhóm' },
  { value: 'members', label: 'Thành viên nhóm' },
  { value: 'project', label: 'Đề tài' },
  { value: 'milestones', label: 'Mốc nghiên cứu' },
  { value: 'tasks', label: 'Nhiệm vụ' },
  { value: 'reports', label: 'Báo cáo nhóm' },
  { value: 'products', label: 'Sản phẩm nhóm' },
  { value: 'evaluations', label: 'Đánh giá' },
  { value: 'logs', label: 'Nhật ký nghiên cứu' },
];

const GROUP_DETAIL_STUDENT_TABS = (isLeader: boolean): Array<{ value: GroupDetailTab; label: string }> => [
  { value: 'overview', label: 'Thông tin nhóm' },
  { value: 'members', label: 'Thành viên nhóm' },
  { value: 'project', label: 'Đề tài' },
  { value: 'milestones', label: 'Mốc nghiên cứu' },
  { value: 'tasks', label: 'Nhiệm vụ' },
  { value: 'reports', label: 'Báo cáo nhóm' },
  { value: 'products', label: 'Sản phẩm nhóm' },
  ...(isLeader ? [{ value: 'logs', label: 'Nhật ký nhóm' } as const] : []),
];

const GROUP_DETAIL_TAB_KEYS = GROUP_DETAIL_MANAGER_TABS.map((tab) => tab.value);

function isGroupDetailTab(value: string | null): value is GroupDetailTab {
  return GROUP_DETAIL_TAB_KEYS.includes(value as GroupDetailTab);
}

export function ResearchGroupDetailPage() {
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
  const { data: groups = [], isLoading: isLoadingGroups } = useResearchGroupsByProject(
    Number.isFinite(numericProjectId) ? numericProjectId : null
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

  if (isLoadingUser || isLoadingProject || isLoadingGroup || isLoadingGroups) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <LoadingState>Đang tải thông tin nhóm nghiên cứu...</LoadingState>
      </section>
    );
  }

  if (!isManager && !isStudent) {
    return <Navigate to="/403" replace />;
  }

  if (isProjectError || isGroupError || !project || !group) {
    let errorMessage = 'Không thể tải thông tin nhóm nghiên cứu.';
    if (isGroupError && axios.isAxiosError(groupError)) {
      const status = groupError.response?.status;
      if (status === 403) {
        errorMessage = 'Bạn không có quyền truy cập nhóm nghiên cứu này.';
      } else if (status === 404) {
        errorMessage = 'Không tìm thấy nhóm nghiên cứu.';
      }
    } else if (isProjectError && axios.isAxiosError(projectError)) {
      const status = projectError.response?.status;
      if (status === 403) {
        errorMessage = 'Bạn không có quyền truy cập đề tài này.';
      } else if (status === 404) {
        errorMessage = 'Không tìm thấy đề tài nghiên cứu.';
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
    <section className="space-y-6">
      {/* Premium Header */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-4">
          {isStudent ? (
            <Link
              className="inline-flex rounded-md border border-slate-200 bg-white px-3.5 py-2 text-sm font-bold text-slate-700 hover:bg-slate-50 transition duration-150"
              to="/app/research"
            >
              Quay lại danh sách nhóm
            </Link>
          ) : (
            <Link
              className="inline-flex rounded-md border border-slate-200 bg-white px-3.5 py-2 text-sm font-bold text-slate-700 hover:bg-slate-50 transition duration-150"
              to={`/app/research/projects/${project.id}`}
            >
              Quay lại đề tài
            </Link>
          )}
          <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Đề tài: {project.title}</span>
        </div>

        <div className="mt-4 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="flex items-center gap-2.5">
              <h2 className="text-2xl font-bold text-slate-950">{group.name}</h2>
              <span className="rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-bold text-blue-700 ring-1 ring-blue-100">
                Nhóm nghiên cứu
              </span>
            </div>
            <p className="mt-2 text-sm text-slate-600 leading-relaxed max-w-4xl">
              {group.objective || 'Chưa cập nhật mục tiêu của nhóm nghiên cứu.'}
            </p>
          </div>
        </div>

        <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-5 border-t border-slate-100 pt-5">
          <div className="min-w-0 flex-1">
            <dt className="font-semibold text-slate-500 text-xs uppercase tracking-wider">Đề tài nghiên cứu</dt>
            <dd className="mt-1 text-slate-800 font-bold truncate leading-snug" title={project.title}>
              {project.title}
            </dd>
          </div>
          <div>
            <dt className="font-semibold text-slate-500 text-xs uppercase tracking-wider">Trưởng nhóm</dt>
            <dd className="mt-1 text-slate-800 font-bold">{group.leaderName || 'Chưa phân công'}</dd>
          </div>
          {isStudent ? (
            <div>
              <dt className="font-semibold text-slate-500 text-xs uppercase tracking-wider">Vai trò của tôi</dt>
              <dd className="mt-1">
                <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-bold ring-1 ${
                  isLeader ? 'bg-blue-50 text-blue-700 ring-blue-100' : 'bg-slate-50 text-slate-700 ring-slate-100'
                }`}>
                  {isLeader ? 'Trưởng nhóm' : 'Thành viên'}
                </span>
              </dd>
            </div>
          ) : (
            <div>
              <dt className="font-semibold text-slate-500 text-xs uppercase tracking-wider">Thành viên</dt>
              <dd className="mt-1 text-slate-800 font-medium">{group.members?.length ?? 0} học viên</dd>
            </div>
          )}
          <div>
            <dt className="font-semibold text-slate-500 text-xs uppercase tracking-wider">Trạng thái nhóm</dt>
            <dd className="mt-1">
              <span className="inline-flex rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-bold text-emerald-700 ring-1 ring-emerald-100">
                Hoạt động
              </span>
            </dd>
          </div>
          <div>
            <dt className="font-semibold text-slate-500 text-xs uppercase tracking-wider">Ngày tạo nhóm</dt>
            <dd className="mt-1 text-slate-800 font-medium">{formatDate(group.createdAt)}</dd>
          </div>
        </dl>
      </div>

      {/* Tabs list */}
      <div className="flex gap-1 overflow-x-auto border-b border-slate-200" role="tablist" aria-label="Quản lý chi tiết nhóm nghiên cứu">
        {tabs.map((tab) => (
          <button
            className={`whitespace-nowrap border-b-2 px-4 py-3 text-sm font-semibold transition-all ${
              activeTab === tab.value
                ? 'border-blue-600 text-blue-600 font-bold'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
            key={tab.value}
            type="button"
            role="tab"
            aria-selected={activeTab === tab.value}
            onClick={() => handleTabChange(tab.value)}
          >
            {tab.label}
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
            emptyMessage="Nhóm chưa có mốc nghiên cứu nào."
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

      {activeTab === 'evaluations' && isManager ? (
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
          title="Mốc hoàn thành"
          value={`${completedMilestones}/${milestones.length}`}
          desc="Các cột mốc nghiên cứu chính"
        />
        <StatItem
          title="Tiến độ Task"
          value={`${taskCompletionRate}%`}
          desc="Phần trăm nhiệm vụ đã hoàn thành"
        />
        <StatItem
          title="Báo cáo đã nộp"
          value={reports.length}
          desc="Tổng số báo cáo tiến độ"
        />
        <StatItem
          title="Sản phẩm nghiên cứu"
          value={products.length}
          desc="Các sản phẩm khoa học đã lưu"
        />
      </div>

      {isStudent && (
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm space-y-4">
          <h3 className="text-base font-bold text-slate-900 border-b border-slate-100 pb-3">Chi tiết thông tin nhóm</h3>
          <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 text-sm">
            <div className="bg-slate-50/50 rounded-lg p-3.5 border border-slate-100 space-y-0.5">
              <dt className="font-semibold text-slate-500 text-xs uppercase tracking-wider">Tên nhóm</dt>
              <dd className="font-bold text-slate-900 text-base">{group.name}</dd>
            </div>
            <div className="bg-slate-50/50 rounded-lg p-3.5 border border-slate-100 space-y-0.5">
              <dt className="font-semibold text-slate-500 text-xs uppercase tracking-wider">Trưởng nhóm</dt>
              <dd className="font-bold text-slate-800">{group.leaderName || 'Chưa phân công'}</dd>
            </div>
            <div className="bg-slate-50/50 rounded-lg p-3.5 border border-slate-100 space-y-0.5">
              <dt className="font-semibold text-slate-500 text-xs uppercase tracking-wider">Vai trò của tôi</dt>
              <dd className="font-bold text-slate-800">
                <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-bold ring-1 ${
                  isLeader ? 'bg-blue-50 text-blue-700 ring-blue-100' : 'bg-slate-50 text-slate-700 ring-slate-100'
                }`}>
                  {isLeader ? 'Trưởng nhóm' : 'Thành viên'}
                </span>
              </dd>
            </div>
            <div className="bg-slate-50/50 rounded-lg p-3.5 border border-slate-100 space-y-0.5">
              <dt className="font-semibold text-slate-500 text-xs uppercase tracking-wider">Trạng thái nhóm</dt>
              <dd>
                <span className="inline-flex rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-bold text-emerald-700 ring-1 ring-emerald-100">
                  Hoạt động
                </span>
              </dd>
            </div>
            <div className="bg-slate-50/50 rounded-lg p-3.5 border border-slate-100 space-y-0.5">
              <dt className="font-semibold text-slate-500 text-xs uppercase tracking-wider">Ngày tạo nhóm</dt>
              <dd className="font-bold text-slate-800">{formatDate(group.createdAt)}</dd>
            </div>
          </dl>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Plan / Descriptions Card */}
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm lg:col-span-2 space-y-4">
          <div>
            <h3 className="text-base font-bold text-slate-900">Kế hoạch nghiên cứu của nhóm</h3>
            <p className="mt-2 text-sm text-slate-600 leading-relaxed whitespace-pre-line">
              {group.plan || 'Chưa có kế hoạch nghiên cứu chi tiết.'}
            </p>
          </div>
          <div className="border-t border-slate-100 pt-4">
            <h3 className="text-base font-bold text-slate-900">Mô tả chi tiết nhóm</h3>
            <p className="mt-2 text-sm text-slate-600 leading-relaxed">
              {group.description || 'Chưa cập nhật mô tả nhóm.'}
            </p>
          </div>
        </div>

        {/* Members List Card */}
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-base font-bold text-slate-900">Thành viên nhóm ({group.members?.length ?? 0})</h3>
          <div className="mt-4 divide-y divide-slate-100">
            {group.members?.map((member: any) => (
              <div key={member.id} className="py-3 flex items-center justify-between gap-3 text-sm">
                <div>
                  <span className="block font-semibold text-slate-900">{member.fullName || member.email}</span>
                  <span className="block text-xs text-slate-500">{member.email}</span>
                </div>
                <span className={`rounded-full px-2 py-0.5 text-xs font-bold ring-1 ${
                  member.role === 'LEADER'
                    ? 'bg-blue-50 text-blue-700 ring-blue-100'
                    : 'bg-slate-50 text-slate-700 ring-slate-100'
                }`}>
                  {member.role === 'LEADER' ? 'Trưởng nhóm' : 'Thành viên'}
                </span>
              </div>
            ))}
            {!group.members?.length && (
              <p className="py-3 text-sm text-slate-500 text-center">Chưa có thành viên nào.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

// 2. Group Members Tab Subcomponent
function GroupMembersTab({ group, isLeader }: { group: any; isLeader: boolean }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm space-y-6">
      <div>
        <h3 className="text-lg font-bold text-slate-950">Thành viên nhóm</h3>
        <p className="mt-1 text-sm text-slate-600">
          Danh sách thành viên chính thức tham gia nhóm nghiên cứu.
        </p>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm border-collapse">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase tracking-wider text-slate-500">
              <th className="px-6 py-3 font-semibold">Họ tên</th>
              <th className="px-6 py-3 font-semibold">Email</th>
              <th className="px-6 py-3 font-semibold">Vai trò trong nhóm</th>
              <th className="px-6 py-3 font-semibold">Trạng thái</th>
              <th className="px-6 py-3 font-semibold">Ngày tham gia</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {group.members?.map((member: any) => (
              <tr key={member.id} className="hover:bg-slate-50/55 transition duration-150">
                <td className="px-6 py-4 font-semibold text-slate-900">
                  {member.fullName || 'Chưa cập nhật'}
                </td>
                <td className="px-6 py-4 text-slate-600">{member.email}</td>
                <td className="px-6 py-4">
                  <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-bold ring-1 ${
                    member.role === 'LEADER'
                      ? 'bg-blue-50 text-blue-700 ring-blue-100'
                      : 'bg-slate-50 text-slate-700 ring-slate-100'
                  }`}>
                    {member.role === 'LEADER' ? 'Trưởng nhóm' : 'Thành viên'}
                  </span>
                </td>
                <td className="px-6 py-4">
                  <span className="inline-flex text-emerald-700 font-semibold">
                    Hoạt động
                  </span>
                </td>
                <td className="px-6 py-4 text-slate-500">
                  {formatDate(member.joinedAt || group.createdAt)}
                </td>
              </tr>
            ))}
            {!group.members?.length && (
              <tr>
                <td colSpan={5} className="px-6 py-8 text-center text-slate-500">
                  Chưa có thành viên nào trong nhóm.
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
function GroupProjectTab({ project }: { project: any }) {
  function formatProjectStatus(status?: string) {
    switch (status) {
      case 'DRAFT': return 'Bản nháp';
      case 'PLANNED': return 'Đang lên kế hoạch';
      case 'ONGOING': return 'Đang thực hiện';
      case 'WAITING_REVIEW': return 'Chờ duyệt';
      case 'COMPLETED': return 'Hoàn thành';
      case 'ARCHIVED': return 'Lưu trữ';
      case 'CANCELLED': return 'Đã hủy';
      default: return 'Chưa cập nhật';
    }
  }

  function getProjectStatusClass(status?: string) {
    switch (status) {
      case 'ONGOING':
        return 'bg-emerald-50 text-emerald-700 ring-emerald-100';
      case 'COMPLETED':
        return 'bg-blue-50 text-blue-700 ring-blue-100';
      case 'WAITING_REVIEW':
        return 'bg-amber-50 text-amber-700 ring-amber-100';
      case 'DRAFT':
      case 'PLANNED':
        return 'bg-slate-50 text-slate-700 ring-slate-100';
      default:
        return 'bg-red-50 text-red-700 ring-red-100';
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-100 pb-4">
        <div>
          <h3 className="text-lg font-bold text-slate-950">Thông tin đề tài nghiên cứu</h3>
          <p className="mt-1 text-sm text-slate-600">
            Chi tiết đề tài khoa học nhóm đang thực hiện. Thông tin này ở chế độ chỉ đọc.
          </p>
        </div>
        <span className={`inline-flex shrink-0 rounded-full px-3 py-1 text-xs font-bold uppercase ring-1 ${getProjectStatusClass(project.status)}`}>
          {formatProjectStatus(project.status)}
        </span>
      </div>

      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        <div className="rounded-lg border border-slate-100 bg-slate-50/50 p-4 space-y-1">
          <span className="block text-xs font-semibold text-slate-500 uppercase tracking-wider">Tên đề tài</span>
          <span className="block font-bold text-slate-900 text-sm leading-snug">{project.title}</span>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50/50 p-4 space-y-1">
          <span className="block text-xs font-semibold text-slate-500 uppercase tracking-wider">Chủ đề nghiên cứu</span>
          <span className="block font-semibold text-slate-800 text-sm">{project.researchDirection || 'Chưa cập nhật'}</span>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50/50 p-4 space-y-1">
          <span className="block text-xs font-semibold text-slate-500 uppercase tracking-wider">Mã đề tài / Quản lý</span>
          <span className="block font-semibold text-slate-800 text-sm">
            {project.code || 'N/A'} — {project.managerName || 'Chưa phân công'}
          </span>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50/50 p-4 space-y-1">
          <span className="block text-xs font-semibold text-slate-500 uppercase tracking-wider">Ngày bắt đầu</span>
          <span className="block font-semibold text-slate-800 text-sm">{formatDate(project.startDate) || 'Chưa cập nhật'}</span>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50/50 p-4 space-y-1 col-span-1 md:col-span-2">
          <span className="block text-xs font-semibold text-slate-500 uppercase tracking-wider font-semibold">Ngày kết thúc dự kiến</span>
          <span className="block font-semibold text-slate-800 text-sm">{formatDate(project.expectedEndDate) || 'Chưa cập nhật'}</span>
        </div>
      </div>

      <div className="border-t border-slate-100 pt-5 space-y-5">
        <div>
          <h4 className="text-sm font-bold text-slate-900 uppercase tracking-wider">Mô tả chi tiết</h4>
          <p className="mt-2 text-sm text-slate-600 leading-relaxed whitespace-pre-line">
            {project.description || 'Chưa có mô tả chi tiết cho đề tài.'}
          </p>
        </div>
        <div>
          <h4 className="text-sm font-bold text-slate-900 uppercase tracking-wider">Mục tiêu đề tài</h4>
          <p className="mt-2 text-sm text-slate-600 leading-relaxed whitespace-pre-line">
            {project.objective || 'Chưa có thông tin mục tiêu đề tài.'}
          </p>
        </div>
        <div>
          <h4 className="text-sm font-bold text-slate-900 uppercase tracking-wider">Sản phẩm kỳ vọng</h4>
          <p className="mt-2 text-sm text-slate-600 leading-relaxed whitespace-pre-line bg-blue-50/30 rounded-lg p-4 border border-blue-100/50">
            {project.requiredProducts || 'Chưa có thông tin sản phẩm kỳ vọng.'}
          </p>
        </div>
        <div>
          <h4 className="text-sm font-bold text-slate-900 uppercase tracking-wider">Tiêu chí đánh giá</h4>
          <p className="mt-2 text-sm text-slate-600 leading-relaxed whitespace-pre-line bg-emerald-50/20 rounded-lg p-4 border border-emerald-100/30">
            {project.evaluationCriteria || 'Chưa có thông tin tiêu chí đánh giá.'}
          </p>
        </div>
      </div>
    </div>
  );
}

function GroupTaskList({ groupId }: { groupId: number }) {
  const { data: tasks = [], error, isLoading, isError, refetch } = useGroupTasks(groupId);
  const errorMessage = getApiErrorMessage(error, {
    fallback: 'Không thể tải danh sách nhiệm vụ.',
    forbidden: 'Bạn không có quyền xem nhiệm vụ của nhóm này.',
  });

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div>
        <h3 className="text-lg font-semibold text-slate-950">Bảng tiến độ nhóm</h3>
        <p className="mt-1 text-sm text-slate-600">
          Danh sách nhiệm vụ của nhóm nghiên cứu theo endpoint group-scoped.
        </p>
      </div>

      {isLoading ? (
        <LoadingState className="mt-5">Đang tải danh sách nhiệm vụ...</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          {errorMessage}
        </ErrorState>
      ) : !tasks.length ? (
        <EmptyState className="mt-5">Nhóm chưa có nhiệm vụ nào.</EmptyState>
      ) : (
        <div className="mt-5 overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase tracking-wider text-slate-500">
                <th className="px-4 py-3 font-semibold">Nhiệm vụ</th>
                <th className="px-4 py-3 font-semibold">Người phụ trách</th>
                <th className="px-4 py-3 font-semibold">Hạn hoàn thành</th>
                <th className="px-4 py-3 font-semibold">Tiến độ</th>
                <th className="px-4 py-3 font-semibold">Trạng thái</th>
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
                    {task.assigneeName || task.assigneeEmail || 'Chưa phân công'}
                  </td>
                  <td className="px-4 py-3 text-slate-600">{formatDate(task.deadline)}</td>
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
                      {task.statusLabel}
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
  const { data: milestones = [], isLoading, isError, error, refetch } = useMilestonesByGroup(groupId);
  const [selectedMilestoneId, setSelectedMilestoneId] = useState<number | null>(null);

  useEffect(() => {
    if (milestones.length > 0 && selectedMilestoneId === null) {
      setSelectedMilestoneId(milestones[0].id);
    }
  }, [milestones, selectedMilestoneId]);

  const errorMessage = getApiErrorMessage(error, {
    fallback: 'Không thể tải danh sách mốc nghiên cứu.',
    forbidden: 'Bạn không có quyền xem thông tin nhiệm vụ của nhóm này.',
  });

  if (isLoading) {
    return <LoadingState className="py-8">Đang tải thông tin nhiệm vụ...</LoadingState>;
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
        Nhóm chưa có mốc nghiên cứu nào. Vui lòng tạo mốc nghiên cứu trước để quản lý nhiệm vụ.
      </EmptyState>
    );
  }

  const selectedMilestone = milestones.find((m) => m.id === selectedMilestoneId) || milestones[0];

  return (
    <div className="space-y-6">
      {/* Milestone Selector */}
      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h3 className="text-base font-bold text-slate-900">Nhiệm vụ theo mốc nghiên cứu</h3>
          <p className="mt-1 text-sm text-slate-600">Chọn mốc nghiên cứu để xem và quản lý danh sách nhiệm vụ.</p>
        </div>
        <div className="w-full sm:w-72">
          <select
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
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm space-y-4">
          <div className="border-b border-slate-100 pb-4">
            <h4 className="text-base font-bold text-slate-900">{selectedMilestone.title}</h4>
            <p className="mt-1 text-sm text-slate-600">{selectedMilestone.description || 'Chưa có mô tả cho mốc này.'}</p>
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
  const [subTab, setSubTab] = useState<'me' | 'group'>('me');

  return (
    <div className="space-y-6">
      {/* Sub-tab selection */}
      <div className="flex gap-1.5 rounded-md bg-slate-100 p-1 w-fit">
        <button
          onClick={() => setSubTab('me')}
          className={`rounded-md px-4 py-1.5 text-xs font-semibold transition ${
            subTab === 'me'
              ? 'bg-white text-blue-700 shadow-sm font-bold'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          Nhiệm vụ của tôi
        </button>
        <button
          onClick={() => setSubTab('group')}
          className={`rounded-md px-4 py-1.5 text-xs font-semibold transition ${
            subTab === 'group'
              ? 'bg-white text-blue-700 shadow-sm font-bold'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          Bảng tiến độ nhóm
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
  const [subTab, setSubTab] = useState<'me' | 'group'>('me');

  return (
    <div className="space-y-6">
      {/* Sub-tab selection */}
      <div className="flex gap-1.5 rounded-md bg-slate-100 p-1 w-fit">
        <button
          onClick={() => setSubTab('me')}
          className={`rounded-md px-4 py-1.5 text-xs font-semibold transition ${
            subTab === 'me'
              ? 'bg-white text-blue-700 shadow-sm font-bold'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          Mốc của tôi
        </button>
        <button
          onClick={() => setSubTab('group')}
          className={`rounded-md px-4 py-1.5 text-xs font-semibold transition ${
            subTab === 'group'
              ? 'bg-white text-blue-700 shadow-sm font-bold'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          Mốc của nhóm
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
          emptyMessage="Bạn chưa được giao mốc nghiên cứu nào trong nhóm này."
        />
      ) : (
        <MilestoneList
          key={`group-milestones-all-${groupId}`}
          projectId={projectId}
          labId={labId}
          canCreate={false}
          groupId={groupId}
          groupRole="LEADER"
          emptyMessage="Nhóm chưa có mốc nghiên cứu nào."
        />
      )}
    </div>
  );
}


function StatItem({
  title,
  value,
  desc,
}: {
  title: string;
  value: string | number;
  desc: string;
}) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div>
        <span className="block text-xs font-semibold text-slate-500 uppercase tracking-wider">
          {title}
        </span>
        <span className="block mt-1 text-2xl font-bold text-slate-900 leading-none">
          {value}
        </span>
        <span className="block mt-1 text-xs text-slate-500 truncate">
          {desc}
        </span>
      </div>
    </div>
  );
}
