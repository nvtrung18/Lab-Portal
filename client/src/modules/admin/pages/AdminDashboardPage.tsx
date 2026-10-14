import { useMemo } from 'react';
import { Activity, ArrowRight, ClipboardList, FlaskConical, UsersRound, type LucideIcon } from 'lucide-react';
import { Link } from 'react-router-dom';

import { EmptyState, ErrorState } from '../../../shared/components';
import { useI18n, type Locale } from '../../../shared/i18n';
import { AdminStatCard } from '../components';
import { useAdminDashboardStats } from '../hooks';
import type { AdminDashboardStats } from '../api';

interface StatItem {
  title: string;
  value: number | string | null | undefined;
  suffix?: string;
}

interface StatSection {
  title: string;
  icon: LucideIcon;
  items: StatItem[];
  to?: string;
}

function toFiniteNumber(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function formatCount(value: unknown, locale: Locale) {
  return new Intl.NumberFormat(locale === 'en' ? 'en-US' : 'vi-VN').format(toFiniteNumber(value));
}

function formatAverageScore(value: number | null | undefined, locale: Locale, t: (key: string, fallback?: string) => string) {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return t('common.notUpdated');
  }

  return new Intl.NumberFormat(locale === 'en' ? 'en-US' : 'vi-VN').format(value);
}

function hasAnyDashboardData(stats?: AdminDashboardStats | null) {
  if (!stats) {
    return false;
  }

  const values = [
    stats.users?.total,
    stats.users?.active,
    stats.users?.banned,
    stats.users?.unassignedManagers,
    stats.labs?.total,
    stats.labs?.active,
    stats.labs?.inactive,
    stats.labs?.withoutManager,
    stats.operations?.pendingApplications,
    stats.operations?.todaySlots,
    stats.operations?.todayBookings,
    stats.operations?.pendingComplaints,
    stats.operations?.pendingCleaningTasks,
    stats.research?.activeProjects,
    stats.research?.activeGroups,
    stats.research?.pendingReports,
    stats.research?.submittedProducts,
    stats.research?.averageEvaluationScore,
  ];

  return values.some((value) => typeof value === 'number' && Number.isFinite(value) && value > 0);
}

function buildSections(
  stats: AdminDashboardStats,
  locale: Locale,
  t: (key: string, fallback?: string) => string,
): StatSection[] {
  return [
    {
      title: t('adminDashboard.users'),
      icon: UsersRound,
      to: '/admin/users',
      items: [
        {
          title: t('adminDashboard.userTotal'),
          value: formatCount(stats.users?.total, locale),
        },
        {
          title: t('adminDashboard.activeAccounts'),
          value: formatCount(stats.users?.active, locale),
        },
        {
          title: t('adminDashboard.bannedAccounts'),
          value: formatCount(stats.users?.banned, locale),
        },
        {
          title: t('adminDashboard.unassignedManagers'),
          value: formatCount(stats.users?.unassignedManagers, locale),
        },
      ],
    },
    {
      title: t('adminDashboard.labs'),
      icon: FlaskConical,
      to: '/admin/labs',
      items: [
        {
          title: t('adminDashboard.labTotal'),
          value: formatCount(stats.labs?.total, locale),
        },
        {
          title: t('adminDashboard.activeLabs'),
          value: formatCount(stats.labs?.active, locale),
        },
        {
          title: t('adminDashboard.inactiveLabs'),
          value: formatCount(stats.labs?.inactive, locale),
        },
        {
          title: t('adminDashboard.labsWithoutManager'),
          value: formatCount(stats.labs?.withoutManager, locale),
        },
      ],
    },
    {
      title: t('adminDashboard.operations'),
      icon: Activity,
      items: [
        {
          title: t('adminDashboard.pendingApplications'),
          value: formatCount(stats.operations?.pendingApplications, locale),
        },
        {
          title: t('adminDashboard.todaySlots'),
          value: formatCount(stats.operations?.todaySlots, locale),
        },
        {
          title: t('adminDashboard.todayBookings'),
          value: formatCount(stats.operations?.todayBookings, locale),
        },
        {
          title: t('adminDashboard.pendingComplaints'),
          value: formatCount(stats.operations?.pendingComplaints, locale),
        },
        {
          title: t('adminDashboard.pendingCleaningTasks'),
          value: formatCount(stats.operations?.pendingCleaningTasks, locale),
        },
      ],
    },
    {
      title: t('adminDashboard.research'),
      icon: ClipboardList,
      items: [
        {
          title: t('adminDashboard.activeProjects'),
          value: formatCount(stats.research?.activeProjects, locale),
        },
        {
          title: t('adminDashboard.activeResearchGroups'),
          value: formatCount(stats.research?.activeGroups, locale),
        },
        {
          title: t('adminDashboard.pendingReports'),
          value: formatCount(stats.research?.pendingReports, locale),
        },
        {
          title: t('adminDashboard.submittedProducts'),
          value: formatCount(stats.research?.submittedProducts, locale),
        },
        {
          title: t('adminDashboard.averageScore'),
          value: formatAverageScore(stats.research?.averageEvaluationScore, locale, t),
          suffix:
            typeof stats.research?.averageEvaluationScore === 'number' &&
            Number.isFinite(stats.research.averageEvaluationScore)
              ? t('adminDashboard.point')
              : undefined,
        },
      ],
    },
  ];
}

export function AdminDashboardPage() {
  const { locale, t } = useI18n();
  const { data: stats, isError, isLoading, refetch } = useAdminDashboardStats();
  const sections = useMemo(() => (stats ? buildSections(stats, locale, t) : []), [locale, stats, t]);
  const quickActions = [
    { icon: UsersRound, label: t('nav.users'), to: '/admin/users' },
    { icon: FlaskConical, label: t('nav.labsAdmin'), to: '/admin/labs' },
    { icon: Activity, label: t('nav.adminOperations'), to: '/admin/operational-logs' },
  ];

  return (
    <section className="mx-auto max-w-7xl space-y-7">
      <DashboardHeader t={t} />

      <nav aria-label={t('dashboard.quickAccess')} className="grid gap-3 sm:grid-cols-3">
        {quickActions.map(({ icon: Icon, label, to }) => (
          <Link className="app-action-card flex min-h-16 items-center gap-3 px-4 py-3 text-sm font-semibold" key={to} to={to}>
            <span className="app-icon-badge flex h-10 w-10 shrink-0 items-center justify-center rounded-xl">
              <Icon aria-hidden="true" className="h-5 w-5" />
            </span>
            <span className="min-w-0 flex-1 truncate">{label}</span>
            <ArrowRight aria-hidden="true" className="h-4 w-4 shrink-0 opacity-70" />
          </Link>
        ))}
      </nav>

      {isLoading ? (
        <section aria-live="polite" className="space-y-3">
          <p className="text-sm font-medium text-slate-600 dark:text-slate-300">{t('adminDashboard.loading')}</p>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 8 }).map((_, index) => (
              <AdminStatCard key={index} loading title={t('common.loading')} value={0} />
            ))}
          </div>
        </section>
      ) : isError ? (
        <ErrorState className="app-surface-elevated" onRetry={() => void refetch()}>
          {t('adminDashboard.loadError')}
        </ErrorState>
      ) : !hasAnyDashboardData(stats) ? (
        <EmptyState className="app-surface-elevated">{t('adminDashboard.empty')}</EmptyState>
      ) : (
        <div className="grid gap-5 xl:grid-cols-2">
          {sections.map((section) => {
            const Icon = section.icon;

            return (
              <section className="app-surface rounded-2xl p-4 sm:p-5" key={section.title}>
                <div className="flex min-w-0 items-center justify-between gap-3">
                  <div className="flex min-w-0 items-center gap-3">
                    <span className="app-icon-badge flex h-10 w-10 shrink-0 items-center justify-center rounded-xl">
                      <Icon aria-hidden="true" className="h-5 w-5" />
                    </span>
                    <h2 className="truncate text-base font-semibold text-slate-950 dark:text-white">{section.title}</h2>
                  </div>
                  {section.to ? (
                    <Link
                      aria-label={`${t('dashboard.viewAll')}: ${section.title}`}
                      className="app-action-card flex h-11 w-11 shrink-0 items-center justify-center"
                      to={section.to}
                    >
                      <ArrowRight aria-hidden="true" className="h-4 w-4" />
                    </Link>
                  ) : null}
                </div>
                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  {section.items.map((item) => (
                    <AdminStatCard
                      key={item.title}
                      title={item.title}
                      value={item.value}
                      suffix={item.suffix}
                    />
                  ))}
                </div>
              </section>
            );
          })}
        </div>
      )}
    </section>
  );
}

function DashboardHeader({ t }: { t: (key: string, fallback?: string) => string }) {
  return (
    <header className="app-feature-banner flex items-end justify-between gap-4">
      <div>
        <p className="text-xs font-semibold tracking-normal text-slate-500 dark:text-slate-400">{t('layout.adminRole')}</p>
        <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-950 dark:text-white sm:text-3xl">{t('adminDashboard.title')}</h1>
      </div>
    </header>
  );
}
