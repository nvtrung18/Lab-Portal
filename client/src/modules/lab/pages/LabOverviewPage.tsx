import {
  ArrowRight,
  CalendarClock,
  CheckCircle2,
  MapPin,
  MessageSquareWarning,
  Microscope,
  ScanFace,
  Sparkles,
  UserRoundCheck,
} from 'lucide-react';
import { Link } from 'react-router-dom';

import { getManagedLabId } from '../../../shared/utils/membership';
import { useI18n } from '../../../shared/i18n';
import { AttendanceChart } from '../../research/components/AttendanceChart';
import { useCurrentUser } from '../../user/hooks';
import { useLab, useLabDashboardStats } from '../hooks';

export function LabOverviewPage() {
  const { t } = useI18n();
  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const managedLabId = getManagedLabId(currentUser);
  const { data: managedLab, isLoading: isLoadingLab, isError } = useLab(managedLabId);
  const { data: stats, isLoading: isLoadingStats } = useLabDashboardStats(managedLabId);

  if (isLoadingUser || isLoadingLab || isLoadingStats) return <DashboardSkeleton label={t('manager.loadingOverview')} />;

  if (!managedLabId) {
    return (
      <section className="rounded-xl border border-amber-200 bg-amber-50 p-6 text-amber-800 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200" role="alert">
        <h2 className="font-semibold">{t('manager.noAssignedLab')}</h2>
        <p className="mt-1 text-sm">{t('manager.contactAdmin')}</p>
      </section>
    );
  }

  if (isError || !managedLab) {
    return (
      <section className="rounded-xl border border-red-200 bg-red-50 p-6 text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200" role="alert">
        <h2 className="font-semibold">{t('manager.labLoadFailed')}</h2>
      </section>
    );
  }

  const isOperating = managedLab.status === 'AVAILABLE' || managedLab.status === 'ACTIVE';
  const displayedStatus = isOperating
    ? t('lab.status.active')
    : managedLab.status === 'MAINTENANCE'
      ? t('lab.status.maintenance')
      : t('lab.status.inactive');

  return (
    <section className="mx-auto max-w-7xl space-y-6">
      <header className="app-gradient-banner overflow-hidden rounded-2xl px-5 py-7 sm:px-8 sm:py-9">
        <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
          <div className="min-w-0 max-w-3xl">
            <div className="flex flex-wrap items-center gap-3">
              <p className="text-sm font-medium text-blue-200">{t('manager.dashboard')}</p>
              <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ${isOperating ? 'bg-emerald-400/10 text-emerald-200 ring-emerald-400/30' : 'bg-amber-400/10 text-amber-200 ring-amber-400/30'}`}>
                <CheckCircle2 aria-hidden="true" className="h-3.5 w-3.5" />{displayedStatus}
              </span>
            </div>
          <h1 className="mt-2 text-2xl font-semibold tracking-tight text-balance sm:text-3xl">{managedLab.labName}</h1>
            <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-sm text-slate-300">
              <span className="inline-flex items-center gap-2"><MapPin aria-hidden="true" className="h-4 w-4" />{managedLab.location || t('manager.locationNotUpdated')}</span>
              <span className="inline-flex items-center gap-2"><UserRoundCheck aria-hidden="true" className="h-4 w-4" />{t('lab.capacity')} {managedLab.capacity ?? t('common.notUpdated')}</span>
            </div>
          </div>
          <Link className="app-button-link w-fit shrink-0 text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 focus-visible:ring-offset-2" to="/app/checkin-scan">
            <ScanFace aria-hidden="true" className="h-4 w-4" />{t('manager.openCheckin')}
          </Link>
        </div>
      </header>

      {stats ? (
        <>
          <section aria-labelledby="today-overview-title">
            <div className="flex flex-wrap items-end justify-between gap-2">
              <div>
                <h2 className="text-lg font-semibold text-slate-950 dark:text-white" id="today-overview-title">{t('manager.today')}</h2>
              </div>
              <p className="text-xs font-medium text-slate-500 dark:text-slate-400">{t('manager.checkinWindow')}</p>
            </div>
            <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <SummaryItem icon={CalendarClock} label={t('manager.todaySlots')} value={stats.todaySlots} />
              <SummaryItem icon={UserRoundCheck} label={t('manager.todayBookings')} value={stats.todayBookings} />
              <SummaryItem icon={CheckCircle2} label={t('manager.attendanceRate')} value={`${formatRate(stats.attendanceRate)}%`} />
              <SummaryItem icon={UserRoundCheck} label={t('manager.activeMembers')} value={stats.memberCount} />
            </div>
          </section>

          <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.35fr)_minmax(19rem,0.65fr)]">
            <section className="app-surface-elevated rounded-2xl p-5" aria-labelledby="attendance-title">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h2 className="text-lg font-semibold text-slate-950 dark:text-white" id="attendance-title">{t('manager.attendance')}</h2>
                </div>
                <Link className="app-button-link text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500" to="/app/lab-members">
                  {t('nav.labMembers')} <ArrowRight aria-hidden="true" className="h-4 w-4" />
                </Link>
              </div>
              <AttendanceChart byStudent={stats.attendanceByStudent} />
            </section>

            <div className="space-y-6">
              <section className="app-surface-elevated rounded-2xl p-5" aria-labelledby="pending-title">
                <h2 className="text-lg font-semibold text-slate-950 dark:text-white" id="pending-title">{t('manager.needsAction')}</h2>
                <div className="mt-4 space-y-2">
                  <ActionLink icon={Sparkles} label={t('cleaning.title')} value={stats.pendingCleaningTasks} to="/app/cleaning" />
                  <ActionLink icon={MessageSquareWarning} label={t('nav.complaints')} value={stats.pendingComplaints} to="/app/complaints" />
                  <ActionLink icon={Microscope} label={t('manager.activeProjects')} value={stats.activeResearchProjects} to="/app/research" />
                </div>
              </section>

              <section className="app-surface-elevated rounded-2xl p-5" aria-labelledby="quick-manager-title">
                <h2 className="text-lg font-semibold text-slate-950 dark:text-white" id="quick-manager-title">{t('dashboard.quickAccess')}</h2>
                <div className="mt-4 grid gap-2">
                  <QuickLink label={t('nav.labSlots')} to="/app/lab-slots" />
                  <QuickLink label={t('manager.reviewApplications')} to="/app/applications" />
                  <QuickLink label={t('nav.adminOperations')} to="/app/operational-logs" />
                </div>
              </section>
            </div>
          </div>
        </>
      ) : null}
    </section>
  );
}

function DashboardSkeleton({ label }: { label: string }) {
  return (
    <section aria-label={label} className="mx-auto max-w-7xl space-y-6">
      <div className="h-56 animate-pulse rounded-2xl bg-slate-200 dark:bg-slate-800" />
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, index) => <div className="h-28 animate-pulse rounded-xl bg-slate-100 dark:bg-slate-800" key={index} />)}
      </div>
    </section>
  );
}

function SummaryItem({ icon: Icon, label, value }: { icon: typeof CalendarClock; label: string; value: number | string }) {
  return (
    <article className="app-surface-elevated flex min-w-0 items-center gap-4 rounded-2xl px-4 py-4">
      <span className="app-icon-badge flex h-11 w-11 shrink-0 items-center justify-center rounded-xl"><Icon aria-hidden="true" className="h-5 w-5" /></span>
      <div><p className="text-2xl font-semibold tabular-nums text-slate-950 dark:text-white">{value}</p><p className="text-sm text-slate-600 dark:text-slate-300">{label}</p></div>
    </article>
  );
}

function ActionLink({ icon: Icon, label, value, to }: { icon: typeof Sparkles; label: string; value: number; to: string }) {
  return (
    <Link className="app-action-card group flex min-h-14 items-center gap-3 px-3 py-2" to={to}>
      <Icon aria-hidden="true" className="h-5 w-5 shrink-0 text-slate-600 dark:text-slate-300" />
      <span className="min-w-0 flex-1 text-sm font-medium text-slate-700 dark:text-slate-200">{label}</span>
      <span className="app-control-surface flex h-9 min-w-9 items-center justify-center rounded-full px-2.5 text-sm font-semibold tabular-nums">{value}</span>
    </Link>
  );
}

function QuickLink({ label, to }: { label: string; to: string }) {
  return (
    <Link className="app-action-card flex min-h-11 items-center justify-between gap-3 px-3 text-sm font-semibold" to={to}>
      {label}<ArrowRight aria-hidden="true" className="h-4 w-4 shrink-0 text-slate-400" />
    </Link>
  );
}

function formatRate(value: number) {
  return Number.isFinite(value) ? (Number.isInteger(value) ? value : value.toFixed(1)) : 0;
}
