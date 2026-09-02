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
import { AttendanceChart } from '../../research/components/AttendanceChart';
import { useCurrentUser } from '../../user/hooks';
import { useLab, useLabDashboardStats } from '../hooks';

export function LabOverviewPage() {
  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const managedLabId = getManagedLabId(currentUser);
  const { data: managedLab, isLoading: isLoadingLab, isError } = useLab(managedLabId);
  const { data: stats, isLoading: isLoadingStats } = useLabDashboardStats(managedLabId);

  if (isLoadingUser || isLoadingLab || isLoadingStats) return <DashboardSkeleton />;

  if (!managedLabId) {
    return (
      <section className="rounded-xl border border-amber-200 bg-amber-50 p-6 text-amber-800 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200" role="alert">
        <h2 className="font-semibold">Chưa có phòng thí nghiệm được phân công</h2>
        <p className="mt-1 text-sm">Liên hệ quản trị viên để gán PTN trước khi sử dụng khu vực quản lý.</p>
      </section>
    );
  }

  if (isError || !managedLab) {
    return (
      <section className="rounded-xl border border-red-200 bg-red-50 p-6 text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200" role="alert">
        <h2 className="font-semibold">Không thể tải thông tin PTN</h2>
        <p className="mt-1 text-sm">Kiểm tra kết nối và tải lại trang để thử lại.</p>
      </section>
    );
  }

  const isOperating = managedLab.status === 'AVAILABLE' || managedLab.status === 'ACTIVE';
  const displayedStatus = isOperating
    ? 'Đang hoạt động'
    : managedLab.status === 'MAINTENANCE'
      ? 'Đang bảo trì'
      : 'Ngừng hoạt động';

  return (
    <section className="mx-auto max-w-7xl space-y-6">
      <header className="relative overflow-hidden rounded-2xl bg-slate-950 px-5 py-7 text-white shadow-lg shadow-slate-300/40 dark:shadow-none sm:px-8 sm:py-9">
        <div aria-hidden="true" className="absolute -right-16 -top-20 h-56 w-56 rounded-full bg-blue-500/20 blur-3xl" />
        <div className="relative flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-3xl">
            <div className="flex flex-wrap items-center gap-3">
              <p className="text-sm font-medium text-blue-200">Bảng điều hành PTN</p>
              <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ${isOperating ? 'bg-emerald-400/10 text-emerald-200 ring-emerald-400/30' : 'bg-amber-400/10 text-amber-200 ring-amber-400/30'}`}>
                <CheckCircle2 aria-hidden="true" className="h-3.5 w-3.5" />{displayedStatus}
              </span>
            </div>
            <h2 className="mt-2 text-2xl font-semibold tracking-tight text-balance sm:text-3xl">{managedLab.labName}</h2>
            <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-300 sm:text-base">
              {managedLab.description || 'Theo dõi ca sử dụng, điểm danh thành viên và các công việc vận hành trong ngày.'}
            </p>
            <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-sm text-slate-300">
              <span className="inline-flex items-center gap-2"><MapPin aria-hidden="true" className="h-4 w-4" />{managedLab.location || 'Chưa cập nhật địa điểm'}</span>
              <span className="inline-flex items-center gap-2"><UserRoundCheck aria-hidden="true" className="h-4 w-4" />Sức chứa {managedLab.capacity ?? 'chưa cập nhật'}</span>
            </div>
          </div>
          <Link className="inline-flex min-h-11 w-fit shrink-0 items-center gap-2 rounded-md bg-white px-4 py-2 text-sm font-semibold text-slate-950 transition hover:bg-blue-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-300 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-950" to="/app/checkin-scan">
            <ScanFace aria-hidden="true" className="h-4 w-4" />Mở trạm check-in
          </Link>
        </div>
      </header>

      {stats ? (
        <>
          <section aria-labelledby="today-overview-title">
            <div className="flex flex-wrap items-end justify-between gap-2">
              <div>
                <h2 className="text-lg font-semibold text-slate-950 dark:text-white" id="today-overview-title">Tình hình hôm nay</h2>
                <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">Số liệu vận hành cần theo dõi trong ngày.</p>
              </div>
              <p className="text-xs font-medium text-slate-500 dark:text-slate-400">Check-in hợp lệ từ trước 5 phút đến sau 10 phút</p>
            </div>
            <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <SummaryItem icon={CalendarClock} label="Ca sử dụng hôm nay" value={stats.todaySlots} />
              <SummaryItem icon={UserRoundCheck} label="Booking hôm nay" value={stats.todayBookings} />
              <SummaryItem icon={CheckCircle2} label="Tỷ lệ điểm danh" value={`${formatRate(stats.attendanceRate)}%`} />
              <SummaryItem icon={UserRoundCheck} label="Thành viên hoạt động" value={stats.memberCount} />
            </div>
          </section>

          <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.35fr)_minmax(19rem,0.65fr)]">
            <section className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800" aria-labelledby="attendance-title">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h2 className="text-lg font-semibold text-slate-950 dark:text-white" id="attendance-title">Điểm danh thành viên</h2>
                  <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">Theo dõi số buổi có mặt và tỷ lệ tham gia theo thành viên.</p>
                </div>
                <Link className="inline-flex min-h-11 items-center gap-1 text-sm font-semibold text-blue-700 hover:text-blue-800 focus-visible:rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:text-blue-300" to="/app/lab-members">
                  Xem thành viên <ArrowRight aria-hidden="true" className="h-4 w-4" />
                </Link>
              </div>
              <AttendanceChart byStudent={stats.attendanceByStudent} />
            </section>

            <div className="space-y-6">
              <section className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800" aria-labelledby="pending-title">
                <h2 className="text-lg font-semibold text-slate-950 dark:text-white" id="pending-title">Cần xử lý</h2>
                <div className="mt-4 space-y-2">
                  <ActionLink icon={Sparkles} label="Nhiệm vụ vệ sinh" value={stats.pendingCleaningTasks} to="/app/cleaning" />
                  <ActionLink icon={MessageSquareWarning} label="Khiếu nại" value={stats.pendingComplaints} to="/app/complaints" />
                  <ActionLink icon={Microscope} label="Đề tài đang thực hiện" value={stats.activeResearchProjects} to="/app/research" />
                </div>
              </section>

              <section className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800" aria-labelledby="quick-manager-title">
                <h2 className="text-lg font-semibold text-slate-950 dark:text-white" id="quick-manager-title">Truy cập nhanh</h2>
                <div className="mt-4 grid gap-2">
                  <QuickLink label="Quản lý khung giờ" to="/app/lab-slots" />
                  <QuickLink label="Duyệt hồ sơ ứng tuyển" to="/app/applications" />
                  <QuickLink label="Xem nhật ký vận hành" to="/app/operational-logs" />
                </div>
              </section>
            </div>
          </div>
        </>
      ) : null}
    </section>
  );
}

function DashboardSkeleton() {
  return (
    <section aria-label="Đang tải tổng quan quản lý" className="mx-auto max-w-7xl space-y-6">
      <div className="h-56 animate-pulse rounded-2xl bg-slate-200 dark:bg-slate-800" />
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, index) => <div className="h-28 animate-pulse rounded-xl bg-slate-100 dark:bg-slate-800" key={index} />)}
      </div>
    </section>
  );
}

function SummaryItem({ icon: Icon, label, value }: { icon: typeof CalendarClock; label: string; value: number | string }) {
  return (
    <article className="flex items-center gap-4 rounded-xl bg-white px-4 py-4 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800">
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-200"><Icon aria-hidden="true" className="h-5 w-5" /></span>
      <div><p className="text-2xl font-semibold tabular-nums text-slate-950 dark:text-white">{value}</p><p className="text-sm text-slate-600 dark:text-slate-300">{label}</p></div>
    </article>
  );
}

function ActionLink({ icon: Icon, label, value, to }: { icon: typeof Sparkles; label: string; value: number; to: string }) {
  return (
    <Link className="group flex min-h-14 items-center gap-3 rounded-lg bg-slate-50 px-3 py-2 transition hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:bg-slate-950 dark:hover:bg-slate-800" to={to}>
      <Icon aria-hidden="true" className="h-5 w-5 shrink-0 text-slate-500 group-hover:text-blue-700 dark:text-slate-400 dark:group-hover:text-blue-300" />
      <span className="min-w-0 flex-1 text-sm font-medium text-slate-700 dark:text-slate-200">{label}</span>
      <span className="rounded-full bg-white px-2.5 py-1 text-sm font-semibold tabular-nums text-slate-900 ring-1 ring-slate-200 dark:bg-slate-900 dark:text-white dark:ring-slate-700">{value}</span>
    </Link>
  );
}

function QuickLink({ label, to }: { label: string; to: string }) {
  return (
    <Link className="flex min-h-11 items-center justify-between gap-3 rounded-lg px-3 text-sm font-medium text-slate-700 transition hover:bg-slate-100 hover:text-slate-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:text-slate-200 dark:hover:bg-slate-800 dark:hover:text-white" to={to}>
      {label}<ArrowRight aria-hidden="true" className="h-4 w-4 shrink-0 text-slate-400" />
    </Link>
  );
}

function formatRate(value: number) {
  return Number.isFinite(value) ? (Number.isInteger(value) ? value : value.toFixed(1)) : 0;
}
