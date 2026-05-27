import { useState } from 'react';

import type { BookingResponse } from '../api';
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
  const disabled = state !== 'open' || createQr.isPending;
  const text =
    state === 'early'
      ? 'Chưa đến giờ check-in'
      : state === 'late'
        ? 'Đã quá thời gian check-in'
        : 'Tạo mã QR check-in';

  const handleCreateQr = async () => {
    await createQr.mutateAsync(booking.id);
    setIsQrOpen(true);
  };

  return (
    <>
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
