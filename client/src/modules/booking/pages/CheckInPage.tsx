import axios from 'axios';
import { FormEvent, useEffect, useRef, useState } from 'react';

import type { Response } from '../../../shared/types';
import { Button } from '../../../shared/components';
import type { BookingResponse } from '../api';
import { useConfirmCheckIn, useManualCheckIn } from '../hooks';

interface BarcodeResult {
  rawValue: string;
}

interface BarcodeDetectorInstance {
  detect(source: HTMLVideoElement): Promise<BarcodeResult[]>;
}

type BarcodeDetectorConstructor = new (options: { formats: string[] }) => BarcodeDetectorInstance;

function getErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const response = error.response?.data as Partial<Response<unknown>> | undefined;
    return response?.message ?? response?.errors?.[0] ?? 'Không thể xác nhận có mặt. Vui lòng thử lại sau.';
  }
  return 'Không thể xác nhận có mặt. Vui lòng thử lại sau.';
}

export function CheckInPage() {
  const [token, setToken] = useState('');
  const [error, setError] = useState('');
  const [cameraError, setCameraError] = useState('');
  const [isCameraActive, setIsCameraActive] = useState(false);
  const [confirmedBooking, setConfirmedBooking] = useState<BookingResponse | null>(null);
  const confirmCheckIn = useConfirmCheckIn();
  const manualCheckIn = useManualCheckIn();
  const [manualBookingId, setManualBookingId] = useState('');
  const [manualReason, setManualReason] = useState('');
  const [manualError, setManualError] = useState('');
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const frameRef = useRef<number | null>(null);
  const handledTokenRef = useRef(false);

  const stopCamera = () => {
    if (frameRef.current !== null) {
      window.cancelAnimationFrame(frameRef.current);
      frameRef.current = null;
    }
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    setIsCameraActive(false);
  };

  useEffect(() => stopCamera, []);

  const confirmToken = (value: string) => {
    const normalizedToken = value.trim();
    if (!normalizedToken) {
      setError('Vui lòng nhập mã QR hoặc token check-in.');
      return;
    }

    setError('');
    setConfirmedBooking(null);
    confirmCheckIn.mutate(normalizedToken, {
      onSuccess: (result) => {
        setConfirmedBooking(result.booking);
        setToken('');
        handledTokenRef.current = false;
      },
      onError: (submitError) => {
        setError(getErrorMessage(submitError));
        handledTokenRef.current = false;
      },
    });
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    confirmToken(token);
  };

  const startCamera = async () => {
    setCameraError('');
    setError('');
    setConfirmedBooking(null);
    handledTokenRef.current = false;

    const BarcodeDetector = (window as unknown as { BarcodeDetector?: BarcodeDetectorConstructor }).BarcodeDetector;
    if (!BarcodeDetector) {
      setCameraError('Trình duyệt chưa hỗ trợ quét QR bằng camera. Vui lòng nhập token thủ công.');
      return;
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' } },
        audio: false,
      });
      streamRef.current = stream;
      setIsCameraActive(true);

      const video = videoRef.current;
      if (!video) {
        stopCamera();
        return;
      }

      video.srcObject = stream;
      await video.play();

      const detector = new BarcodeDetector({ formats: ['qr_code'] });
      const scan = async () => {
        if (!videoRef.current || handledTokenRef.current) {
          return;
        }
        try {
          const results = await detector.detect(videoRef.current);
          const scannedToken = results[0]?.rawValue?.trim();
          if (scannedToken) {
            handledTokenRef.current = true;
            setToken(scannedToken);
            stopCamera();
            confirmToken(scannedToken);
            return;
          }
        } catch {
          setCameraError('Không thể đọc mã QR. Vui lòng thử lại hoặc nhập token thủ công.');
        }
        frameRef.current = window.requestAnimationFrame(scan);
      };
      frameRef.current = window.requestAnimationFrame(scan);
    } catch {
      setCameraError('Không thể mở camera. Vui lòng kiểm tra quyền truy cập camera hoặc nhập token thủ công.');
      stopCamera();
    }
  };

  const handleManualCheckin = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (Number(manualBookingId) <= 0 || !manualReason.trim()) {
      setManualError('Vui lòng nhập booking ID và lý do xác nhận thủ công.');
      return;
    }
    setManualError('');
    manualCheckIn.mutate(
      { bookingId: Number(manualBookingId), reason: manualReason.trim() },
      { onSuccess: () => { setManualBookingId(''); setManualReason(''); } },
    );
  };

  return (
    <div className="mx-auto max-w-3xl">
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div>
          <p className="text-sm font-semibold uppercase tracking-wide text-slate-500">Quản lý PTN</p>
          <h1 className="mt-1 text-2xl font-semibold text-slate-950">Xác nhận có mặt</h1>
          <p className="mt-2 text-sm text-slate-600">
            Quét mã QR của sinh viên để xác nhận đã đến PTN. Token chỉ hợp lệ trong thời gian check-in và dùng một lần.
          </p>
        </div>

        <div className="mt-6 rounded-lg border border-slate-200 bg-slate-50 p-4">
          <div className="aspect-video overflow-hidden rounded-md bg-slate-900">
            <video
              ref={videoRef}
              className="h-full w-full object-cover"
              muted
              playsInline
            />
          </div>
          <div className="mt-4 flex flex-col gap-2 sm:flex-row">
            <button
              className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:bg-slate-400"
              disabled={confirmCheckIn.isPending || isCameraActive}
              type="button"
              onClick={startCamera}
            >
              {isCameraActive ? 'Đang quét QR...' : 'Bật camera quét QR'}
            </button>
            <button
              className="rounded-md border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 disabled:opacity-60"
              disabled={!isCameraActive}
              type="button"
              onClick={stopCamera}
            >
              Tắt camera
            </button>
          </div>
          {cameraError ? <p className="mt-3 text-sm font-medium text-amber-700">{cameraError}</p> : null}
        </div>

        <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
          <label className="block text-sm font-semibold text-slate-700" htmlFor="checkin-token">
            Token check-in dự phòng
          </label>
          <textarea
            id="checkin-token"
            className="min-h-24 w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-200"
            placeholder="Dán token từ mã QR của sinh viên nếu camera không quét được"
            value={token}
            onChange={(event) => setToken(event.target.value)}
          />

          {error ? (
            <div className="rounded-md border border-red-200 bg-red-50 px-3 py-3 text-sm font-semibold text-red-700">
              {error}
            </div>
          ) : null}

          {confirmedBooking ? (
            <div className="rounded-md border border-emerald-200 bg-emerald-50 px-3 py-3 text-sm text-emerald-800">
              <p className="font-semibold">Xác nhận có mặt thành công.</p>
              <p className="mt-1">
                Sinh viên: {confirmedBooking.studentName ?? confirmedBooking.studentEmail ?? `#${confirmedBooking.userId}`}
              </p>
              <p>PTN: {confirmedBooking.labName ?? `#${confirmedBooking.labId}`}</p>
              <p>Trạng thái: Đã xác nhận có mặt</p>
            </div>
          ) : null}

          <button
            className="w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400 sm:w-auto"
            disabled={confirmCheckIn.isPending}
            type="submit"
          >
            {confirmCheckIn.isPending ? 'Đang xác nhận...' : 'Xác nhận'}
          </button>
        </form>
      </section>

      <section className="mt-5 rounded-lg border border-amber-200 bg-amber-50 p-6 shadow-sm">
        <p className="text-sm font-semibold uppercase tracking-wide text-amber-700">Fallback có kiểm soát</p>
        <h2 className="mt-1 text-xl font-semibold text-slate-950">Xác nhận thủ công</h2>
        <p className="mt-2 text-sm leading-6 text-slate-600">Chỉ sử dụng khi Face Check-in và QR không khả dụng. Lý do sẽ được lưu trong audit log.</p>
        <form className="mt-5 grid gap-4 sm:grid-cols-2" onSubmit={handleManualCheckin}>
          <label className="text-sm font-semibold text-slate-700" htmlFor="manual-booking-id">Booking ID<input id="manual-booking-id" className="mt-2 min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-base" min={1} type="number" value={manualBookingId} onChange={(event) => setManualBookingId(event.target.value)} /></label>
          <label className="text-sm font-semibold text-slate-700 sm:col-span-2" htmlFor="manual-checkin-reason">Lý do xác nhận thủ công<textarea id="manual-checkin-reason" className="mt-2 min-h-24 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-base" maxLength={1000} value={manualReason} onChange={(event) => setManualReason(event.target.value)} /></label>
          {manualError ? <p className="text-sm font-medium text-red-700 sm:col-span-2" role="alert">{manualError}</p> : null}
          <Button className="sm:col-span-2 sm:justify-self-start" loading={manualCheckIn.isPending} loadingText="Đang xác nhận..." type="submit" variant="danger">Xác nhận fallback thủ công</Button>
        </form>
      </section>
    </div>
  );
}
