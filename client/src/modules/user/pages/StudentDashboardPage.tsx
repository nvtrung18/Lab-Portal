import {
  ArrowRight,
  Bot,
  CalendarClock,
  CircleAlert,
  FlaskConical,
  ScanFace,
  UsersRound,
} from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';

import { useMyBookings } from '../../booking/hooks';
import { getBookingStatusLabel } from '../../booking/utils';
import {
  getActiveMemberships,
  getMembershipLabId,
  getMembershipLabName,
} from '../../../shared/utils/membership';
import { translate, useI18n } from '../../../shared/i18n';
import { useCurrentUser } from '../hooks';

const ACTIVE_BOOKING_STATUSES = new Set([
  'PENDING',
  'PENDING_APPROVAL',
  'APPROVED',
  'CONFIRMED',
  'CHECKED_IN',
]);

function formatDateTime(value: string, locale: 'vi' | 'en') {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return translate('common.notUpdated', locale);
  return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN', {
    weekday: 'short',
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

function statusClassName(status: string) {
  if (status === 'APPROVED' || status === 'CONFIRMED') {
    return 'bg-emerald-50 text-emerald-700 ring-emerald-200 dark:bg-emerald-950/50 dark:text-emerald-200 dark:ring-emerald-900';
  }
  if (status === 'PENDING' || status === 'PENDING_APPROVAL') {
    return 'bg-amber-50 text-amber-700 ring-amber-200 dark:bg-amber-950/50 dark:text-amber-200 dark:ring-amber-900';
  }
  return 'bg-slate-100 text-slate-700 ring-slate-200 dark:bg-slate-800 dark:text-slate-200 dark:ring-slate-700';
}

export function StudentDashboardPage() {
  const { locale, t } = useI18n();
  const currentUser = useCurrentUser();
  const activeMemberships = useMemo(
    () => getActiveMemberships(currentUser.data),
    [currentUser.data],
  );
  const hasMembership = activeMemberships.length > 0;
  const bookings = useMyBookings(hasMembership);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const activeBookings = useMemo(
    () => (bookings.data ?? [])
      .filter((booking) => ACTIVE_BOOKING_STATUSES.has(booking.status)
        && new Date(booking.endTime).getTime() >= now)
      .sort((first, second) => new Date(first.startTime).getTime() - new Date(second.startTime).getTime()),
    [bookings.data, now],
  );
  const pendingCount = (bookings.data ?? []).filter(
    (booking) => booking.status === 'PENDING' || booking.status === 'PENDING_APPROVAL',
  ).length;
  const displayName = currentUser.data?.fullName?.trim()
    || currentUser.data?.username
    || currentUser.data?.email
    || t('dashboard.you');

  if (currentUser.isLoading) {
    return (
      <section aria-label={t('dashboard.loadingOverview')} className="space-y-5">
        <div className="h-52 animate-pulse rounded-2xl bg-slate-200 dark:bg-slate-800" />
        <div className="grid gap-4 sm:grid-cols-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <div className="h-28 animate-pulse rounded-xl bg-slate-100 dark:bg-slate-800" key={index} />
          ))}
        </div>
      </section>
    );
  }

  if (currentUser.isError || !currentUser.data) {
    return (
      <section className="rounded-xl border border-red-200 bg-red-50 p-6 text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200" role="alert">
        <h2 className="font-semibold">{t('dashboard.loadErrorTitle')}</h2>
      </section>
    );
  }

  return (
    <section className="mx-auto max-w-7xl space-y-6">
      <header className="app-feature-banner overflow-hidden rounded-2xl px-5 py-7 sm:px-8 sm:py-9">
        <div className="flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
          <div className="min-w-0 max-w-3xl">
            <p className="text-sm font-semibold text-blue-200">{t('dashboard.studySpace')}</p>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-balance sm:text-3xl">
              {t('dashboard.greeting')}, {displayName}
            </h1>
          </div>
          <Link
            className="app-button-link w-fit shrink-0 text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 focus-visible:ring-offset-2"
            to={hasMembership ? '/app/other' : '/app/labs'}
          >
            {hasMembership ? t('dashboard.enterMyLab') : t('dashboard.discoverLabs')}
            <ArrowRight aria-hidden="true" className="h-4 w-4" />
          </Link>
        </div>
      </header>

      <section aria-labelledby="student-overview-title">
        <h2 className="sr-only" id="student-overview-title">{t('dashboard.systemStatus')}</h2>
        <div className="grid gap-3 sm:grid-cols-3">
          <SummaryItem icon={FlaskConical} label={t('dashboard.activeLabs')} value={String(activeMemberships.length)} />
          <SummaryItem icon={CalendarClock} label={t('dashboard.upcomingSessions')} value={String(activeBookings.length)} />
          <SummaryItem icon={CircleAlert} label={t('dashboard.pendingApproval')} value={String(pendingCount)} />
        </div>
      </section>

      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.35fr)_minmax(19rem,0.65fr)]">
        <section className="app-surface-elevated rounded-2xl p-5" aria-labelledby="upcoming-bookings-title">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="text-lg font-semibold text-slate-950 dark:text-white" id="upcoming-bookings-title">{t('dashboard.upcomingTitle')}</h2>
            </div>
            {hasMembership ? (
              <Link className="app-button-link text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500" to="/app/my-bookings">
                {t('dashboard.viewAll')} <ArrowRight aria-hidden="true" className="ml-1 h-4 w-4" />
              </Link>
            ) : null}
          </div>

          {!hasMembership ? (
            <EmptyDashboardState
              linkLabel={t('dashboard.viewLabs')}
              linkTo="/app/labs"
            />
          ) : bookings.isLoading ? (
            <div className="mt-5 space-y-3" aria-label={t('dashboard.loadingSessions')}>
              {Array.from({ length: 2 }).map((_, index) => <div className="h-20 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" key={index} />)}
            </div>
          ) : bookings.isError ? (
            <div className="mt-5 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200" role="alert">
              <p>{t('dashboard.sessionsLoadFailed')}</p>
              <button className="mt-2 min-h-11 font-semibold underline underline-offset-4" type="button" onClick={() => void bookings.refetch()}>{t('common.retry')}</button>
            </div>
          ) : activeBookings.length === 0 ? (
            <EmptyDashboardState
              linkLabel={t('dashboard.viewSlots')}
              linkTo="/app/other"
            />
          ) : (
            <ul className="mt-5 space-y-3">
              {activeBookings.slice(0, 3).map((booking) => (
                <li className="app-surface flex flex-col gap-3 rounded-xl p-3 sm:flex-row sm:items-center sm:justify-between" key={booking.id}>
                  <div className="flex min-w-0 items-center gap-3">
                    <span className="app-icon-badge flex h-10 w-10 shrink-0 items-center justify-center rounded-xl">
                      <CalendarClock aria-hidden="true" className="h-5 w-5" />
                    </span>
                    <div className="min-w-0">
                      <p className="break-words font-semibold text-slate-950 dark:text-white">{booking.labName ?? `${t('lab.shortName')} #${booking.labId}`}</p>
                      <p className="mt-1 text-sm tabular-nums text-slate-600 dark:text-slate-300">{formatDateTime(booking.startTime, locale)} – {formatDateTime(booking.endTime, locale)}</p>
                    </div>
                  </div>
                  <span className={`w-fit shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ${statusClassName(booking.status)}`}>
                    {getBookingStatusLabel(booking.status, locale)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <div className="space-y-6">
          <section className="app-surface-elevated rounded-2xl p-5" aria-labelledby="quick-actions-title">
            <h2 className="text-lg font-semibold text-slate-950 dark:text-white" id="quick-actions-title">{t('dashboard.quickAccess')}</h2>
            <div className="mt-4 grid gap-2">
              <QuickLink icon={FlaskConical} label={t('dashboard.findAndApply')} to="/app/labs" />
              {hasMembership ? <QuickLink icon={ScanFace} label={t('dashboard.faceProfile')} to="/app/profile" /> : null}
              {hasMembership ? <QuickLink icon={UsersRound} label={t('dashboard.myLabArea')} to="/app/other" /> : null}
              <QuickLink icon={Bot} label={t('dashboard.askAssistant')} to="/app/assistant" />
            </div>
          </section>

          <section className="app-surface-elevated rounded-2xl p-5" aria-labelledby="memberships-title">
            <h2 className="text-lg font-semibold text-slate-950 dark:text-white" id="memberships-title">{t('dashboard.yourLabs')}</h2>
            {activeMemberships.length ? (
              <ul className="mt-3 space-y-2">
                {activeMemberships.map((membership, index) => (
                  <li className="app-surface flex items-center gap-3 rounded-xl px-3 py-3" key={getMembershipLabId(membership) ?? index}>
                    <span className="app-icon-badge flex h-10 w-10 shrink-0 items-center justify-center rounded-xl"><FlaskConical aria-hidden="true" className="h-4 w-4" /></span>
                    <span className="min-w-0 truncate text-sm font-medium text-slate-900 dark:text-slate-100">{getMembershipLabName(membership)}</span>
                  </li>
                ))}
              </ul>
            ) : <p className="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">{t('dashboard.noActiveLabs')}</p>}
          </section>
        </div>
      </div>
    </section>
  );
}

interface SummaryItemProps {
  icon: typeof FlaskConical;
  label: string;
  value: string;
}

function SummaryItem({ icon: Icon, label, value }: SummaryItemProps) {
  return (
    <article className="app-stat-card app-surface-elevated flex min-w-0 items-center gap-4 rounded-2xl px-5 py-5">
      <span className="app-icon-badge flex h-11 w-11 shrink-0 items-center justify-center rounded-xl"><Icon aria-hidden="true" className="h-5 w-5" /></span>
      <div>
        <p className="text-2xl font-semibold tabular-nums text-slate-950 dark:text-white">{value}</p>
        <p className="text-sm text-slate-600 dark:text-slate-300">{label}</p>
      </div>
    </article>
  );
}

function QuickLink({ icon: Icon, label, to }: { icon: typeof FlaskConical; label: string; to: string }) {
  return (
    <Link className="app-action-card group flex min-h-12 items-center gap-3 px-3 py-2 text-sm font-semibold" to={to}>
      <span className="app-icon-badge flex h-9 w-9 shrink-0 items-center justify-center rounded-xl">
        <Icon aria-hidden="true" className="h-4 w-4" />
      </span>
      <span>{label}</span>
      <ArrowRight aria-hidden="true" className="ml-auto h-4 w-4 text-slate-400" />
    </Link>
  );
}

function EmptyDashboardState({ linkLabel, linkTo }: { linkLabel: string; linkTo: string }) {
  return (
    <div className="mt-5 rounded-lg border border-dashed border-slate-300 bg-slate-50 p-5 dark:border-slate-700 dark:bg-slate-950">
      <Link className="app-button-link text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500" to={linkTo}>
        {linkLabel} <ArrowRight aria-hidden="true" className="h-4 w-4" />
      </Link>
    </div>
  );
}
