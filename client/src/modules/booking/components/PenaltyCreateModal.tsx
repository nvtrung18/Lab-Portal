import { useEffect, useMemo, useState } from 'react';

import type { BookingResponse } from '../api';
import type { LabSlot } from '../types';
import { formatPenaltyType } from '../../penalty/utils';

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

function formatDateTime(value?: string | null) {
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
    touched && trimmedReason.length < 10 ? 'Lý do vi phạm cần tối thiểu 10 ký tự.' : null;
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 px-4">
      <div className="w-full max-w-2xl rounded-lg bg-white shadow-xl">
        <div className="border-b border-slate-200 px-6 py-4">
          <h3 className="text-lg font-semibold text-slate-950">Ghi nhận vi phạm</h3>
          <p className="mt-1 text-sm text-slate-600">
            Ghi nhận vi phạm cho sinh viên trong ca sử dụng PTN.
          </p>
        </div>

        <div className="space-y-5 px-6 py-5">
          <dl className="grid gap-4 rounded-md bg-slate-50 p-4 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-slate-500">Sinh viên</dt>
              <dd className="mt-1 font-semibold text-slate-950">{booking.studentName ?? 'Chưa cập nhật'}</dd>
              <dd className="text-slate-600">{booking.studentEmail ?? 'Chưa cập nhật'}</dd>
            </div>
            <div>
              <dt className="text-slate-500">PTN</dt>
              <dd className="mt-1 font-semibold text-slate-950">{slot.labName ?? booking.labName ?? 'Chưa cập nhật'}</dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-slate-500">Khung giờ</dt>
              <dd className="mt-1 font-semibold text-slate-950">
                {formatDateTime(slot.startTime)} - {formatDateTime(slot.endTime)}
              </dd>
            </div>
          </dl>

          <label className="block text-sm font-medium text-slate-700">
            Loại vi phạm
            <select
              className="mt-2 w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-slate-500 focus:outline-none"
              value={type}
              onChange={(event) => setType(event.target.value)}
              disabled={isSubmitting}
            >
              {PENALTY_TYPES.map((penaltyType) => (
                <option key={penaltyType} value={penaltyType}>
                  {formatPenaltyType(penaltyType)}
                </option>
              ))}
            </select>
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Điểm vi phạm
            <input
              className="mt-2 w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-slate-500 focus:outline-none"
              min={0}
              type="number"
              value={point}
              onChange={(event) => setPoint(Math.max(0, Number(event.target.value)))}
              disabled={isSubmitting}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Lý do vi phạm
            <textarea
              className="mt-2 min-h-32 w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-slate-500 focus:outline-none"
              maxLength={1000}
              placeholder="Nhập lý do hoặc mô tả chi tiết vi phạm..."
              value={reason}
              onBlur={() => setTouched(true)}
              onChange={(event) => setReason(event.target.value)}
              disabled={isSubmitting}
            />
          </label>
          {reasonError ? <p className="text-sm text-red-600">{reasonError}</p> : null}
        </div>

        <div className="flex flex-col-reverse gap-2 border-t border-slate-200 px-6 py-4 sm:flex-row sm:justify-end">
          <button
            type="button"
            className="rounded-md border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700"
            onClick={onClose}
            disabled={isSubmitting}
          >
            Hủy
          </button>
          <button
            type="button"
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:bg-slate-400"
            onClick={handleSubmit}
            disabled={!canSubmit}
          >
            {isSubmitting ? 'Đang ghi nhận...' : 'Xác nhận ghi nhận'}
          </button>
        </div>
      </div>
    </div>
  );
}
