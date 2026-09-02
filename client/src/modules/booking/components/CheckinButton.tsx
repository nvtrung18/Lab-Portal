import { useState } from 'react';

import type { BookingResponse, CheckinQrHistoryResponse, FaceFallbackReason } from '../api';
import { useCreateCheckinQr } from '../hooks';
import { CheckinQrModal } from './CheckinQrModal';

interface CheckinButtonProps {
  booking: BookingResponse;
  qrHistory?: CheckinQrHistoryResponse;
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

export function CheckinButton({ booking, qrHistory }: CheckinButtonProps) {
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
  };

  if (qr?.status === 'APPROVED') {
    return (
      <>
        <button
          className="w-full rounded-md bg-blue-700 px-3 py-2 text-sm font-semibold text-white hover:bg-blue-800"
          type="button"
          onClick={() => setIsQrOpen(true)}
        >
          Xem mã QR đã được cấp
        </button>
        <CheckinQrModal
          booking={booking}
          qr={qr}
          isOpen={isQrOpen}
          isCreating={false}
          onClose={() => setIsQrOpen(false)}
        />
      </>
    );
  }

  if (qr?.status === 'PENDING') {
    return (
      <button
        className="w-full rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-700"
        disabled
        type="button"
      >
        Đã gửi yêu cầu – chờ thông báo
      </button>
    );
  }

  return (
    <>
      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_minmax(14rem,auto)] md:items-end">
        <div>
          <label className="block text-xs font-semibold text-slate-600" htmlFor={`fallback-reason-${booking.id}`}>
            Lý do cần dùng mã QR
            <select
              id={`fallback-reason-${booking.id}`}
              className="mt-1.5 min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-sm text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
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
              aria-label="Lý do khác"
              className="mt-2 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              maxLength={1000}
              placeholder="Mô tả ngắn lý do cần dùng mã QR"
              value={customReason}
              onChange={(event) => setCustomReason(event.target.value)}
            />
          ) : null}
        </div>
        <button
          className="min-h-11 w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-600"
          disabled={disabled}
          type="button"
          onClick={handleCreateQr}
        >
          {createQr.isPending ? 'Đang gửi yêu cầu...' : text}
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
    </>
  );
}
