import { useEffect, useMemo, useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import type { BookingResponse } from '../api';
import type { LabSlot } from '../types';
import { formatDateTime, formatPenaltyType } from '../../penalty/utils';

const PENALTY_TYPES = ['NO_SHOW', 'LATE_CHECKIN', 'CLEANING_FAILED', 'RULE_VIOLATION', 'OTHER'] as const;

interface PenaltyCreateModalProps {
  booking: BookingResponse | null;
  slot?: LabSlot | null;
  isOpen: boolean;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (payload: {
    userId: number;
    slotId: number;
    bookingId: number;
    type: string;
    point: number;
    reason: string;
  }) => void;
}

function inferPenaltyType(booking: BookingResponse | null, slot?: LabSlot | null) {
  if (!booking || !slot) {
    return 'OTHER';
  }

  const now = Date.now();
  const startTime = new Date(slot.startTime).getTime();
  if (booking.status === 'APPROVED' && Number.isFinite(startTime) && now > startTime + 10 * 60 * 1000) {
    return 'NO_SHOW';
  }

  if (booking.status === 'CHECKED_IN') {
    return 'RULE_VIOLATION';
  }

  if (booking.status === 'CANCELLED_BY_STUDENT') {
    return 'OTHER';
  }

  return 'OTHER';
}

export function PenaltyCreateModal({
  booking,
  slot,
  isOpen,
  isSubmitting,
  onClose,
  onSubmit,
}: PenaltyCreateModalProps) {
  const { locale, t } = useI18n();
  const inferredType = useMemo(() => inferPenaltyType(booking, slot), [booking, slot]);
  const [type, setType] = useState(inferredType);
  const [point, setPoint] = useState(1);
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setType(inferredType);
      setPoint(1);
      setReason('');
      setTouched(false);
    }
  }, [inferredType, isOpen]);

  if (!isOpen || !booking || !slot) {
    return null;
  }

  const trimmedReason = reason.trim();
  const reasonError =
    touched && trimmedReason.length < 10 ? t('penalty.reasonMinLength') : null;
  const canSubmit = Boolean(type) && trimmedReason.length >= 10 && point >= 0 && !isSubmitting;

  const handleSubmit = () => {
    setTouched(true);
    if (!canSubmit) {
      return;
    }

    onSubmit({
      userId: booking.userId,
      slotId: slot.id,
      bookingId: booking.id,
      type,
      point,
      reason: trimmedReason,
    });
  };

  return (
    <Modal
      footer={(
        <>
          <Button disabled={isSubmitting} onClick={onClose} variant="outline">
            {t('common.cancel')}
          </Button>
          <Button disabled={!canSubmit} loading={isSubmitting} loadingText={t('penalty.creating')} onClick={handleSubmit}>
            {t('penalty.createConfirm')}
          </Button>
        </>
      )}
      onClose={onClose}
      size="lg"
      title={t('penalty.createTitle')}
    >
        <div className="space-y-5">
          <dl className="grid gap-4 rounded-md bg-slate-50 p-4 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-slate-500">{t('penalty.student')}</dt>
              <dd className="mt-1 font-semibold text-slate-950">{booking.studentName ?? t('common.notUpdated')}</dd>
              <dd className="break-all text-slate-600">{booking.studentEmail ?? t('common.notUpdated')}</dd>
            </div>
            <div>
              <dt className="text-slate-500">{t('penalty.lab')}</dt>
              <dd className="mt-1 font-semibold text-slate-950">{slot.labName ?? booking.labName ?? t('common.notUpdated')}</dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-slate-500">{t('penalty.slot')}</dt>
              <dd className="mt-1 font-semibold text-slate-950">
                {formatDateTime(slot.startTime, locale)} - {formatDateTime(slot.endTime, locale)}
              </dd>
            </div>
          </dl>

          <label className="block text-sm font-medium text-slate-700">
            {t('penalty.typeLabel')}
            <select
              className="mt-2 min-h-11 w-full rounded-md border border-slate-300 px-3 py-2 text-base text-slate-900 shadow-sm focus:border-slate-500 focus:outline-none"
              value={type}
              onChange={(event) => setType(event.target.value)}
              disabled={isSubmitting}
            >
              {PENALTY_TYPES.map((penaltyType) => (
                <option key={penaltyType} value={penaltyType}>
                  {formatPenaltyType(penaltyType, locale)}
                </option>
              ))}
            </select>
          </label>

          <label className="block text-sm font-medium text-slate-700">
            {t('penalty.points')}
            <input
              className="mt-2 min-h-11 w-full rounded-md border border-slate-300 px-3 py-2 text-base text-slate-900 shadow-sm focus:border-slate-500 focus:outline-none"
              min={0}
              type="number"
              value={point}
              onChange={(event) => setPoint(Math.max(0, Number(event.target.value)))}
              disabled={isSubmitting}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            {t('penalty.reasonLabel')}
            <textarea
              className="mt-2 min-h-32 w-full rounded-md border border-slate-300 px-3 py-2 text-base text-slate-900 shadow-sm focus:border-slate-500 focus:outline-none"
              maxLength={1000}
              placeholder={t('penalty.reasonPlaceholder')}
              value={reason}
              onBlur={() => setTouched(true)}
              onChange={(event) => setReason(event.target.value)}
              disabled={isSubmitting}
            />
          </label>
          {reasonError ? <p className="text-sm text-red-600">{reasonError}</p> : null}
        </div>

    </Modal>
  );
}
