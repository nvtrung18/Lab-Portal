import { useMemo } from 'react';
import axios from 'axios';

import { EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import type { UserProfileResponse } from '../../user/api/user.api';
import { useProjectDashboardStats, useResearchLogs, useProductsByProject } from '../hooks';
import type { DashboardStats, ResearchProductStatus, ResearchProductType } from '../types';
import { StatCard } from './StatCard';
import { TaskProgressChart } from './TaskProgressChart';
import { getStatusClass } from '../utils';

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
};

const MAX_VISIBLE_MILESTONES = 20;
const MAX_VISIBLE_GROUPS = 10;

export function DashboardPage({ projectId, role, groupRole }: DashboardPageProps) {
  const { locale, t } = useI18n();
  const { data: stats, error, isError, isFetching, isLoading, refetch } = useProjectDashboardStats(projectId);
  const { data: logsData } = useResearchLogs(projectId);
  const { data: products = [] } = useProductsByProject(projectId);

  const statsForProject = useMemo(() => (stats?.projectId === projectId ? stats : undefined), [projectId, stats]);
  const cards = useMemo(() => buildStatCards(statsForProject, t), [statsForProject, t]);
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
  const scopeLabel = useMemo(() => resolveScopeLabel(statsForProject, role, groupRole, t), [groupRole, role, statsForProject, t]);
  const errorMessage = useMemo(() => getDashboardErrorMessage(error, t), [error, t]);
  const isForbidden = useMemo(() => isForbiddenError(error), [error]);
  const isStatsLoading = isLoading || (isFetching && !statsForProject);

  if (isStatsLoading) {
    return (
      <section className="space-y-5">
        <DashboardHeader scopeLabel={scopeLabel} t={t} />
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <LoadingState />
        </div>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, index) => (
            <StatCard key={index} title={t('common.loading')} value="0" loading />
          ))}
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="space-y-5">
        <DashboardHeader scopeLabel={scopeLabel} t={t} />
        <ErrorState onRetry={!isForbidden ? () => refetch() : undefined}>
          {errorMessage}
        </ErrorState>
      </section>
    );
  }

  if (!statsForProject || isEmpty) {
    return (
      <section className="space-y-5">
        <DashboardHeader scopeLabel={scopeLabel} t={t} />
        <EmptyState>{t('common.empty')}</EmptyState>
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <DashboardHeader scopeLabel={scopeLabel} t={t} />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => (
          <StatCard
            key={card.title}
            title={card.title}
            value={card.value}
            suffix={card.suffix}
          />
        ))}
      </div>

      <div className="grid gap-5">
        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-base font-semibold text-slate-950">{t('research.dashboard.taskProgress')}</h3>
          <TaskProgressChart taskProgress={taskChartData} />
        </section>
      </div>

      <div className="grid gap-5 xl:grid-cols-2">
        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-base font-semibold text-slate-950">{t('research.dashboard.milestones')}</h3>
          {!milestoneProgressData.length ? (
            <p className="mt-4 text-sm text-slate-600">{t('research.dashboard.noMilestones')}</p>
          ) : (
            <div className="mt-4 space-y-4">
              {visibleMilestones.map((milestone) => (
                <div key={milestone.milestoneId || milestone.title}>
                  <div className="flex items-center justify-between gap-3 text-sm">
                    <span className="font-medium text-slate-800">{milestone.title || t('research.dashboard.unnamedMilestone')}</span>
                    <span className="shrink-0 text-slate-500">{milestone.statusLabel}</span>
                  </div>
                  <ProgressBar value={milestone.progressPercent} className="mt-2" />
                </div>
              ))}
              {hiddenMilestoneCount ? (
                <p className="text-xs text-slate-500">{t('research.dashboard.moreMilestones', undefined, { count: hiddenMilestoneCount })}</p>
              ) : null}
            </div>
          )}
        </section>

        {statsForProject.scope !== 'ME' ? (
          <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <h3 className="text-base font-semibold text-slate-950">{t('research.dashboard.groupProgress')}</h3>
            {!groupProgressData.length ? (
              <p className="mt-4 text-sm text-slate-600">{t('research.dashboard.noGroupData')}</p>
            ) : (
              <div className="mt-4 space-y-4">
                {visibleGroups.map((group) => (
                  <div key={group.groupId || group.groupName} className="rounded-md border border-slate-200 p-4">
                    <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                      <h4 className="font-semibold text-slate-950">{group.groupName || t('research.dashboard.unnamedGroup')}</h4>
                      <span className="text-sm text-slate-500">{t('research.dashboard.memberCount', undefined, { count: group.memberCount })}</span>
                    </div>
                    <ProgressBar value={group.taskCompletionRate} className="mt-3" />
                    <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-3">
                      <Metric label={t('research.report.submitted')} value={group.reportCount} />
                      <Metric label={t('research.product.title')} value={group.productCount} />
                      <Metric label={t('adminDashboard.averageScore')} value={formatScore(group.averageEvaluationScore, t)} />
                    </dl>
                  </div>
                ))}
                {hiddenGroupCount ? (
                  <p className="text-xs text-slate-500">{t('research.dashboard.moreGroups', undefined, { count: hiddenGroupCount })}</p>
                ) : null}
              </div>
            )}
          </section>
        ) : null}
      </div>

      <div className="grid gap-5 xl:grid-cols-2">
        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-base font-semibold text-slate-950">{t('research.dashboard.recentLogs')}</h3>
          {!recentLogs.length ? (
            <p className="mt-4 text-sm text-slate-600">{t('research.dashboard.noLogs')}</p>
          ) : (
            <div className="mt-4 space-y-4">
              {recentLogs.map((log) => (
                <div key={log.id} className="relative pl-5 border-l-2 border-slate-100 pb-2 last:pb-0">
                  <span className="absolute -left-1.5 top-1.5 h-3 w-3 rounded-full border-2 border-white bg-slate-900" />
                  <div className="text-sm">
                    <div className="flex items-center justify-between gap-3">
                      <span className="font-semibold text-slate-800">{log.authorName || t('research.role.member')}</span>
                      <span className="text-xs text-slate-500">
                        {log.workDate ? new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN').format(new Date(log.workDate)) : ''}
                      </span>
                    </div>
                    <p className="mt-1 text-slate-600 line-clamp-2">{log.content}</p>
                    {log.taskTitle && (
                      <span className="mt-1.5 inline-block rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                        {t('research.dashboard.taskPrefix', undefined, { name: log.taskTitle })}
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-base font-semibold text-slate-950">{t('research.dashboard.recentProducts')}</h3>
          {!recentProducts.length ? (
            <p className="mt-4 text-sm text-slate-600">{t('research.dashboard.noProducts')}</p>
          ) : (
            <div className="mt-4 space-y-4">
              {recentProducts.map((product) => (
                <div key={product.id} className="flex items-start justify-between gap-4 rounded-md border border-slate-100 p-3 hover:bg-slate-50">
                  <div className="min-w-0">
                    <h4 className="truncate text-sm font-semibold text-slate-800">{product.title}</h4>
                    <div className="mt-1 flex flex-wrap gap-2 text-xs text-slate-500">
                      <span>{t('research.dashboard.submittedBy', undefined, { name: product.submittedByName || t('common.notUpdated') })}</span>
                      <span>•</span>
                      <span>{t('research.dashboard.productType', undefined, { type: getProductTypeLabel(product.productType, t) })}</span>
                    </div>
                  </div>
                  <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold ring-1 ${getStatusClass(product.status)}`}>
                    {getProductStatusLabel(product.status, t)}
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

function DashboardHeader({ scopeLabel, t }: { scopeLabel: string; t: ReturnType<typeof useI18n>['t'] }) {
  return (
    <div>
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <h2 className="text-xl font-semibold text-slate-950">{t('research.dashboard.title')}</h2>
        <span className="w-fit rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-700">
          {scopeLabel}
        </span>
      </div>
    </div>
  );
}

function buildStatCards(stats: DashboardStats | undefined, t: ReturnType<typeof useI18n>['t']): StatCardModel[] {
  if (!stats) {
    return [];
  }

  return [
    {
      title: stats.scope === 'PROJECT' ? t('research.dashboard.member') : stats.scope === 'GROUP' ? t('research.dashboard.groupMembers') : t('research.dashboard.personal'),
      value: stats.cards.memberCount,
    },
    {
      title: stats.scope === 'ME' ? t('research.dashboard.myTasks') : t('research.dashboard.taskProgressCard'),
      value: formatNumber(stats.cards.taskCompletionRate),
      suffix: '%',
    },
    {
      title: stats.scope === 'ME' ? t('research.dashboard.myOverdueTasks') : t('research.dashboard.overdueTasks'),
      value: stats.cards.overdueTaskCount,
    },
    {
      title: stats.scope === 'ME' ? t('research.dashboard.myReports') : t('research.dashboard.submittedReports'),
      value: stats.cards.reportCount,
    },
    {
      title: stats.scope === 'ME' ? t('research.dashboard.myApprovedReports') : t('research.dashboard.approvedReports'),
      value: stats.cards.approvedReportCount,
    },
    {
      title: stats.scope === 'ME' ? t('research.dashboard.myProducts') : t('research.dashboard.products'),
      value: stats.cards.productCount,
    },
    {
      title: stats.scope === 'ME' ? t('research.dashboard.myScore') : t('research.dashboard.averageScore'),
      value: formatScore(stats.cards.averageEvaluationScore, t),
    },
    {
      title: t('research.dashboard.completedMilestones'),
      value: stats.cards.completedMilestoneCount,
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

function resolveScopeLabel(stats: DashboardStats | undefined, role: string, groupRole: string | null | undefined, t: ReturnType<typeof useI18n>['t']) {
  if (stats?.scope === 'GROUP') {
    return t('research.dashboard.scopeGroup');
  }
  if (stats?.scope === 'ME') {
    return t('research.dashboard.scopeMe');
  }
  if (stats?.scope === 'PROJECT') {
    return t('research.dashboard.scopeProject');
  }
  if (role === 'LAB_MANAGER') {
    return t('research.dashboard.scopeProject');
  }
  if (groupRole === 'LEADER') {
    return t('research.dashboard.scopeGroup');
  }
  return t('research.dashboard.scopeMe');
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

function formatScore(value: number | null, t: ReturnType<typeof useI18n>['t']) {
  return value == null ? t('research.dashboard.noScore') : formatNumber(value);
}

function formatNumber(value: number) {
  const safeValue = safeNumber(value);
  return Number.isInteger(safeValue) ? String(safeValue) : safeValue.toFixed(1);
}

function safeNumber(value: number | null | undefined) {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function getDashboardErrorMessage(error: unknown, t: ReturnType<typeof useI18n>['t']) {
  if (isForbiddenError(error)) {
    return t('research.dashboard.forbidden');
  }
  return t('research.dashboard.loadFailed');
}

function getProductTypeLabel(type: ResearchProductType, t: ReturnType<typeof useI18n>['t']) {
  const key = {
    FINAL_REPORT: 'finalReport',
    SLIDE: 'slide',
    SOURCE_CODE: 'sourceCode',
    DATASET: 'dataset',
    DEMO_VIDEO: 'demoVideo',
    PAPER: 'paper',
    SOFTWARE_DEMO: 'softwareDemo',
    OTHER: 'other',
  }[type];
  return t(`research.product.type.${key}`);
}

function getProductStatusLabel(status: ResearchProductStatus, t: ReturnType<typeof useI18n>['t']) {
  const key = {
    SUBMITTED: 'submitted',
    ACCEPTED: 'accepted',
    NEEDS_REVISION: 'needsRevision',
    REJECTED: 'rejected',
  }[status];
  return t(`research.product.status.${key}`);
}

function isForbiddenError(error: unknown) {
  return axios.isAxiosError(error) && error.response?.status === 403;
}
