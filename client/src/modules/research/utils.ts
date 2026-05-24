import type { GroupStatus, ProjectStatus, ResearchPriority, TopicStatus } from './types';

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

export function getStatusClass(status?: string | null) {
  switch (status) {
    case 'ACTIVE':
    case 'ONGOING':
    case 'RECRUITING':
      return 'bg-emerald-50 text-emerald-700 ring-emerald-200';
    case 'COMPLETED':
      return 'bg-blue-50 text-blue-700 ring-blue-200';
    case 'PAUSED':
    case 'WAITING_REVIEW':
      return 'bg-amber-50 text-amber-700 ring-amber-200';
    case 'ARCHIVED':
    case 'CANCELLED':
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
