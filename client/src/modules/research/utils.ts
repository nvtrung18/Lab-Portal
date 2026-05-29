import axios from 'axios';

import type { GroupStatus, MilestoneStatus, ProjectStatus, ResearchPriority, TopicStatus } from './types';

interface ApiErrorMessageContext {
  fallback?: string;
  forbidden?: string;
  notFound?: string;
}

export function getApiErrorMessage(error: unknown, context: string | ApiErrorMessageContext = {}) {
  const options = typeof context === 'string' ? { fallback: context } : context;

  if (axios.isAxiosError(error)) {
    if (!error.response) {
      return 'Không thể kết nối tới máy chủ. Vui lòng kiểm tra BE hoặc VITE_API_BASE_URL.';
    }

    const status = error.response.status;
    if (status === 401) {
      return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
    }
    if (status === 403) {
      return options.forbidden ?? 'Bạn không có quyền xem dữ liệu này.';
    }
    if (status === 404) {
      return options.notFound ?? 'Không tìm thấy dữ liệu.';
    }
    if (status >= 500) {
      return 'Hệ thống đang gặp sự cố. Vui lòng thử lại sau.';
    }
  }

  return options.fallback ?? 'Không thể tải dữ liệu.';
}

export function formatTopicStatus(status?: TopicStatus | null) {
  const labels: Record<TopicStatus, string> = {
    RECRUITING: 'Đang tuyển',
    ONGOING: 'Đang thực hiện',
    PAUSED: 'Tạm dừng',
    COMPLETED: 'Kết thúc',
  };
  return status ? labels[status] ?? status : 'Chưa cập nhật';
}

export function formatGroupStatus(status?: GroupStatus | null) {
  const labels: Record<GroupStatus, string> = {
    ACTIVE: 'Đang hoạt động',
    PAUSED: 'Tạm dừng',
    COMPLETED: 'Hoàn thành',
    ARCHIVED: 'Đã lưu trữ',
  };
  return status ? labels[status] ?? status : 'Chưa cập nhật';
}

export function formatProjectStatus(status?: ProjectStatus | null) {
  const labels: Record<ProjectStatus, string> = {
    DRAFT: 'Mới tạo',
    PLANNED: 'Mới tạo',
    ONGOING: 'Đang thực hiện',
    WAITING_REVIEW: 'Chờ phản biện',
    COMPLETED: 'Hoàn thành',
    ARCHIVED: 'Đã lưu trữ',
    CANCELLED: 'Đã hủy',
  };
  return status ? labels[status] ?? status : 'Chưa cập nhật';
}

export function formatPriority(priority?: ResearchPriority | null) {
  const labels: Record<ResearchPriority, string> = {
    HIGH: 'Cao',
    MEDIUM: 'Trung bình',
    LOW: 'Thấp',
  };
  return priority ? labels[priority] ?? priority : 'Chưa cập nhật';
}

export function formatGroupRole(role?: 'LEADER' | 'MEMBER' | null) {
  const labels = {
    LEADER: 'Trưởng nhóm',
    MEMBER: 'Thành viên',
  } as const;
  return role ? labels[role] ?? role : 'Chưa cập nhật';
}

export function formatMilestoneStatus(status?: MilestoneStatus | null) {
  const labels: Record<MilestoneStatus, string> = {
    NOT_STARTED: 'Chưa bắt đầu',
    IN_PROGRESS: 'Đang thực hiện',
    WAITING_REVIEW: 'Chờ duyệt',
    COMPLETED: 'Hoàn thành',
    OVERDUE: 'Quá hạn',
    CANCELLED: 'Đã hủy',
  };
  return status ? labels[status] ?? status : 'Chưa cập nhật';
}

export function isMilestoneOverdue(deadline?: string | null, status?: MilestoneStatus | null) {
  if (!deadline || status === 'COMPLETED' || status === 'CANCELLED') {
    return false;
  }
  const now = new Date();
  const today = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
  ].join('-');
  return deadline < today;
}

export function getStatusClass(status?: string | null) {
  switch (status) {
    case 'ACTIVE':
    case 'ONGOING':
    case 'IN_PROGRESS':
    case 'DOING':
    case 'RECRUITING':
      return 'bg-emerald-50 text-emerald-700 ring-emerald-200';
    case 'COMPLETED':
    case 'DONE':
      return 'bg-blue-50 text-blue-700 ring-blue-200';
    case 'PAUSED':
    case 'WAITING_REVIEW':
      return 'bg-amber-50 text-amber-700 ring-amber-200';
    case 'NEEDS_REVISION':
      return 'bg-orange-50 text-orange-700 ring-orange-200';
    case 'OVERDUE':
      return 'bg-red-50 text-red-700 ring-red-200';
    case 'ARCHIVED':
    case 'CANCELLED':
    case 'NOT_STARTED':
    case 'TODO':
      return 'bg-slate-100 text-slate-600 ring-slate-200';
    default:
      return 'bg-slate-100 text-slate-600 ring-slate-200';
  }
}

export function formatDate(value?: string | null) {
  if (!value) {
    return 'Chưa cập nhật';
  }
  return new Intl.DateTimeFormat('vi-VN').format(new Date(value));
}

export function formatReportSubmitterName(report: { submittedByName?: string | null }) {
  return report.submittedByName || 'Không rõ người nộp';
}

