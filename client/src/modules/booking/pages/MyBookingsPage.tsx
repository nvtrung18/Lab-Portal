import { CalendarClock, History, QrCode } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import { ConfirmDialog, EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { translate, useI18n, type Locale } from '../../../shared/i18n';
import { CheckinButton, CheckinQrModal } from '../components';
import type { CheckinQrHistoryResponse } from '../api';
import { useCancelBooking, useMyBookings, useMyCheckinQrHistory } from '../hooks';
import { getBookingStatusLabel, isCancellableBooking } from '../utils';

type BookingView = 'UPCOMING' | 'HISTORY' | 'QR_HISTORY';

const TERMINAL_STATUSES = new Set([
  'REJECTED',
  'CANCELLED',
  'CANCELLED_BY_STUDENT',
  'CANCELLED_BY_MANAGER',
  'NO_SHOW',
  'COMPLETED',
]);

function formatDateTime(value: string | undefined, locale: Locale) {
  if (!value) {
    return translate('common.notUpdated', locale);
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return translate('common.notUpdated', locale);
  }
  return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function MyBookingsPage() {
  const { locale, t } = useI18n();
  const { data: bookings = [], isLoading, isError, refetch } = useMyBookings();
  const qrHistoryQuery = useMyCheckinQrHistory();
  const cancelBooking = useCancelBooking();
  const [view, setView] = useState<BookingView>('UPCOMING');
  const [selectedQr, setSelectedQr] = useState<CheckinQrHistoryResponse | null>(null);
  const [bookingPendingCancel, setBookingPendingCancel] = useState<number | null>(null);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const { upcomingBookings, bookingHistory } = useMemo(() => {
    const upcoming = bookings
      .filter((booking) => !TERMINAL_STATUSES.has(booking.status) && new Date(booking.endTime).getTime() >= now)
      .sort((first, second) => new Date(first.startTime).getTime() - new Date(second.startTime).getTime());
    const history = bookings
      .filter((booking) => TERMINAL_STATUSES.has(booking.status) || new Date(booking.endTime).getTime() < now)
      .sort((first, second) => new Date(second.startTime).getTime() - new Date(first.startTime).getTime());
    return { upcomingBookings: upcoming, bookingHistory: history };
  }, [bookings, now]);
  const visibleBookings = view === 'UPCOMING' ? upcomingBookings : bookingHistory;
  const qrHistory = qrHistoryQuery.data ?? [];
  const qrByBookingId = useMemo(
    () => new Map(qrHistory.map((item) => [item.bookingId, item])),
    [qrHistory],
  );

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <LoadingState />
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-lg border border-red-200 bg-white p-6 shadow-sm">
        <ErrorState onRetry={() => refetch()} />
      </section>
    );
  }

  return (
    <section className="mx-auto max-w-7xl space-y-5">
      <div className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800 sm:p-6">
        <h1 className="text-xl font-semibold tracking-tight text-slate-950 dark:text-white">{t('bookings.title')}</h1>
        <div className="mt-5 flex gap-2" role="tablist" aria-label={t('bookings.viewTabs')}>
          <BookingTab active={view === 'UPCOMING'} count={upcomingBookings.length} icon={CalendarClock} label={t('bookings.upcoming')} onClick={() => setView('UPCOMING')} />
          <BookingTab active={view === 'HISTORY'} count={bookingHistory.length} icon={History} label={t('bookings.history')} onClick={() => setView('HISTORY')} />
          <BookingTab active={view === 'QR_HISTORY'} count={qrHistory.length} icon={QrCode} label={t('bookings.qrHistory')} onClick={() => setView('QR_HISTORY')} />
        </div>
      </div>

      {view === 'QR_HISTORY' ? (
        qrHistoryQuery.isLoading ? <LoadingState /> : qrHistoryQuery.isError ? (
          <ErrorState onRetry={() => void qrHistoryQuery.refetch()}>{t('bookings.qrLoadFailed')}</ErrorState>
        ) : !qrHistory.length ? (
          <EmptyState>{t('bookings.qrEmpty')}</EmptyState>
        ) : (
          <div className={`grid gap-4 ${qrHistory.length > 1 ? 'xl:grid-cols-2' : ''}`}>
            {qrHistory.map((item) => {
              const booking = bookings.find((candidate) => candidate.id === item.bookingId);
              return (
                <article key={item.requestId} className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <h3 className="font-semibold text-slate-950 dark:text-white">{booking?.labName ?? `${t('bookings.session')} #${item.bookingId}`}</h3>
                      <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">{t('bookings.reason')}: {item.reason}</p>
                    </div>
                    <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700 dark:bg-slate-800 dark:text-slate-200">
                      {item.status === 'APPROVED' ? t('bookings.qrApproved') : item.status === 'REJECTED' ? t('bookings.qrRejected') : item.status === 'USED' ? t('bookings.qrUsed') : t('bookings.qrPending')}
                    </span>
                  </div>
                  <p className="mt-4 text-xs text-slate-500">{t('bookings.validUntil')}: {formatDateTime(item.expiresAt, locale)}</p>
                  {item.status === 'APPROVED' && item.token && booking ? (
                    <button className="mt-4 min-h-11 w-full rounded-md bg-blue-700 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-800" type="button" onClick={() => setSelectedQr(item)}>
                      {t('bookings.viewQr')}
                    </button>
                  ) : null}
                </article>
              );
            })}
          </div>
        )
      ) : !visibleBookings.length ? (
        <EmptyState>
          {view === 'UPCOMING'
            ? t('bookings.noUpcoming')
            : t('bookings.noHistory')}
        </EmptyState>
      ) : (
        <div className={`grid gap-4 ${visibleBookings.length > 1 ? 'xl:grid-cols-2' : ''}`}>
          {visibleBookings.map((booking) => {
            const canCancel = isCancellableBooking(booking.status, booking.startTime);
            return (
              <article key={booking.id} className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <h3 className="text-base font-semibold text-slate-950 dark:text-white">{booking.labName ?? t('lab.shortName')}</h3>
                    <p className="mt-1 text-sm tabular-nums text-slate-600 dark:text-slate-300">
                      {formatDateTime(booking.startTime, locale)} - {formatDateTime(booking.endTime, locale)}
                    </p>
                  </div>
                  <span className="w-fit rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700 dark:bg-slate-800 dark:text-slate-200">
                    {getBookingStatusLabel(booking.status)}
                  </span>
                </div>

                {booking.purpose ? (
                   <p className="mt-4 text-sm text-slate-600 dark:text-slate-300">{t('bookings.purpose')}: {booking.purpose}</p>
                ) : null}

                <div className={`mt-5 border-t border-slate-200 pt-4 dark:border-slate-800 ${canCancel ? 'grid gap-3 lg:grid-cols-[minmax(0,1fr)_14rem] lg:items-end' : ''}`}>
                  <CheckinButton booking={booking} qrHistory={qrByBookingId.get(booking.id)} />
                  {canCancel ? (
                    <button
                      className="min-h-11 rounded-md border border-red-200 bg-red-50 px-4 py-2 text-sm font-semibold text-red-700 transition hover:bg-red-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 disabled:opacity-60 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200"
                      disabled={cancelBooking.isPending}
                      type="button"
                      onClick={() => setBookingPendingCancel(booking.id)}
                    >
                       {t('bookings.cancel')}
                    </button>
                  ) : null}
                </div>
              </article>
            );
          })}
        </div>
      )}
      {selectedQr ? (() => {
        const booking = bookings.find((item) => item.id === selectedQr.bookingId);
        return booking ? (
          <CheckinQrModal
            booking={booking}
            qr={selectedQr}
            isOpen
            isCreating={false}
            onClose={() => setSelectedQr(null)}
          />
        ) : null;
      })() : null}
      <ConfirmDialog
         confirmLabel={t('bookings.cancel')}
        isOpen={bookingPendingCancel !== null}
        isPending={cancelBooking.isPending}
         message={t('bookings.confirmCancel')}
        onCancel={() => setBookingPendingCancel(null)}
        onConfirm={() => {
          if (bookingPendingCancel === null) return;
          cancelBooking.mutate(bookingPendingCancel, { onSuccess: () => setBookingPendingCancel(null) });
        }}
         title={t('bookings.confirmCancelTitle')}
      />
    </section>
  );
}

function BookingTab({
  active,
  count,
  icon: Icon,
  label,
  onClick,
}: {
  active: boolean;
  count: number;
  icon: typeof CalendarClock;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      aria-selected={active}
      className={`relative flex min-h-11 items-center gap-2 px-3 py-2 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 ${active ? 'text-slate-950 dark:text-white' : 'text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200'}`}
      role="tab"
      type="button"
      onClick={onClick}
    >
      <Icon aria-hidden="true" className="h-4 w-4" />
      {label}
      <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs tabular-nums dark:bg-slate-800">{count}</span>
      {active ? <span aria-hidden="true" className="absolute inset-x-2 -bottom-px h-0.5 bg-blue-600" /> : null}
    </button>
  );
}
