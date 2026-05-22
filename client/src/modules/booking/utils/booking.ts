export const BOOKING_STATUS_LABELS: Record<string, string> = {
  PENDING_APPROVAL: 'Chờ phê duyệt',
  APPROVED: 'Đã phê duyệt',
  REJECTED: 'Không được phê duyệt',
  CANCELLED_BY_STUDENT: 'Sinh viên đã hủy',
  CANCELLED_BY_MANAGER: 'Quản lý đã hủy',
  CHECKED_IN: 'Đã xác nhận có mặt',
  PENDING: 'Chờ phê duyệt',
  CONFIRMED: 'Đã phê duyệt',
  CANCELLED: 'Đã hủy',
  WAITLISTED: 'Danh sách chờ',
};

export function getBookingStatusLabel(status?: string | null) {
  return status ? BOOKING_STATUS_LABELS[status] ?? status : 'Chưa đăng ký';
}

export function isCancellableBooking(status?: string | null, startTime?: string) {
  if (status !== 'PENDING_APPROVAL' && status !== 'APPROVED' && status !== 'PENDING' && status !== 'CONFIRMED') {
    return false;
  }

  if (!startTime) {
    return true;
  }

  const start = new Date(startTime);
  return Number.isNaN(start.getTime()) || start.getTime() > Date.now();
}
