import type { LabSlot } from '../types';
import type { BookingResponse } from '../api';
import { getBookingStatusLabel, getSlotStatusLabel, isCancellableBooking } from '../utils';
import { translate, useI18n } from '../../../shared/i18n';

interface SlotCardProps {
  slot: LabSlot;
  showLabName?: boolean;
  mode?: 'readonly' | 'student' | 'manager';
  userBooking?: BookingResponse | null;
  isMutating?: boolean;
  onRegister?: (slot: LabSlot) => void;
  onCancelBooking?: (booking: BookingResponse) => void;
  onViewDetail?: (slot: LabSlot) => void;
  onCancelSlot?: (slot: LabSlot) => void;
  historical?: boolean;
}

const badgeStyles: Record<string, string> = {
  AVAILABLE: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  FULL: 'bg-rose-50 text-rose-700 ring-rose-200',
  CLOSED: 'bg-slate-100 text-slate-700 ring-slate-200',
  INACTIVE: 'bg-amber-50 text-amber-700 ring-amber-200',
  MAINTENANCE: 'bg-orange-50 text-orange-700 ring-orange-200',
  IN_PROGRESS: 'bg-blue-50 text-blue-700 ring-blue-200',
  EXPIRED: 'bg-slate-100 text-slate-600 ring-slate-200',
  CANCELLED: 'bg-red-50 text-red-700 ring-red-200',
};

function formatDate(value: string, locale: 'vi' | 'en') {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return translate('common.notUpdated', locale);
  }

  return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(date);
}

function formatTime(value: string, locale: 'vi' | 'en') {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return '--:--';
  }

  return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}

function isPastSlot(slot: LabSlot) {
  const start = new Date(slot.startTime);
  return !Number.isNaN(start.getTime()) && start.getTime() <= Date.now();
}

export function SlotCard({
  slot,
  showLabName = false,
  mode = 'readonly',
  userBooking,
  isMutating = false,
  onRegister,
  onCancelBooking,
  onViewDetail,
  onCancelSlot,
  historical = false,
}: SlotCardProps) {
  const { locale, t } = useI18n();
  const badgeClass = badgeStyles[slot.status] ?? 'bg-slate-100 text-slate-700 ring-slate-200';
  const canRegister =
    mode === 'student' &&
    !userBooking &&
    slot.status === 'AVAILABLE' &&
    !isPastSlot(slot);
  const canCancelBooking =
    mode === 'student' &&
    userBooking &&
    isCancellableBooking(userBooking.status, slot.startTime);
  const canCancelSlot =
    mode === 'manager' &&
    !historical &&
    slot.status !== 'CANCELLED' &&
    !isPastSlot(slot);

  return (
    <article className="min-w-0 rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-medium text-slate-500">{formatDate(slot.startTime, locale)}</p>
          <h3 className="mt-1 whitespace-nowrap text-base font-semibold text-slate-950 sm:text-lg">
            {formatTime(slot.startTime, locale)} - {formatTime(slot.endTime, locale)}
          </h3>
          {showLabName && slot.labName ? (
            <p className="mt-1 truncate text-sm text-slate-600">{slot.labName}</p>
          ) : null}
        </div>
        <span
          className={[
            'inline-flex shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ring-1',
            badgeClass,
          ].join(' ')}
        >
          {getSlotStatusLabel(slot.status, locale)}
        </span>
      </div>

      <dl className="mt-5 grid grid-cols-2 gap-4 text-sm">
        <div>
          <dt className="text-slate-500">{t('slot.capacity')}</dt>
          <dd className="mt-1 font-semibold text-slate-950 dark:text-slate-100">{slot.capacity}</dd>
        </div>
        {slot.hasBookedCount ? (
          <div>
            <dt className="text-slate-500">{t('slot.approved')}</dt>
            <dd className="mt-1 font-semibold text-slate-950 dark:text-slate-100">{slot.approvedCount}</dd>
          </div>
        ) : null}
        {slot.hasBookedCount ? (
          <div>
            <dt className="text-slate-500">{t('slot.checkedIn')}</dt>
            <dd className="mt-1 font-semibold text-slate-950 dark:text-slate-100">{slot.checkedInCount}</dd>
          </div>
        ) : null}
        {slot.remainingCapacity !== null ? (
          <div>
            <dt className="text-slate-500">{t('slot.remaining')}</dt>
            <dd className="mt-1 font-semibold text-slate-950 dark:text-slate-100">{slot.remainingCapacity}</dd>
          </div>
        ) : null}
        <div>
          <dt className="text-slate-500">{t('slot.statusLabel')}</dt>
          <dd className="mt-1 font-semibold text-slate-950 dark:text-slate-100">{getSlotStatusLabel(slot.status, locale)}</dd>
        </div>
      </dl>

      {mode === 'student' ? (
        <div className="mt-5 border-t border-slate-200 pt-4">
          {userBooking ? (
            <div className="flex flex-col gap-3">
              <span className="text-sm font-semibold text-slate-800">
                {getBookingStatusLabel(userBooking.status)}
              </span>
              {canCancelBooking ? (
                <button
                  type="button"
                  className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700 transition hover:bg-red-100 disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={isMutating}
                  onClick={() => onCancelBooking?.(userBooking)}
                >
                  {t('bookings.cancel')}
                </button>
              ) : null}
            </div>
          ) : (
            <button
              type="button"
              className="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
              disabled={!canRegister || isMutating}
              onClick={() => onRegister?.(slot)}
            >
              {t('slot.register')}
            </button>
          )}
        </div>
      ) : null}

      {mode === 'manager' ? (
        <div className="mt-5 flex flex-col gap-2 border-t border-slate-200 pt-4 sm:flex-row">
          <button
            type="button"
            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
            onClick={() => onViewDetail?.(slot)}
          >
            {t('common.viewDetails')}
          </button>
          {canCancelSlot ? (
            <button
              type="button"
              className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700 transition hover:bg-red-100"
              onClick={() => onCancelSlot?.(slot)}
            >
              {t('slot.cancel')}
            </button>
          ) : null}
        </div>
      ) : null}
    </article>
  );
}
