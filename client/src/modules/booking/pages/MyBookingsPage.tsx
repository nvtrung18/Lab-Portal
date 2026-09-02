import { CalendarClock, History } from 'lucide-react';
import { useMemo, useState } from 'react';

import { EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { CheckinButton } from '../components';
import { useCancelBooking, useMyBookings } from '../hooks';
import { getBookingStatusLabel, isCancellableBooking } from '../utils';

type BookingView = 'UPCOMING' | 'HISTORY';

const TERMINAL_STATUSES = new Set([
  'REJECTED',
  'CANCELLED',
  'CANCELLED_BY_STUDENT',
  'CANCELLED_BY_MANAGER',
  'NO_SHOW',
  'COMPLETED',
]);

function formatDateTime(value?: string) {
  if (!value) {
    return 'Chưa cập nhật';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return 'Chưa cập nhật';
  }
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function MyBookingsPage() {
  const { data: bookings = [], isLoading, isError, refetch } = useMyBookings();
  const cancelBooking = useCancelBooking();
  const [view, setView] = useState<BookingView>('UPCOMING');
  const { upcomingBookings, bookingHistory } = useMemo(() => {
    const now = Date.now();
    const upcoming = bookings
      .filter((booking) => !TERMINAL_STATUSES.has(booking.status) && new Date(booking.endTime).getTime() >= now)
      .sort((first, second) => new Date(first.startTime).getTime() - new Date(second.startTime).getTime());
    const history = bookings
      .filter((booking) => TERMINAL_STATUSES.has(booking.status) || new Date(booking.endTime).getTime() < now)
      .sort((first, second) => new Date(second.startTime).getTime() - new Date(first.startTime).getTime());
    return { upcomingBookings: upcoming, bookingHistory: history };
  }, [bookings]);
  const visibleBookings = view === 'UPCOMING' ? upcomingBookings : bookingHistory;

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
        <h2 className="text-xl font-semibold tracking-tight text-slate-950 dark:text-white">Ca sử dụng của tôi</h2>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600 dark:text-slate-300">
          Theo dõi trạng thái đăng ký và thời gian sử dụng. Khi đến PTN, quản lý sẽ xác nhận có mặt bằng Face ID hoặc QR trên thiết bị quản lý.
        </p>
        <div className="mt-5 flex gap-2 border-b border-slate-200 dark:border-slate-800" role="tablist" aria-label="Phân loại ca sử dụng">
          <BookingTab active={view === 'UPCOMING'} count={upcomingBookings.length} icon={CalendarClock} label="Sắp tới" onClick={() => setView('UPCOMING')} />
          <BookingTab active={view === 'HISTORY'} count={bookingHistory.length} icon={History} label="Lịch sử" onClick={() => setView('HISTORY')} />
        </div>
      </div>

      {!visibleBookings.length ? (
        <EmptyState>
          {view === 'UPCOMING'
            ? 'Bạn chưa có ca sử dụng nào sắp tới.'
            : 'Bạn chưa có lịch sử sử dụng PTN.'}
        </EmptyState>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {visibleBookings.map((booking) => {
            const canCancel = isCancellableBooking(booking.status, booking.startTime);
            return (
              <article key={booking.id} className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <h3 className="text-base font-semibold text-slate-950 dark:text-white">{booking.labName ?? 'PTN'}</h3>
                    <p className="mt-1 text-sm tabular-nums text-slate-600 dark:text-slate-300">
                      {formatDateTime(booking.startTime)} - {formatDateTime(booking.endTime)}
                    </p>
                  </div>
                  <span className="w-fit rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700 dark:bg-slate-800 dark:text-slate-200">
                    {getBookingStatusLabel(booking.status)}
                  </span>
                </div>

                {booking.purpose ? (
                  <p className="mt-4 text-sm text-slate-600 dark:text-slate-300">Mục đích: {booking.purpose}</p>
                ) : null}

                <div className="mt-5 grid gap-3 border-t border-slate-200 pt-4 dark:border-slate-800 sm:grid-cols-2">
                  <CheckinButton booking={booking} />
                  {canCancel ? (
                    <button
                      className="min-h-11 rounded-md border border-red-200 bg-red-50 px-4 py-2 text-sm font-semibold text-red-700 transition hover:bg-red-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 disabled:opacity-60 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200"
                      disabled={cancelBooking.isPending}
                      type="button"
                      onClick={() => {
                        if (window.confirm('Bạn có chắc muốn hủy đăng ký sử dụng khung giờ này không?')) {
                          cancelBooking.mutate(booking.id);
                        }
                      }}
                    >
                      Hủy đăng ký
                    </button>
                  ) : null}
                </div>
              </article>
            );
          })}
        </div>
      )}
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
