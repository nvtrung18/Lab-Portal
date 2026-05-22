import { useEffect, useState } from 'react';
import QRCode from 'qrcode';

import type { BookingResponse, CheckinQrResponse } from '../api';
import { useMyBookings } from '../hooks';

interface CheckinQrModalProps {
  booking: BookingResponse;
  qr?: CheckinQrResponse;
  isOpen: boolean;
  isCreating: boolean;
  onRegenerate: () => void;
  onClose: () => void;
}

function secondsUntil(value?: string) {
  if (!value) {
    return 0;
  }
  return Math.max(0, Math.floor((new Date(value).getTime() - Date.now()) / 1000));
}

export function CheckinQrModal({
  booking,
  qr,
  isOpen,
  isCreating,
  onRegenerate,
  onClose,
}: CheckinQrModalProps) {
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [qrImageUrl, setQrImageUrl] = useState('');
  const { refetch } = useMyBookings(isOpen);

  useEffect(() => {
    if (!isOpen) {
      return undefined;
    }
    const update = () => setRemainingSeconds(secondsUntil(qr?.expiresAt));
    update();
    const timer = window.setInterval(update, 1000);
    const poller = window.setInterval(() => void refetch(), 7000);
    return () => {
      window.clearInterval(timer);
      window.clearInterval(poller);
    };
  }, [isOpen, qr?.expiresAt, refetch]);

  useEffect(() => {
    let cancelled = false;
    if (!qr?.token) {
      setQrImageUrl('');
      return undefined;
    }

    QRCode.toDataURL(qr.token, {
      errorCorrectionLevel: 'M',
      margin: 2,
      width: 260,
    })
      .then((url) => {
        if (!cancelled) {
          setQrImageUrl(url);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setQrImageUrl('');
        }
      });

    return () => {
      cancelled = true;
    };
  }, [qr?.token]);

  if (!isOpen) {
    return null;
  }

  const expired = Boolean(qr) && remainingSeconds <= 0;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 px-4 py-6">
      <section className="w-full max-w-md rounded-lg bg-white p-5 text-center shadow-xl">
        <h2 className="text-xl font-semibold text-slate-950">Mã QR check-in</h2>
        <p className="mt-2 text-sm text-slate-600">
          Vui lòng đưa mã này cho quản lý PTN quét để xác nhận có mặt.
        </p>

        <div className="mt-5 rounded-lg border border-slate-200 bg-slate-50 p-4">
          {qrImageUrl ? (
            <img className="mx-auto h-64 w-64 rounded bg-white p-2" src={qrImageUrl} alt="Mã QR check-in" />
          ) : (
            <div className="flex h-64 items-center justify-center text-sm text-slate-500">Chưa có mã QR</div>
          )}
        </div>

        {qr?.token ? (
          <div className="mt-4 rounded-md bg-slate-100 px-3 py-2 text-xs font-semibold text-slate-700">
            Mã dự phòng: <span className="break-all">{qr.token}</span>
          </div>
        ) : null}

        <p className="mt-4 text-sm font-medium text-slate-700">
          {expired ? 'Mã QR đã hết hạn.' : `Mã QR hết hạn sau ${remainingSeconds} giây.`}
        </p>
        <p className="mt-1 text-xs text-slate-500">
          Đăng ký #{booking.id} - {booking.labName ?? 'PTN'}
        </p>

        <div className="mt-5 grid gap-2 sm:grid-cols-2">
          <button
            className="rounded-md border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 disabled:opacity-60"
            disabled={isCreating}
            type="button"
            onClick={onClose}
          >
            Đóng
          </button>
          <button
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:bg-slate-400"
            disabled={isCreating || !expired}
            type="button"
            onClick={onRegenerate}
          >
            {isCreating ? 'Đang tạo...' : 'Tạo lại mã QR'}
          </button>
        </div>
      </section>
    </div>
  );
}
