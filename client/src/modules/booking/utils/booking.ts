import { getStoredLocale, translate, type Locale } from '../../../shared/i18n';

const BOOKING_STATUS_KEYS: Record<string, string> = {
  PENDING_APPROVAL: 'booking.status.pendingApproval',
  APPROVED: 'booking.status.approved',
  REJECTED: 'booking.status.rejected',
  CANCELLED_BY_STUDENT: 'booking.status.cancelledByStudent',
  CANCELLED_BY_MANAGER: 'booking.status.cancelledByManager',
  CHECKED_IN: 'booking.status.checkedIn',
  IN_PROGRESS: 'booking.status.inProgress',
  COMPLETED: 'booking.status.completed',
  NO_SHOW: 'booking.status.noShow',
  PENDING: 'booking.status.pendingApproval',
  CONFIRMED: 'booking.status.approved',
  CANCELLED: 'booking.status.cancelled',
  WAITLISTED: 'booking.status.waitlisted',
};

export function getBookingStatusLabel(status?: string | null, locale: Locale = getStoredLocale()) {
  return status
    ? translate(BOOKING_STATUS_KEYS[status] ?? '', locale, status)
    : translate('booking.status.notRegistered', locale);
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
