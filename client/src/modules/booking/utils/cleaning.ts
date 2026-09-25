import { getStoredLocale, translate } from '../../../shared/i18n';

export function formatCleaningStatus(status?: string | null) {
  const keys: Record<string, string> = {
    PENDING: 'cleaning.pending',
    ASSIGNED: 'cleaning.assigned',
    DONE: 'cleaning.done',
    COMPLETED: 'cleaning.done',
    CANCELLED: 'booking.status.cancelled',
  };

  const locale = getStoredLocale();
  return translate(keys[status ?? ''] ?? 'common.notUpdated', locale, status ?? undefined);
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
