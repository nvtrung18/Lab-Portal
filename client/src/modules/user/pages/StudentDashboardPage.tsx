import {
  ArrowRight,
  Bot,
  CalendarClock,
  CircleAlert,
  FlaskConical,
  ScanFace,
  UsersRound,
} from 'lucide-react';
import { useMemo } from 'react';
import { Link } from 'react-router-dom';

import { useMyBookings } from '../../booking/hooks';
import { getBookingStatusLabel } from '../../booking/utils';
import {
  getActiveMemberships,
  getMembershipLabId,
  getMembershipLabName,
} from '../../../shared/utils/membership';
import { useCurrentUser } from '../hooks';

const ACTIVE_BOOKING_STATUSES = new Set([
  'PENDING',
  'PENDING_APPROVAL',
  'APPROVED',
  'CONFIRMED',
  'CHECKED_IN',
]);

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Chưa cập nhật';
  return new Intl.DateTimeFormat('vi-VN', {
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
  const currentUser = useCurrentUser();
  const activeMemberships = useMemo(
    () => getActiveMemberships(currentUser.data),
    [currentUser.data],
  );
  const hasMembership = activeMemberships.length > 0;
  const bookings = useMyBookings(hasMembership);
  const now = Date.now();
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
    || 'bạn';

  if (currentUser.isLoading) {
    return (
      <section aria-label="Đang tải tổng quan" className="space-y-5">
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
        <h2 className="font-semibold">Không thể tải tổng quan cá nhân</h2>
        <p className="mt-1 text-sm">Kiểm tra kết nối và tải lại trang để thử lại.</p>
      </section>
    );
  }

  return (
    <section className="mx-auto max-w-7xl space-y-6">
      <header className="relative overflow-hidden rounded-2xl bg-slate-950 px-5 py-7 text-white shadow-lg shadow-slate-300/40 dark:shadow-none sm:px-8 sm:py-9">
        <div aria-hidden="true" className="absolute -right-16 -top-20 h-56 w-56 rounded-full bg-blue-500/20 blur-3xl" />
        <div className="relative max-w-3xl">
          <p className="text-sm font-medium text-blue-200">Không gian học tập và nghiên cứu của bạn</p>
          <h2 className="mt-2 text-2xl font-semibold tracking-tight text-balance sm:text-3xl">
            Xin chào, {displayName}
          </h2>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-300 sm:text-base">
            {hasMembership
              ? 'Theo dõi ca sử dụng sắp tới, vào khu vực PTN và xử lý các công việc cần thiết từ một nơi.'
              : 'Bắt đầu bằng cách tìm phòng thí nghiệm phù hợp và gửi hồ sơ ứng tuyển.'}
          </p>
          <Link
            className="mt-6 inline-flex min-h-11 items-center gap-2 rounded-md bg-white px-4 py-2 text-sm font-semibold text-slate-950 transition hover:bg-blue-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-300 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-950"
            to={hasMembership ? '/app/other' : '/app/labs'}
          >
            {hasMembership ? 'Vào PTN của tôi' : 'Khám phá phòng thí nghiệm'}
            <ArrowRight aria-hidden="true" className="h-4 w-4" />
          </Link>
        </div>
      </header>

      <section aria-labelledby="student-overview-title">
        <h2 className="sr-only" id="student-overview-title">Tình trạng sử dụng hệ thống</h2>
        <div className="grid gap-3 sm:grid-cols-3">
          <SummaryItem icon={FlaskConical} label="PTN đang tham gia" value={String(activeMemberships.length)} />
          <SummaryItem icon={CalendarClock} label="Ca sắp tới" value={String(activeBookings.length)} />
          <SummaryItem icon={CircleAlert} label="Đang chờ duyệt" value={String(pendingCount)} />
        </div>
      </section>

      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.35fr)_minmax(19rem,0.65fr)]">
        <section className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800" aria-labelledby="upcoming-bookings-title">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="text-lg font-semibold text-slate-950 dark:text-white" id="upcoming-bookings-title">Ca sử dụng sắp tới</h2>
              <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">Các ca còn hiệu lực được sắp theo thời gian gần nhất.</p>
            </div>
            {hasMembership ? (
              <Link className="inline-flex min-h-11 items-center text-sm font-semibold text-blue-700 hover:text-blue-800 focus-visible:rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:text-blue-300" to="/app/my-bookings">
                Xem tất cả <ArrowRight aria-hidden="true" className="ml-1 h-4 w-4" />
              </Link>
            ) : null}
          </div>

          {!hasMembership ? (
            <EmptyDashboardState
              description="Sau khi được duyệt vào một PTN, các ca sử dụng của bạn sẽ xuất hiện tại đây."
              linkLabel="Xem danh sách PTN"
              linkTo="/app/labs"
            />
          ) : bookings.isLoading ? (
            <div className="mt-5 space-y-3" aria-label="Đang tải ca sử dụng">
              {Array.from({ length: 2 }).map((_, index) => <div className="h-20 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" key={index} />)}
            </div>
          ) : bookings.isError ? (
            <div className="mt-5 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200" role="alert">
              <p>Không thể tải danh sách ca sử dụng.</p>
              <button className="mt-2 min-h-11 font-semibold underline underline-offset-4" type="button" onClick={() => void bookings.refetch()}>Thử lại</button>
            </div>
          ) : activeBookings.length === 0 ? (
            <EmptyDashboardState
              description="Bạn chưa có ca sử dụng nào sắp tới. Hãy vào PTN của tôi để xem các khung giờ khả dụng."
              linkLabel="Xem khung giờ"
              linkTo="/app/other"
            />
          ) : (
            <ul className="mt-5 divide-y divide-slate-200 dark:divide-slate-800">
              {activeBookings.slice(0, 3).map((booking) => (
                <li className="flex flex-col gap-3 py-4 first:pt-0 last:pb-0 sm:flex-row sm:items-center sm:justify-between" key={booking.id}>
                  <div className="min-w-0">
                    <p className="truncate font-semibold text-slate-950 dark:text-white">{booking.labName ?? `PTN #${booking.labId}`}</p>
                    <p className="mt-1 text-sm tabular-nums text-slate-600 dark:text-slate-300">{formatDateTime(booking.startTime)} – {formatDateTime(booking.endTime)}</p>
                  </div>
                  <span className={`w-fit shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ${statusClassName(booking.status)}`}>
                    {getBookingStatusLabel(booking.status)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <div className="space-y-6">
          <section className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800" aria-labelledby="quick-actions-title">
            <h2 className="text-lg font-semibold text-slate-950 dark:text-white" id="quick-actions-title">Truy cập nhanh</h2>
            <div className="mt-4 grid gap-2">
              <QuickLink icon={FlaskConical} label="Tìm và ứng tuyển PTN" to="/app/labs" />
              {hasMembership ? <QuickLink icon={ScanFace} label="Đăng ký hồ sơ khuôn mặt" to="/app/face-profile" /> : null}
              {hasMembership ? <QuickLink icon={UsersRound} label="Khu vực PTN của tôi" to="/app/other" /> : null}
              <QuickLink icon={Bot} label="Hỏi trợ lý Lab Portal" to="/app/assistant" />
            </div>
          </section>

          <section className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800" aria-labelledby="memberships-title">
            <h2 className="text-lg font-semibold text-slate-950 dark:text-white" id="memberships-title">Phòng thí nghiệm của bạn</h2>
            {activeMemberships.length ? (
              <ul className="mt-3 space-y-2">
                {activeMemberships.map((membership, index) => (
                  <li className="flex items-center gap-3 rounded-lg bg-slate-50 px-3 py-3 dark:bg-slate-950" key={getMembershipLabId(membership) ?? index}>
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-200"><FlaskConical aria-hidden="true" className="h-4 w-4" /></span>
                    <span className="min-w-0 truncate text-sm font-medium text-slate-900 dark:text-slate-100">{getMembershipLabName(membership)}</span>
                  </li>
                ))}
              </ul>
            ) : <p className="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">Bạn chưa là thành viên hoạt động của PTN nào.</p>}
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
    <article className="flex items-center gap-4 rounded-xl bg-white px-4 py-4 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800">
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-200"><Icon aria-hidden="true" className="h-5 w-5" /></span>
      <div>
        <p className="text-2xl font-semibold tabular-nums text-slate-950 dark:text-white">{value}</p>
        <p className="text-sm text-slate-600 dark:text-slate-300">{label}</p>
      </div>
    </article>
  );
}

function QuickLink({ icon: Icon, label, to }: { icon: typeof FlaskConical; label: string; to: string }) {
  return (
    <Link className="group flex min-h-12 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100 hover:text-slate-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:text-slate-200 dark:hover:bg-slate-800 dark:hover:text-white" to={to}>
      <Icon aria-hidden="true" className="h-5 w-5 shrink-0 text-slate-500 group-hover:text-blue-700 dark:text-slate-400 dark:group-hover:text-blue-300" />
      <span>{label}</span>
      <ArrowRight aria-hidden="true" className="ml-auto h-4 w-4 text-slate-400" />
    </Link>
  );
}

function EmptyDashboardState({ description, linkLabel, linkTo }: { description: string; linkLabel: string; linkTo: string }) {
  return (
    <div className="mt-5 rounded-lg border border-dashed border-slate-300 bg-slate-50 p-5 dark:border-slate-700 dark:bg-slate-950">
      <p className="max-w-xl text-sm leading-6 text-slate-600 dark:text-slate-300">{description}</p>
      <Link className="mt-3 inline-flex min-h-11 items-center gap-1 text-sm font-semibold text-blue-700 hover:text-blue-800 focus-visible:rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:text-blue-300" to={linkTo}>
        {linkLabel} <ArrowRight aria-hidden="true" className="h-4 w-4" />
      </Link>
    </div>
  );
}
