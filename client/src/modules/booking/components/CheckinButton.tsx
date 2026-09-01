import { useState } from 'react';

import type { BookingResponse, FaceFallbackReason } from '../api';
import { useCreateCheckinQr } from '../hooks';
import { CheckinQrModal } from './CheckinQrModal';

interface CheckinButtonProps {
  booking: BookingResponse;
}

const CHECKIN_AFTER_START_MINUTES = 10;

function getWindowState(startTime: string) {
  const start = new Date(startTime).getTime();
  if (Number.isNaN(start)) {
    return 'invalid';
  }
  const now = Date.now();
  const closesAt = start + CHECKIN_AFTER_START_MINUTES * 60 * 1000;
  if (now < start) {
    return 'early';
  }
  if (now > closesAt) {
    return 'late';
  }
  return 'open';
}

export function CheckinButton({ booking }: CheckinButtonProps) {
  const [isQrOpen, setIsQrOpen] = useState(false);
  const [fallbackReason, setFallbackReason] = useState<FaceFallbackReason | ''>('');
  const createQr = useCreateCheckinQr();

  if (booking.status === 'CHECKED_IN') {
    return (
      <button
        className="w-full rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-semibold text-emerald-700"
        disabled
        type="button"
      >
        Đã xác nhận có mặt
      </button>
    );
  }

  if (booking.status !== 'APPROVED') {
    return null;
  }

  const state = getWindowState(booking.startTime);
  const disabled = state !== 'open' || createQr.isPending || !fallbackReason;
  const text =
    state === 'early'
      ? 'Chưa đến giờ check-in'
      : state === 'late'
        ? 'Đã quá thời gian check-in'
        : 'Tạo mã QR check-in';

  const handleCreateQr = async () => {
    if (!fallbackReason) return;
    await createQr.mutateAsync({ bookingId: booking.id, fallbackReason });
    setIsQrOpen(true);
  };

  return (
    <>
      <label className="mb-2 block text-xs font-semibold text-slate-600" htmlFor={`fallback-reason-${booking.id}`}>
        Lý do dùng QR fallback
        <select
          id={`fallback-reason-${booking.id}`}
          className="mt-1 min-h-11 w-full rounded-md border border-slate-300 bg-white px-2 text-sm text-slate-900"
          value={fallbackReason}
          onChange={(event) => setFallbackReason(event.target.value as FaceFallbackReason | '')}
        >
          <option value="">Chọn lý do</option>
          <option value="FACE_DISABLED">Face Check-in đang tắt</option>
          <option value="FACE_SERVICE_UNAVAILABLE">Dịch vụ Face không khả dụng</option>
          <option value="FACE_PROFILE_UNAVAILABLE">Chưa có Face Profile</option>
        </select>
      </label>
      <button
        className="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:bg-slate-400"
        disabled={disabled}
        type="button"
        onClick={handleCreateQr}
      >
        {createQr.isPending ? 'Đang tạo mã QR...' : text}
      </button>
      <CheckinQrModal
        booking={booking}
        qr={createQr.data}
        isOpen={isQrOpen}
        isCreating={createQr.isPending}
        onRegenerate={handleCreateQr}
        onClose={() => setIsQrOpen(false)}
      />
    </>
  );
}
