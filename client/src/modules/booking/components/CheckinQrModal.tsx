import { useEffect, useState } from 'react';
import QRCode from 'qrcode';

import { Button, Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import type { BookingResponse, CheckinQrResponse } from '../api';

interface CheckinQrModalProps {
  booking: BookingResponse;
  qr?: Pick<CheckinQrResponse, 'requestId' | 'status' | 'token' | 'expiresAt'>;
  isOpen: boolean;
  isCreating: boolean;
  onRegenerate?: () => void;
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
  const { t } = useI18n();
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [qrImageUrl, setQrImageUrl] = useState('');

  useEffect(() => {
    if (!isOpen) {
      return undefined;
    }
    const update = () => setRemainingSeconds(secondsUntil(qr?.expiresAt));
    update();
    const timer = window.setInterval(update, 1000);
    return () => {
      window.clearInterval(timer);
    };
  }, [isOpen, qr?.expiresAt]);

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

  const expired = Boolean(qr?.token) && remainingSeconds <= 0;
  const pending = qr?.status === 'PENDING';
  const rejected = qr?.status === 'REJECTED';

  return (
    <Modal
      footer={(
        <>
          <Button disabled={isCreating} onClick={onClose} variant="outline">
            {t('common.close')}
          </Button>
          {onRegenerate ? (
            <Button disabled={!expired && !rejected} loading={isCreating} loadingText={t('checkin.sendingQrRequest')} onClick={onRegenerate}>
              {t('checkin.resendQrRequest')}
            </Button>
          ) : null}
        </>
      )}
      onClose={onClose}
      size="sm"
      title={t('checkin.qrTitle')}
    >
      <section className="text-center">
        <p className="mt-2 text-sm text-slate-600">
          {pending ? t('checkin.qrPendingDescription') : rejected ? t('checkin.qrRejectedDescription') : t('checkin.qrApprovedDescription')}
        </p>

        <div className="mt-5 rounded-lg border border-slate-200 bg-slate-50 p-4">
          {qrImageUrl ? (
            <img className="mx-auto h-auto w-full max-w-64 rounded bg-white p-2" src={qrImageUrl} alt={t('checkin.qrAlt')} />
          ) : (
            <div className="flex aspect-square items-center justify-center text-sm text-slate-500">{t('checkin.qrNotAvailable')}</div>
          )}
        </div>

        {qr?.token ? (
          <div className="mt-4 rounded-md bg-slate-100 px-3 py-2 text-xs font-semibold text-slate-700">
            {t('checkin.fallbackCode')}: <span className="break-all">{qr.token}</span>
          </div>
        ) : null}

        <p className="mt-4 text-sm font-medium text-slate-700">
          {pending
            ? t('checkin.qrPendingStatus')
            : rejected
              ? t('checkin.qrRejectedStatus')
              : expired
                ? t('checkin.qrExpired')
                : t('checkin.qrExpiresIn', undefined, { seconds: remainingSeconds })}
        </p>
        <p className="mt-1 text-xs text-slate-500">
          {t('checkin.bookingLabel')} #{booking.id} - {booking.labName ?? t('lab.shortName')}
        </p>

      </section>
    </Modal>
  );
}
