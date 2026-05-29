import { useMemo } from 'react';

import { EmptyState, ErrorState } from '../../../shared/components';
import { AdminStatCard } from '../components';
import { useAdminDashboardStats } from '../hooks';
import type { AdminDashboardStats } from '../api';

interface StatItem {
  title: string;
  value: number | string | null | undefined;
  description?: string;
  suffix?: string;
}

interface StatSection {
  title: string;
  items: StatItem[];
}

const numberFormatter = new Intl.NumberFormat('vi-VN');

function toFiniteNumber(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function formatCount(value: unknown) {
  return numberFormatter.format(toFiniteNumber(value));
}

function formatAverageScore(value: number | null | undefined) {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return 'Chưa có';
  }

  return numberFormatter.format(value);
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

function buildSections(stats: AdminDashboardStats): StatSection[] {
  return [
    {
      title: 'Người dùng',
      items: [
        {
          title: 'Tổng số người dùng',
          value: formatCount(stats.users?.total),
          description: 'Tài khoản đã đăng ký thành công',
        },
        {
          title: 'Tài khoản hoạt động',
          value: formatCount(stats.users?.active),
          description: 'Người dùng đang có trạng thái ACTIVE',
        },
        {
          title: 'Tài khoản bị khóa',
          value: formatCount(stats.users?.banned),
          description: 'Tài khoản đang bị khóa hoặc tạm ngừng',
        },
        {
          title: 'Manager chưa được gán PTN',
          value: formatCount(stats.users?.unassignedManagers),
          description: 'Quản lý PTN chưa phụ trách phòng nào',
        },
      ],
    },
    {
      title: 'Phòng thí nghiệm',
      items: [
        {
          title: 'Tổng số PTN',
          value: formatCount(stats.labs?.total),
          description: 'Toàn bộ phòng thí nghiệm trong hệ thống',
        },
        {
          title: 'PTN đang hoạt động',
          value: formatCount(stats.labs?.active),
          description: 'Phòng thí nghiệm sẵn sàng vận hành',
        },
        {
          title: 'PTN tạm ngừng',
          value: formatCount(stats.labs?.inactive),
          description: 'Phòng thí nghiệm đang ngừng hoạt động',
        },
        {
          title: 'PTN chưa có manager',
          value: formatCount(stats.labs?.withoutManager),
          description: 'Phòng chưa được phân công quản lý',
        },
      ],
    },
    {
      title: 'Vận hành',
      items: [
        {
          title: 'Hồ sơ ứng tuyển chờ duyệt',
          value: formatCount(stats.operations?.pendingApplications),
          description: 'Hồ sơ đang chờ quản lý PTN duyệt',
        },
        {
          title: 'Ca sử dụng hôm nay',
          value: formatCount(stats.operations?.todaySlots),
          description: 'Ca sử dụng bắt đầu trong ngày',
        },
        {
          title: 'Booking hôm nay',
          value: formatCount(stats.operations?.todayBookings),
          description: 'Lượt đặt phòng diễn ra trong ngày',
        },
        {
          title: 'Khiếu nại chờ xử lý',
          value: formatCount(stats.operations?.pendingComplaints),
          description: 'Khiếu nại đang chờ quản lý xử lý',
        },
        {
          title: 'Nhiệm vụ vệ sinh chờ hoàn thành',
          value: formatCount(stats.operations?.pendingCleaningTasks),
          description: 'Nhiệm vụ chưa hoàn tất',
        },
      ],
    },
    {
      title: 'Nghiên cứu khoa học',
      items: [
        {
          title: 'Đề tài đang thực hiện',
          value: formatCount(stats.research?.activeProjects),
          description: 'Đề tài đang ở trạng thái triển khai',
        },
        {
          title: 'Nhóm nghiên cứu đang hoạt động',
          value: formatCount(stats.research?.activeGroups),
          description: 'Nhóm nghiên cứu còn hoạt động',
        },
        {
          title: 'Báo cáo chờ duyệt',
          value: formatCount(stats.research?.pendingReports),
          description: 'Báo cáo chờ quản lý PTN duyệt',
        },
        {
          title: 'Sản phẩm đã nộp',
          value: formatCount(stats.research?.submittedProducts),
          description: 'Sản phẩm nghiên cứu đã được nộp',
        },
        {
          title: 'Điểm đánh giá trung bình',
          value: formatAverageScore(stats.research?.averageEvaluationScore),
          suffix:
            typeof stats.research?.averageEvaluationScore === 'number' &&
            Number.isFinite(stats.research.averageEvaluationScore)
              ? 'điểm'
              : undefined,
          description: 'Điểm trung bình từ các đánh giá đã ghi nhận',
        },
      ],
    },
  ];
}

export function AdminDashboardPage() {
  const { data: stats, isError, isLoading, refetch } = useAdminDashboardStats();
  const sections = useMemo(() => (stats ? buildSections(stats) : []), [stats]);

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
        <DashboardHeader />
        <p className="mt-6 text-sm text-slate-300">Đang tải dữ liệu tổng quan...</p>
        <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, index) => (
            <AdminStatCard key={index} loading title="Đang tải" value={0} />
          ))}
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
        <DashboardHeader />
        <ErrorState
          className="mt-6 border-red-900/70 bg-red-950/40 text-red-200"
          onRetry={() => void refetch()}
        >
          Không thể tải dữ liệu dashboard.
        </ErrorState>
      </section>
    );
  }

  if (!hasAnyDashboardData(stats)) {
    return (
      <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
        <DashboardHeader />
        <EmptyState className="mt-6 border-slate-700 bg-slate-800 text-slate-300">
          Chưa có dữ liệu thống kê.
        </EmptyState>
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-slate-800 bg-slate-900 p-6 shadow-sm">
      <DashboardHeader />
      <div className="mt-8 space-y-8">
        {sections.map((section) => (
          <section key={section.title}>
            <h3 className="text-base font-semibold text-white">{section.title}</h3>
            <div className="mt-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-4 2xl:grid-cols-5">
              {section.items.map((item) => (
                <AdminStatCard
                  key={item.title}
                  title={item.title}
                  value={item.value}
                  description={item.description}
                  suffix={item.suffix}
                />
              ))}
            </div>
          </section>
        ))}
      </div>
    </section>
  );
}

function DashboardHeader() {
  return (
    <header>
      <h2 className="text-xl font-semibold text-white">Tổng quan quản trị</h2>
      <p className="mt-2 text-sm text-slate-300">Theo dõi trạng thái vận hành toàn hệ thống.</p>
    </header>
  );
}
