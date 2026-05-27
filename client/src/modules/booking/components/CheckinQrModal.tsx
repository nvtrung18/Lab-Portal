import { useEffect, useState } from 'react';
import QRCode from 'qrcode';

import { Button, Modal } from '../../../shared/components';
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
    <Modal
      footer={(
        <>
          <Button disabled={isCreating} onClick={onClose} variant="outline">
            Đóng
          </Button>
          <Button disabled={!expired} loading={isCreating} loadingText="Đang tạo..." onClick={onRegenerate}>
            Tạo lại mã QR
          </Button>
        </>
      )}
      onClose={onClose}
      size="sm"
      title="Mã QR check-in"
    >
      <section className="text-center">
        <p className="mt-2 text-sm text-slate-600">
          Vui lòng đưa mã này cho quản lý PTN quét để xác nhận có mặt.
        </p>

        <div className="mt-5 rounded-lg border border-slate-200 bg-slate-50 p-4">
          {qrImageUrl ? (
            <img className="mx-auto h-auto w-full max-w-64 rounded bg-white p-2" src={qrImageUrl} alt="Mã QR check-in" />
          ) : (
            <div className="flex aspect-square items-center justify-center text-sm text-slate-500">Chưa có mã QR</div>
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

      </section>
    </Modal>
  );
}
