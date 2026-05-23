export function formatPenaltyType(type?: string | null) {
  const labels: Record<string, string> = {
    NO_SHOW: 'Vắng không phép',
    LATE_CHECKIN: 'Check-in muộn',
    CLEANING_FAILED: 'Vệ sinh không đạt',
    LATE_CANCEL: 'Hủy đăng ký muộn',
    CLEANING_NOT_DONE: 'Chưa hoàn thành vệ sinh',
    RULE_VIOLATION: 'Vi phạm nội quy PTN',
    OTHER: 'Khác',
  };

  return labels[type ?? ''] ?? 'Khác';
}

export function formatPenaltyStatus(status?: string | null) {
  const labels: Record<string, string> = {
    ACTIVE: 'Đang hiệu lực',
    RESOLVED: 'Đã xử lý',
    CANCELLED: 'Đã hủy',
    PAID: 'Đã xử lý',
  };

  return labels[status ?? ''] ?? 'Chưa cập nhật';
}

export function formatComplaintStatus(status?: string | null) {
  const labels: Record<string, string> = {
    PENDING: 'Chờ xử lý',
    APPROVED: 'Đã chấp nhận',
    REJECTED: 'Không chấp nhận',
    RESOLVED: 'Đã xử lý',
  };

  return labels[status ?? ''] ?? 'Chưa cập nhật';
}

export function getPenaltyStatusClass(status?: string | null) {
  if (status === 'ACTIVE') {
    return 'bg-amber-50 text-amber-700 ring-amber-200';
  }
  if (status === 'CANCELLED') {
    return 'bg-slate-100 text-slate-600 ring-slate-200';
  }
  return 'bg-emerald-50 text-emerald-700 ring-emerald-200';
}

export function getComplaintStatusClass(status?: string | null) {
  if (status === 'PENDING') {
    return 'bg-sky-50 text-sky-700 ring-sky-200';
  }
  if (status === 'REJECTED') {
    return 'bg-red-50 text-red-700 ring-red-200';
  }
  return 'bg-emerald-50 text-emerald-700 ring-emerald-200';
}

export function formatDateTime(value?: string | null) {
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
