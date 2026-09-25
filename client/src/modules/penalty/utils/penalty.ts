import { getStoredLocale, translate, type Locale } from '../../../shared/i18n';

const PENALTY_TYPE_KEYS: Record<string, string> = {
  NO_SHOW: 'penalty.type.noShow',
  LATE_CHECKIN: 'penalty.type.lateCheckin',
  CLEANING_FAILED: 'penalty.type.cleaningFailed',
  LATE_CANCEL: 'penalty.type.lateCancel',
  CLEANING_NOT_DONE: 'penalty.type.cleaningNotDone',
  RULE_VIOLATION: 'penalty.type.ruleViolation',
  OTHER: 'penalty.type.other',
};

export function formatPenaltyType(type?: string | null, locale: Locale = getStoredLocale()) {
  return translate(PENALTY_TYPE_KEYS[type ?? ''] ?? 'penalty.type.other', locale);
}

export function formatPenaltyStatus(status?: string | null, locale: Locale = getStoredLocale()) {
  const key = {
    ACTIVE: 'penalty.status.active',
    RESOLVED: 'penalty.status.resolved',
    CANCELLED: 'penalty.status.cancelled',
    PAID: 'penalty.status.resolved',
  }[status ?? ''];
  return translate(key ?? 'common.notUpdated', locale);
}

export function formatComplaintStatus(status?: string | null, locale: Locale = getStoredLocale()) {
  const key = {
    PENDING: 'penalty.complaint.pending',
    APPROVED: 'penalty.complaint.approved',
    REJECTED: 'penalty.complaint.rejected',
    RESOLVED: 'penalty.complaint.resolved',
  }[status ?? ''];
  return translate(key ?? 'common.notUpdated', locale);
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

export function formatDateTime(value?: string | null, locale: Locale = getStoredLocale()) {
  if (!value) {
    return translate('common.notUpdated', locale);
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return translate('common.notUpdated', locale);
  }

  return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}
