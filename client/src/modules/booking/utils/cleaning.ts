export function formatCleaningStatus(status?: string | null) {
  const labels: Record<string, string> = {
    PENDING: 'Chưa phân công',
    ASSIGNED: 'Đã phân công',
    DONE: 'Đã hoàn thành',
    COMPLETED: 'Đã hoàn thành',
    CANCELLED: 'Đã hủy',
  };

  return labels[status ?? ''] ?? 'Chưa cập nhật';
}

export function getCleaningStatusClass(status?: string | null) {
  if (status === 'ASSIGNED') {
    return 'bg-sky-50 text-sky-700 ring-sky-200';
  }
  if (status === 'DONE' || status === 'COMPLETED') {
    return 'bg-emerald-50 text-emerald-700 ring-emerald-200';
  }
  if (status === 'CANCELLED') {
    return 'bg-slate-100 text-slate-600 ring-slate-200';
  }
  return 'bg-amber-50 text-amber-700 ring-amber-200';
}
