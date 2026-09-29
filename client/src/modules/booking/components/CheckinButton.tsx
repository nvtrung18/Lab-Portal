import { useState } from 'react';

import { useI18n } from '../../../shared/i18n';
import type { BookingResponse, CheckinQrHistoryResponse, FaceFallbackReason } from '../api';
import { useCreateCheckinQr } from '../hooks';
import { StudentFaceCheckinPanel } from '../../face/components/StudentFaceCheckinPanel';
import { CheckinQrModal } from './CheckinQrModal';

interface CheckinButtonProps {
  booking: BookingResponse;
  qrHistory?: CheckinQrHistoryResponse;
}

const CHECKIN_BEFORE_START_MINUTES = 5;
const CHECKIN_AFTER_START_MINUTES = 10;

type WindowState = 'invalid' | 'early' | 'late' | 'open';

function getWindowState(startTime: string): WindowState {
  const start = new Date(startTime).getTime();
  if (Number.isNaN(start)) {
    return 'invalid';
  }
  const now = Date.now();
  const closesAt = start + CHECKIN_AFTER_START_MINUTES * 60 * 1000;
  if (now < start - CHECKIN_BEFORE_START_MINUTES * 60 * 1000) {
    return 'early';
  }
  if (now > closesAt) {
    return 'late';
  }
  return 'open';
}

export function CheckinButton({ booking, qrHistory }: CheckinButtonProps) {
  const { t } = useI18n();
  const [isQrOpen, setIsQrOpen] = useState(false);
  const [fallbackReason, setFallbackReason] = useState<FaceFallbackReason | ''>('');
  const [customReason, setCustomReason] = useState('');
  const createQr = useCreateCheckinQr();
  const qr = qrHistory ?? createQr.data;

  if (booking.status === 'CHECKED_IN') {
    return (
      <button
        className="w-full rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-semibold text-emerald-700"
        disabled
        type="button"
      >
        {t('booking.status.checkedIn')}
      </button>
    );
  }

  if (booking.status !== 'APPROVED') {
    return null;
  }

  const state = getWindowState(booking.startTime);
  const waiting = qr?.status === 'PENDING';
  const qrDisabled = state !== 'open' || createQr.isPending || waiting || !fallbackReason
    || (fallbackReason === 'OTHER' && !customReason.trim());
  const qrText =
    state === 'early'
      ? t('checkin.qrEarly')
      : state === 'late'
        ? t('checkin.qrLate')
        : waiting ? t('checkin.qrWaiting') : t('checkin.qrRequest');

  const handleCreateQr = async () => {
    if (!fallbackReason) return;
    await createQr.mutateAsync({
      bookingId: booking.id,
      fallbackReason,
      customReason: fallbackReason === 'OTHER' ? customReason.trim() : undefined,
    });
  };

  const studentFaceCheckinPanel = (
    <section
      className="rounded-lg border border-blue-200 bg-blue-50/70 p-3 dark:border-blue-900 dark:bg-blue-950/30"
      aria-label={t('checkin.selfLabel')}
    >
      <p className="text-sm font-semibold text-blue-950 dark:text-blue-100">{t('checkin.selfTitle')}</p>
      <StudentFaceCheckinPanel booking={booking} disabled={state !== 'open'} />
    </section>
  );

  if (qr?.status === 'APPROVED') {
    return (
      <div className="space-y-3">
        {studentFaceCheckinPanel}
        <button
          className="min-h-11 w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500"
          type="button"
          onClick={() => setIsQrOpen(true)}
        >
          {t('checkin.viewIssuedQr')}
        </button>
        <CheckinQrModal
          booking={booking}
          qr={qr}
          isOpen={isQrOpen}
          isCreating={false}
          onClose={() => setIsQrOpen(false)}
        />
      </div>
    );
  }

  if (qr?.status === 'PENDING') {
    return (
      <div className="space-y-3">
        {studentFaceCheckinPanel}
        <button
          className="w-full rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-700"
          disabled
          type="button"
        >
          {t('checkin.qrSubmitted')}
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {studentFaceCheckinPanel}
      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_minmax(14rem,auto)] md:items-end">
        <div>
          <label className="block text-xs font-semibold text-slate-600 dark:text-slate-300" htmlFor={`fallback-reason-${booking.id}`}>
            {t('checkin.qrReason')}
            <select
              id={`fallback-reason-${booking.id}`}
              className="mt-1.5 min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-base text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:border-slate-700 dark:bg-slate-950 dark:text-white"
              value={fallbackReason}
              onChange={(event) => setFallbackReason(event.target.value as FaceFallbackReason | '')}
            >
              <option value="">{t('checkin.chooseReason')}</option>
              <option value="FACE_DISABLED">{t('checkin.reasonFaceDisabled')}</option>
              <option value="FACE_SERVICE_UNAVAILABLE">{t('checkin.reasonFaceUnavailable')}</option>
              <option value="FACE_PROFILE_UNAVAILABLE">{t('checkin.reasonProfileUnavailable')}</option>
              <option value="OTHER">{t('checkin.reasonOther')}</option>
            </select>
          </label>
          {fallbackReason === 'OTHER' ? (
            <textarea
              aria-label={t('checkin.reasonOther')}
              className="mt-2 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-base focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:border-slate-700 dark:bg-slate-950 dark:text-white"
              maxLength={1000}
              placeholder={t('checkin.reasonPlaceholder')}
              value={customReason}
              onChange={(event) => setCustomReason(event.target.value)}
            />
          ) : null}
        </div>
        <button
          className="min-h-11 w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-600 dark:bg-slate-700 dark:hover:bg-slate-600"
          disabled={qrDisabled}
          type="button"
          onClick={handleCreateQr}
        >
          {createQr.isPending ? t('checkin.sendingQrRequest') : qrText}
        </button>
      </div>
      <CheckinQrModal
        booking={booking}
        qr={qr}
        isOpen={isQrOpen}
        isCreating={createQr.isPending}
        onRegenerate={handleCreateQr}
        onClose={() => setIsQrOpen(false)}
      />
    </div>
  );
}
