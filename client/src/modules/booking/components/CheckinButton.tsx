import { useState } from 'react';

import type { BookingResponse, FaceFallbackReason } from '../api';
import { useCreateCheckinQr, useMyCheckinQrRequest } from '../hooks';
import { CheckinQrModal } from './CheckinQrModal';

interface CheckinButtonProps {
  booking: BookingResponse;
}

const CHECKIN_BEFORE_START_MINUTES = 5;
const CHECKIN_AFTER_START_MINUTES = 10;

function getWindowState(startTime: string) {
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

export function CheckinButton({ booking }: CheckinButtonProps) {
  const [isQrOpen, setIsQrOpen] = useState(false);
  const [fallbackReason, setFallbackReason] = useState<FaceFallbackReason | ''>('');
  const [customReason, setCustomReason] = useState('');
  const createQr = useCreateCheckinQr();
  const qrRequest = useMyCheckinQrRequest(booking.id, Boolean(createQr.data?.requestId));
  const qr = qrRequest.data ?? createQr.data;

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
  const waiting = qr?.status === 'PENDING';
  const disabled = state !== 'open' || createQr.isPending || waiting || !fallbackReason || (fallbackReason === 'OTHER' && !customReason.trim());
  const text =
    state === 'early'
      ? 'Mở trước giờ bắt đầu 5 phút'
      : state === 'late'
        ? 'Đã quá thời gian check-in'
        : waiting ? 'Đang chờ quản lý duyệt QR' : 'Gửi yêu cầu tạo mã QR';

  const handleCreateQr = async () => {
    if (!fallbackReason) return;
    await createQr.mutateAsync({ bookingId: booking.id, fallbackReason, customReason: fallbackReason === 'OTHER' ? customReason.trim() : undefined });
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
          <option value="OTHER">Lý do khác</option>
        </select>
      </label>
      {fallbackReason === 'OTHER' ? (
        <textarea
          className="mb-2 min-h-20 w-full rounded-md border border-slate-300 px-2 py-2 text-sm"
          maxLength={1000}
          placeholder="Nhập lý do xin tạo QR"
          value={customReason}
          onChange={(event) => setCustomReason(event.target.value)}
        />
      ) : null}
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
        qr={qr}
        isOpen={isQrOpen}
        isCreating={createQr.isPending}
        onRegenerate={handleCreateQr}
        onClose={() => setIsQrOpen(false)}
      />
    </>
  );
}
