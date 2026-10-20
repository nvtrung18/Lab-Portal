import type { LabSlot } from '../types';
import type { BookingResponse } from '../api';
import { getBookingStatusLabel, getSlotStatusLabel, isCancellableBooking } from '../utils';
import { translate, useI18n } from '../../../shared/i18n';
import { Button } from '../../../shared/components';

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
  AVAILABLE: 'bg-emerald-50 text-emerald-700 ring-emerald-200 dark:bg-emerald-950/50 dark:text-emerald-300 dark:ring-emerald-800',
  FULL: 'bg-rose-50 text-rose-700 ring-rose-200 dark:bg-rose-950/50 dark:text-rose-300 dark:ring-rose-800',
  CLOSED: 'bg-slate-100 text-slate-700 ring-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700',
  INACTIVE: 'bg-amber-50 text-amber-700 ring-amber-200 dark:bg-amber-950/50 dark:text-amber-300 dark:ring-amber-800',
  MAINTENANCE: 'bg-orange-50 text-orange-700 ring-orange-200 dark:bg-orange-950/50 dark:text-orange-300 dark:ring-orange-800',
  IN_PROGRESS: 'bg-blue-50 text-blue-700 ring-blue-200 dark:bg-blue-950/50 dark:text-blue-300 dark:ring-blue-800',
  EXPIRED: 'bg-slate-100 text-slate-600 ring-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:ring-slate-700',
  CANCELLED: 'bg-red-50 text-red-700 ring-red-200 dark:bg-red-950/50 dark:text-red-300 dark:ring-red-800',
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
  const badgeClass = badgeStyles[slot.status] ?? 'bg-slate-100 text-slate-700 ring-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700';
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
    <article className="min-w-0 rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900 sm:p-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <p className="text-sm font-medium text-slate-500 dark:text-slate-400">{formatDate(slot.startTime, locale)}</p>
          <h3 className="mt-1 text-base font-semibold text-slate-950 dark:text-white sm:text-lg">
            {formatTime(slot.startTime, locale)} - {formatTime(slot.endTime, locale)}
          </h3>
          {showLabName && slot.labName ? (
            <p className="mt-1 break-words text-sm text-slate-600 dark:text-slate-300">{slot.labName}</p>
          ) : null}
        </div>
        <span
          className={[
            'inline-flex w-fit shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ring-1',
            badgeClass,
          ].join(' ')}
        >
          {getSlotStatusLabel(slot.status, locale)}
        </span>
      </div>

      <dl className="mt-5 grid grid-cols-2 gap-4 text-sm">
        <div>
          <dt className="text-slate-500 dark:text-slate-400">{t('slot.capacity')}</dt>
          <dd className="mt-1 font-semibold text-slate-950 dark:text-slate-100">{slot.capacity}</dd>
        </div>
        {slot.hasBookedCount ? (
          <div>
            <dt className="text-slate-500 dark:text-slate-400">{t('slot.approved')}</dt>
            <dd className="mt-1 font-semibold text-slate-950 dark:text-slate-100">{slot.approvedCount}</dd>
          </div>
        ) : null}
        {slot.hasBookedCount ? (
          <div>
            <dt className="text-slate-500 dark:text-slate-400">{t('slot.checkedIn')}</dt>
            <dd className="mt-1 font-semibold text-slate-950 dark:text-slate-100">{slot.checkedInCount}</dd>
          </div>
        ) : null}
        {slot.remainingCapacity !== null ? (
          <div>
            <dt className="text-slate-500 dark:text-slate-400">{t('slot.remaining')}</dt>
            <dd className="mt-1 font-semibold text-slate-950 dark:text-slate-100">{slot.remainingCapacity}</dd>
          </div>
        ) : null}
        <div>
          <dt className="text-slate-500 dark:text-slate-400">{t('slot.statusLabel')}</dt>
          <dd className="mt-1 font-semibold text-slate-950 dark:text-slate-100">{getSlotStatusLabel(slot.status, locale)}</dd>
        </div>
      </dl>

      {mode === 'student' ? (
        <div className="mt-5 border-t border-slate-200 pt-4 dark:border-slate-800">
          {userBooking ? (
            <div className="flex flex-col gap-3">
              <span className="text-sm font-semibold text-slate-800 dark:text-slate-200">
                {getBookingStatusLabel(userBooking.status, locale)}
              </span>
              {canCancelBooking ? (
                <Button
                  variant="danger"
                  className="w-full"
                  disabled={isMutating}
                  onClick={() => onCancelBooking?.(userBooking)}
                >
                  {t('bookings.cancel')}
                </Button>
              ) : null}
            </div>
          ) : (
            <Button
              className="w-full"
              disabled={!canRegister || isMutating}
              onClick={() => onRegister?.(slot)}
            >
              {t('slot.register')}
            </Button>
          )}
        </div>
      ) : null}

      {mode === 'manager' ? (
        <div className="mt-5 flex flex-col gap-2 border-t border-slate-200 pt-4 dark:border-slate-800 sm:flex-row">
          <Button
            variant="outline"
            size="sm"
            onClick={() => onViewDetail?.(slot)}
          >
            {t('common.viewDetails')}
          </Button>
          {canCancelSlot ? (
            <Button
              variant="danger"
              size="sm"
              onClick={() => onCancelSlot?.(slot)}
            >
              {t('slot.cancel')}
            </Button>
          ) : null}
        </div>
      ) : null}
    </article>
  );
}
