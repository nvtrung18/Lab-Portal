import { getManagedLabId } from '../../../shared/utils/membership';
import { useCurrentUser } from '../../user/hooks';
import { useLab, useLabDashboardStats } from '../hooks';
import { StatCard } from '../../research/components/StatCard';
import { AttendanceChart } from '../../research/components/AttendanceChart';

export function LabOverviewPage() {
  const { data: currentUser, isLoading: isLoadingUser } = useCurrentUser();
  const managedLabId = getManagedLabId(currentUser);
  const { data: managedLab, isLoading: isLoadingLab, isError } = useLab(managedLabId);
  const { data: stats, isLoading: isLoadingStats } = useLabDashboardStats(managedLabId);

  if (isLoadingUser || isLoadingLab || isLoadingStats) {
    return (
      <section className="space-y-6">
        <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
          <div className="h-6 w-40 animate-pulse rounded bg-slate-200" />
          <div className="mt-6 grid gap-4 md:grid-cols-2">
            <div className="h-16 animate-pulse rounded bg-slate-100" />
            <div className="h-16 animate-pulse rounded bg-slate-100" />
            <div className="h-16 animate-pulse rounded bg-slate-100" />
            <div className="h-16 animate-pulse rounded bg-slate-100" />
          </div>
        </section>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <StatCard key={index} title="Đang tải" value="0" loading />
          ))}
        </div>
      </section>
    );
  }

  if (!managedLabId) {
    return (
      <section className="rounded-lg border border-amber-200 bg-white p-6 text-sm text-amber-700 shadow-sm">
        Tài khoản quản lý hiện chưa được phân công PTN.
      </section>
    );
  }

  if (isError || !managedLab) {
    return (
      <section className="rounded-lg border border-red-200 bg-white p-6 text-sm text-red-700 shadow-sm">
        Không thể tải thông tin PTN đang quản lý.
      </section>
    );
  }

  const displayedStatus =
    managedLab.status === 'AVAILABLE' || managedLab.status === 'ACTIVE'
      ? 'Đang hoạt động'
      : managedLab.status === 'MAINTENANCE'
        ? 'Đang bảo trì'
        : 'Ngừng hoạt động';

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
          Quản lý PTN
        </p>
        <h2 className="mt-1 text-xl font-semibold text-slate-950">Tổng quan PTN</h2>
        <p className="mt-2 text-sm text-slate-600">
          Theo dõi tình hình sử dụng, điểm danh và vận hành phòng thí nghiệm.
        </p>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-3 border-b border-slate-200 pb-5 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h3 className="text-lg font-semibold text-slate-950">
              {managedLab.labName}
            </h3>
            <p className="mt-1 text-sm text-slate-600">
              {managedLab.description || 'PTN chưa có mô tả.'}
            </p>
          </div>
          <span className="inline-flex w-fit rounded-full bg-emerald-50 px-2 py-1 text-xs font-semibold text-emerald-700 ring-1 ring-emerald-200">
            {displayedStatus}
          </span>
        </div>

        <dl className="mt-6 grid gap-5 md:grid-cols-2">
          <div>
            <dt className="text-sm font-medium text-slate-500">Quản lý</dt>
            <dd className="mt-1 text-sm text-slate-950">
              {managedLab.manager?.fullName || managedLab.manager?.email || 'Chưa phân công'}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-slate-500">Khoa / đơn vị</dt>
            <dd className="mt-1 text-sm text-slate-950">
              {managedLab.department || 'Chưa cập nhật'}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-slate-500">Địa điểm</dt>
            <dd className="mt-1 text-sm text-slate-950">
              {managedLab.location || 'Chưa cập nhật'}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-slate-500">Sức chứa</dt>
            <dd className="mt-1 text-sm text-slate-950">
              {managedLab.capacity ?? 'Chưa cập nhật'}
            </dd>
          </div>
        </dl>
      </div>

      {stats && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard
              title="Tổng số thành viên PTN"
              value={stats.memberCount}
              description="Thành viên đang hoạt động"
            />
            <StatCard
              title="Số ca sử dụng hôm nay"
              value={stats.todaySlots}
              description="Tổng số ca cấu hình"
            />
            <StatCard
              title="Số booking hôm nay"
              value={stats.todayBookings}
              description="Lượt đặt sử dụng hôm nay"
            />
            <StatCard
              title="Tỷ lệ điểm danh PTN"
              value={Number.isInteger(stats.attendanceRate) ? stats.attendanceRate : stats.attendanceRate.toFixed(1)}
              suffix="%"
              description="Tỷ lệ trung bình toàn lab"
            />
            <StatCard
              title="Nhiệm vụ vệ sinh chờ xử lý"
              value={stats.pendingCleaningTasks}
              description="Công việc chưa hoàn thành"
            />
            <StatCard
              title="Khiếu nại đang chờ xử lý"
              value={stats.pendingComplaints}
              description="Phản hồi từ sinh viên"
            />
            <StatCard
              title="Số đề tài NCKH đang thực hiện"
              value={stats.activeResearchProjects}
              description="Dự án nghiên cứu đang chạy"
            />
          </div>

          <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <h3 className="text-base font-semibold text-slate-950">Điểm danh sinh viên</h3>
            <AttendanceChart byStudent={stats.attendanceByStudent as any} />
          </section>
        </>
      )}
    </section>
  );
}

