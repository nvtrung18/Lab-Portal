import { useMemo } from 'react';
import axios from 'axios';

import { EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import type { UserProfileResponse } from '../../user/api/user.api';
import { useProjectDashboardStats, useResearchLogs, useProductsByProject } from '../hooks';
import type { DashboardStats, ResearchProductStatus, ResearchProductType } from '../types';
import { StatCard } from './StatCard';
import { TaskProgressChart } from './TaskProgressChart';
import { getStatusClass } from '../utils';

const PRODUCT_TYPE_LABELS: Record<ResearchProductType, string> = {
  FINAL_REPORT: 'Báo cáo tổng kết',
  SLIDE: 'Slide thuyết trình',
  SOURCE_CODE: 'Source code',
  DATASET: 'Bộ dữ liệu',
  DEMO_VIDEO: 'Video demo',
  PAPER: 'Bài báo',
  SOFTWARE_DEMO: 'Demo phần mềm',
  OTHER: 'Khác',
};

const PRODUCT_STATUS_LABELS: Record<ResearchProductStatus, string> = {
  SUBMITTED: 'Đã nộp',
  ACCEPTED: 'Đã chấp nhận',
  NEEDS_REVISION: 'Cần chỉnh sửa',
  REJECTED: 'Không đạt',
};

interface DashboardPageProps {
  projectId: number;
  currentUser?: UserProfileResponse | null;
  role: string;
  groupRole?: string | null;
}

type StatCardModel = {
  title: string;
  value: number | string;
  suffix?: string;
  description?: string;
};

const MAX_VISIBLE_MILESTONES = 20;
const MAX_VISIBLE_GROUPS = 10;

export function DashboardPage({ projectId, role, groupRole }: DashboardPageProps) {
  const { data: stats, error, isError, isFetching, isLoading, refetch } = useProjectDashboardStats(projectId);
  const { data: logsData } = useResearchLogs(projectId);
  const { data: products = [] } = useProductsByProject(projectId);

  const statsForProject = useMemo(() => (stats?.projectId === projectId ? stats : undefined), [projectId, stats]);
  const cards = useMemo(() => buildStatCards(statsForProject), [statsForProject]);
  const taskChartData = useMemo(() => getTaskProgressData(statsForProject), [statsForProject]);
  const milestoneProgressData = useMemo(() => statsForProject?.milestoneProgress ?? [], [statsForProject]);
  const groupProgressData = useMemo(() => statsForProject?.groupProgress ?? [], [statsForProject]);
  const visibleMilestones = useMemo(
    () => milestoneProgressData.slice(0, MAX_VISIBLE_MILESTONES),
    [milestoneProgressData],
  );
  const hiddenMilestoneCount = Math.max(0, milestoneProgressData.length - MAX_VISIBLE_MILESTONES);
  const visibleGroups = useMemo(() => groupProgressData.slice(0, MAX_VISIBLE_GROUPS), [groupProgressData]);
  const hiddenGroupCount = Math.max(0, groupProgressData.length - MAX_VISIBLE_GROUPS);

  const logs = useMemo(() => {
    if (!logsData) return [];
    return logsData.pages.flat();
  }, [logsData]);
  const recentLogs = useMemo(() => logs.slice(0, 5), [logs]);

  const recentProducts = useMemo(() => {
    return [...products]
      .sort((a, b) => {
        const timeA = new Date(a.submittedAt || a.createdAt || 0).getTime();
        const timeB = new Date(b.submittedAt || b.createdAt || 0).getTime();
        return timeB - timeA;
      })
      .slice(0, 5);
  }, [products]);

  const isEmpty = useMemo(() => isDashboardEmpty(statsForProject), [statsForProject]);
  const scopeLabel = useMemo(() => resolveScopeLabel(statsForProject, role, groupRole), [groupRole, role, statsForProject]);
  const errorMessage = useMemo(() => getDashboardErrorMessage(error), [error]);
  const isForbidden = useMemo(() => isForbiddenError(error), [error]);
  const isStatsLoading = isLoading || (isFetching && !statsForProject);

  if (isStatsLoading) {
    return (
      <section className="space-y-5">
        <DashboardHeader scopeLabel={scopeLabel} />
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <LoadingState />
        </div>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, index) => (
            <StatCard key={index} title="Đang tải" value="0" loading />
          ))}
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="space-y-5">
        <DashboardHeader scopeLabel={scopeLabel} />
        <ErrorState onRetry={!isForbidden ? () => refetch() : undefined}>
          {errorMessage}
        </ErrorState>
      </section>
    );
  }

  if (!statsForProject || isEmpty) {
    return (
      <section className="space-y-5">
        <DashboardHeader scopeLabel={scopeLabel} />
        <EmptyState>Chưa có dữ liệu.</EmptyState>
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <DashboardHeader scopeLabel={scopeLabel} />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => (
          <StatCard
            key={card.title}
            title={card.title}
            value={card.value}
            suffix={card.suffix}
            description={card.description}
          />
        ))}
      </div>

      <div className="grid gap-5">
        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-base font-semibold text-slate-950">Tiến độ task</h3>
          <TaskProgressChart taskProgress={taskChartData} />
        </section>
      </div>

      <div className="grid gap-5 xl:grid-cols-2">
        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-base font-semibold text-slate-950">Mốc nghiên cứu</h3>
          {!milestoneProgressData.length ? (
            <p className="mt-4 text-sm text-slate-600">Chưa có mốc nghiên cứu.</p>
          ) : (
            <div className="mt-4 space-y-4">
              {visibleMilestones.map((milestone) => (
                <div key={milestone.milestoneId || milestone.title}>
                  <div className="flex items-center justify-between gap-3 text-sm">
                    <span className="font-medium text-slate-800">{milestone.title || 'Mốc chưa đặt tên'}</span>
                    <span className="shrink-0 text-slate-500">{milestone.statusLabel}</span>
                  </div>
                  <ProgressBar value={milestone.progressPercent} className="mt-2" />
                </div>
              ))}
              {hiddenMilestoneCount ? (
                <p className="text-xs text-slate-500">Còn {hiddenMilestoneCount} mốc khác.</p>
              ) : null}
            </div>
          )}
        </section>

        {statsForProject.scope !== 'ME' ? (
          <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <h3 className="text-base font-semibold text-slate-950">Tiến độ nhóm</h3>
            {!groupProgressData.length ? (
              <p className="mt-4 text-sm text-slate-600">Chưa có dữ liệu nhóm.</p>
            ) : (
              <div className="mt-4 space-y-4">
                {visibleGroups.map((group) => (
                  <div key={group.groupId || group.groupName} className="rounded-md border border-slate-200 p-4">
                    <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                      <h4 className="font-semibold text-slate-950">{group.groupName || 'Nhóm chưa đặt tên'}</h4>
                      <span className="text-sm text-slate-500">{group.memberCount} thành viên</span>
                    </div>
                    <ProgressBar value={group.taskCompletionRate} className="mt-3" />
                    <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-3">
                      <Metric label="Báo cáo" value={group.reportCount} />
                      <Metric label="Sản phẩm" value={group.productCount} />
                      <Metric label="Điểm TB" value={formatScore(group.averageEvaluationScore)} />
                    </dl>
                  </div>
                ))}
                {hiddenGroupCount ? (
                  <p className="text-xs text-slate-500">Còn {hiddenGroupCount} nhóm khác.</p>
                ) : null}
              </div>
            )}
          </section>
        ) : null}
      </div>

      <div className="grid gap-5 xl:grid-cols-2">
        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-base font-semibold text-slate-950">Nhật ký nghiên cứu gần đây</h3>
          {!recentLogs.length ? (
            <p className="mt-4 text-sm text-slate-600">Chưa có nhật ký nghiên cứu nào.</p>
          ) : (
            <div className="mt-4 space-y-4">
              {recentLogs.map((log) => (
                <div key={log.id} className="relative pl-5 border-l-2 border-slate-100 pb-2 last:pb-0">
                  <span className="absolute -left-1.5 top-1.5 h-3 w-3 rounded-full border-2 border-white bg-slate-900" />
                  <div className="text-sm">
                    <div className="flex items-center justify-between gap-3">
                      <span className="font-semibold text-slate-800">{log.authorName || 'Thành viên'}</span>
                      <span className="text-xs text-slate-500">
                        {log.workDate ? new Intl.DateTimeFormat('vi-VN').format(new Date(log.workDate)) : ''}
                      </span>
                    </div>
                    <p className="mt-1 text-slate-600 line-clamp-2">{log.content}</p>
                    {log.taskTitle && (
                      <span className="mt-1.5 inline-block rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                        Nhiệm vụ: {log.taskTitle}
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-base font-semibold text-slate-950">Sản phẩm mới nộp</h3>
          {!recentProducts.length ? (
            <p className="mt-4 text-sm text-slate-600">Chưa có sản phẩm nghiên cứu nào.</p>
          ) : (
            <div className="mt-4 space-y-4">
              {recentProducts.map((product) => (
                <div key={product.id} className="flex items-start justify-between gap-4 rounded-md border border-slate-100 p-3 hover:bg-slate-50">
                  <div className="min-w-0">
                    <h4 className="truncate text-sm font-semibold text-slate-800">{product.title}</h4>
                    <div className="mt-1 flex flex-wrap gap-2 text-xs text-slate-500">
                      <span>Người nộp: {product.submittedByName || 'Chưa cập nhật'}</span>
                      <span>•</span>
                      <span>Loại: {PRODUCT_TYPE_LABELS[product.productType] ?? product.productType}</span>
                    </div>
                  </div>
                  <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold ring-1 ${getStatusClass(product.status)}`}>
                    {PRODUCT_STATUS_LABELS[product.status] ?? product.status}
                  </span>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </section>
  );
}

function DashboardHeader({ scopeLabel }: { scopeLabel: string }) {
  return (
    <div>
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <h2 className="text-xl font-semibold text-slate-950">Tổng quan NCKH</h2>
        <span className="w-fit rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-700">
          {scopeLabel}
        </span>
      </div>
      <p className="mt-1 text-sm text-slate-600">
        Theo dõi nhanh tiến độ, báo cáo, sản phẩm và đánh giá của đề tài.
      </p>
    </div>
  );
}

function buildStatCards(stats?: DashboardStats): StatCardModel[] {
  if (!stats) {
    return [];
  }

  return [
    {
      title: stats.scope === 'PROJECT' ? 'Thành viên' : stats.scope === 'GROUP' ? 'Thành viên nhóm' : 'Cá nhân',
      value: stats.cards.memberCount,
      description: getScopeDescription(stats),
    },
    {
      title: stats.scope === 'ME' ? 'Task của tôi' : 'Tiến độ task',
      value: formatNumber(stats.cards.taskCompletionRate),
      suffix: '%',
      description: `${stats.taskProgress.done} task đã hoàn thành`,
    },
    {
      title: stats.scope === 'ME' ? 'Task quá hạn của tôi' : 'Task quá hạn',
      value: stats.cards.overdueTaskCount,
      description: 'Task cần được xử lý sớm',
    },
    {
      title: stats.scope === 'ME' ? 'Báo cáo của tôi' : 'Báo cáo đã nộp',
      value: stats.cards.reportCount,
      description: 'Tổng số báo cáo trong đề tài',
    },
    {
      title: stats.scope === 'ME' ? 'Báo cáo của tôi đã duyệt' : 'Báo cáo đã duyệt',
      value: stats.cards.approvedReportCount,
      description: 'Báo cáo đã được phê duyệt',
    },
    {
      title: stats.scope === 'ME' ? 'Sản phẩm của tôi' : 'Sản phẩm nghiên cứu',
      value: stats.cards.productCount,
      description: 'Số sản phẩm đã ghi nhận',
    },
    {
      title: stats.scope === 'ME' ? 'Điểm đánh giá của tôi' : 'Điểm đánh giá trung bình',
      value: formatScore(stats.cards.averageEvaluationScore),
      description: 'Điểm trung bình hiện tại',
    },
    {
      title: 'Mốc đã hoàn thành',
      value: stats.cards.completedMilestoneCount,
      description: `${stats.cards.milestoneCount} mốc nghiên cứu`,
    },
  ];
}

function getTaskProgressData(stats?: DashboardStats) {
  return stats?.taskProgress ?? {
    todo: 0,
    doing: 0,
    waitingReview: 0,
    needsRevision: 0,
    done: 0,
    overdue: 0,
  };
}

function resolveScopeLabel(stats: DashboardStats | undefined, role: string, groupRole?: string | null) {
  if (stats?.scopeLabel) {
    return stats.scopeLabel;
  }
  if (role === 'LAB_MANAGER') {
    return 'Tổng quan đề tài';
  }
  if (groupRole === 'LEADER') {
    return 'Tổng quan nhóm của tôi';
  }
  return 'Tổng quan cá nhân';
}

function getScopeDescription(stats: DashboardStats) {
  if (stats.scope === 'PROJECT') {
    return 'Toàn bộ thành viên trong đề tài';
  }
  if (stats.scope === 'GROUP') {
    return 'Thành viên thuộc nhóm của bạn';
  }
  return 'Dữ liệu cá nhân của bạn';
}

function isDashboardEmpty(stats?: DashboardStats) {
  if (!stats) {
    return true;
  }

  return (
    stats.cards.taskCount === 0 &&
    stats.cards.reportCount === 0 &&
    stats.cards.productCount === 0 &&
    stats.cards.memberCount === 0
  );
}

function ProgressBar({ value, className = '' }: { value: number; className?: string }) {
  const width = Math.max(0, Math.min(100, safeNumber(value)));

  return (
    <div className={`h-2 overflow-hidden rounded-full bg-slate-100 ${className}`}>
      <div className="h-full rounded-full bg-emerald-500" style={{ width: `${width}%` }} />
    </div>
  );
}

function Metric({ label, value }: { label: string; value: number | string }) {
  return (
    <div>
      <dt className="text-slate-500">{label}</dt>
      <dd className="mt-1 font-semibold text-slate-900">{value}</dd>
    </div>
  );
}

function formatScore(value: number | null) {
  return value == null ? 'Chưa có' : formatNumber(value);
}

function formatNumber(value: number) {
  const safeValue = safeNumber(value);
  return Number.isInteger(safeValue) ? String(safeValue) : safeValue.toFixed(1);
}

function safeNumber(value: number | null | undefined) {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function getDashboardErrorMessage(error: unknown) {
  if (isForbiddenError(error)) {
    return 'Bạn không có quyền xem dashboard của đề tài này.';
  }
  return 'Không thể tải dữ liệu dashboard. Vui lòng thử lại.';
}

function isForbiddenError(error: unknown) {
  return axios.isAxiosError(error) && error.response?.status === 403;
}
