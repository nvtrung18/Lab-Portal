import type { IScannerControls } from '@zxing/browser';
import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import { CheckCircle2, Clock3, Keyboard, QrCode, ScanFace } from 'lucide-react';
import { FormEvent, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

import type { Response } from '../../../shared/types';
import { queryKeys } from '../../../shared/api';
import { Button, EmptyState, ErrorState, Modal, ResponsiveTable } from '../../../shared/components';
import { translateBackendMessage, useI18n } from '../../../shared/i18n';
import { cameraErrorMessage } from '../../../shared/utils';
import { useOperationalLogs } from '../../operations/hooks';
import type { FaceCheckinLog } from '../../operations/types';
import { ManagerFaceCheckinPanel } from '../../face/components/ManagerFaceCheckinPanel';
import { getFaceCheckinCandidates } from '../../face/api';
import type { BookingResponse } from '../api';
import { useConfirmCheckIn, useManualCheckIn, usePendingCheckinQrRequests, useReviewCheckinQrRequest } from '../hooks';
import { getCheckinQrReasonLabel } from '../utils';

type CheckinTab = 'QR' | 'FACE' | 'MANUAL';

const faceResultKeys: Record<string, string> = {
  SUCCESS: 'common.success',
  FAILED: 'operations.failed',
  DENIED: 'operations.denied',
};

const faceFailureKeys: Record<string, string> = {
  NO_MATCH: 'operations.noMatch',
  NO_FACE: 'operations.noFace',
  MULTIPLE_FACES: 'operations.multipleFaces',
  LOW_QUALITY: 'operations.lowQuality',
  SPOOF_DETECTED: 'operations.spoofDetected',
  CHALLENGE_MISSING: 'face.challengeMissing',
  CHALLENGE_INVALID: 'face.challengeInvalid',
  CHALLENGE_FACE_INVALID: 'face.observationInvalid',
  CHALLENGE_START_NOT_FRONTAL: 'face.notFrontal',
  CHALLENGE_TURN_NOT_DETECTED: 'face.challengeInvalid',
  OBSERVATION_TOO_SHORT: 'face.observationTooShort',
  OBSERVATION_FACE_INVALID: 'face.observationInvalid',
  OBSERVATION_NOT_FRONTAL: 'face.notFrontal',
  OBSERVATION_NOT_LIVE: 'face.notLive',
  SERVICE_ERROR: 'operations.serviceError',
};

function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const response = error.response?.data as Partial<Response<unknown>> | undefined;
    return translateBackendMessage(response?.message ?? response?.errors?.[0]) ?? fallback;
  }
  return translateBackendMessage(error instanceof Error ? error.message : null) ?? fallback;
}

export function CheckInPage() {
  const { locale, t } = useI18n();
  const [activeTab, setActiveTab] = useState<CheckinTab>('FACE');
  const [token, setToken] = useState('');
  const [error, setError] = useState('');
  const [cameraError, setCameraError] = useState('');
  const [isCameraActive, setIsCameraActive] = useState(false);
  const [qrCameraDialogOpen, setQrCameraDialogOpen] = useState(false);
  const [lastSuccess, setLastSuccess] = useState<{
    method: CheckinTab;
    bookingId: number;
    studentName?: string | null;
    booking?: BookingResponse;
  } | null>(null);
  const confirmCheckIn = useConfirmCheckIn();
  const manualCheckIn = useManualCheckIn();
  const qrRequests = usePendingCheckinQrRequests(activeTab === 'QR');
  const reviewQrRequest = useReviewCheckinQrRequest();
  const faceLogs = useOperationalLogs('face-checkins', {}, 0, 50);
  const candidates = useQuery({
    queryKey: queryKeys.face.checkinCandidates,
    queryFn: getFaceCheckinCandidates,
  });
  const [manualBookingId, setManualBookingId] = useState('');
  const [manualReason, setManualReason] = useState('');
  const [manualError, setManualError] = useState('');
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const scannerControlsRef = useRef<IScannerControls | null>(null);
  const scannerSessionRef = useRef(0);
  const handledTokenRef = useRef(false);

  const stopCamera = () => {
    scannerSessionRef.current += 1;
    scannerControlsRef.current?.stop();
    scannerControlsRef.current = null;
    const video = videoRef.current;
    if (video?.srcObject instanceof MediaStream) {
      video.srcObject.getTracks().forEach((track) => track.stop());
      video.srcObject = null;
    }
    setIsCameraActive(false);
  };

  useEffect(() => stopCamera, []);

  const confirmToken = (value: string) => {
    const normalizedToken = value.trim();
    if (!normalizedToken) {
      setError(t('checkin.qrTokenRequired'));
      return;
    }

    setError('');
    setLastSuccess(null);
    confirmCheckIn.mutate(normalizedToken, {
      onSuccess: (result) => {
        setLastSuccess({ method: 'QR', bookingId: result.booking.id, booking: result.booking });
        setToken('');
        handledTokenRef.current = false;
      },
      onError: (submitError) => {
        setError(getErrorMessage(submitError, t('common.serverFailure')));
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
    setLastSuccess(null);
    handledTokenRef.current = false;

    if (!navigator.mediaDevices?.getUserMedia) {
      setCameraError(t('checkin.cameraPermissionHint'));
      return;
    }

    try {
      setQrCameraDialogOpen(true);
      setIsCameraActive(true);
      await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()));
      const session = scannerSessionRef.current + 1;
      scannerSessionRef.current = session;
      const video = videoRef.current;
      if (!video) {
        setCameraError(t('checkin.cameraNotFound'));
        setIsCameraActive(false);
        return;
      }
      const { BrowserQRCodeReader } = await import('@zxing/browser');
      const reader = new BrowserQRCodeReader(undefined, { delayBetweenScanAttempts: 200 });
      const controls = await reader.decodeFromConstraints(
        { video: { facingMode: { ideal: 'environment' } }, audio: false },
        video,
        (result, _scanError, activeControls) => {
          if (scannerSessionRef.current !== session) {
            activeControls.stop();
            return;
          }
          const scannedToken = result?.getText().trim();
          if (!scannedToken || handledTokenRef.current) return;
          handledTokenRef.current = true;
          setToken(scannedToken);
          scannerSessionRef.current += 1;
          activeControls.stop();
          scannerControlsRef.current = null;
          setIsCameraActive(false);
          confirmToken(scannedToken);
        },
      );
      if (scannerSessionRef.current !== session) {
        controls.stop();
        return;
      }
      scannerControlsRef.current = controls;
    } catch (cameraError) {
      setCameraError(`${cameraErrorMessage(cameraError)} ${t('checkin.cameraManualHint')}`);
      stopCamera();
    }
  };

  const closeQrCameraDialog = () => {
    if (confirmCheckIn.isPending) return;
    stopCamera();
    setQrCameraDialogOpen(false);
    setCameraError('');
  };

  const handleManualCheckin = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (Number(manualBookingId) <= 0 || !manualReason.trim()) {
      setManualError(t('checkin.manualReasonRequired'));
      return;
    }
    setManualError('');
    manualCheckIn.mutate(
      { bookingId: Number(manualBookingId), reason: manualReason.trim() },
      { onSuccess: (result) => {
        setLastSuccess({ method: 'MANUAL', bookingId: result.booking.id, booking: result.booking });
        setManualBookingId('');
        setManualReason('');
      } },
    );
  };

  const faceAttempts = (faceLogs.data?.items ?? []).filter(
    (item): item is FaceCheckinLog => 'method' in item && item.method === 'FACE',
  );

  return (
    <div className="mx-auto max-w-7xl">
      <section className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800 sm:p-6">
        <div>
          <p className="text-sm font-semibold text-blue-700 dark:text-blue-300">{t('layout.managerPortal')}</p>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-950 dark:text-white">{t('checkin.title')}</h1>
        </div>

        <div className="mt-5 flex items-start gap-3 rounded-lg border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900 dark:border-blue-900 dark:bg-blue-950/40 dark:text-blue-100">
          <Clock3 aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0" />
          <div><p className="font-semibold">{t('manager.checkinWindow')}</p></div>
        </div>

        <div className="mt-6 flex flex-col gap-2 sm:flex-row sm:flex-wrap" role="tablist" aria-label={t('checkin.managerTabs')}>
          {([
            { id: 'FACE' as const, label: t('checkin.tabFace'), icon: ScanFace },
            { id: 'QR' as const, label: t('checkin.tabQr'), icon: QrCode },
            { id: 'MANUAL' as const, label: t('checkin.tabManual'), icon: Keyboard },
          ]).map(({ id, label, icon: Icon }) => <button aria-selected={activeTab === id} className={activeTab === id ? 'flex min-h-11 w-full items-center justify-center gap-2 rounded-md bg-slate-900 px-4 text-sm font-semibold text-white shadow-sm dark:bg-white dark:text-slate-950 sm:w-auto' : 'flex min-h-11 w-full items-center justify-center gap-2 rounded-md border border-slate-300 px-4 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800 sm:w-auto'} key={id} role="tab" type="button" onClick={() => { stopCamera(); setActiveTab(id); }}><Icon aria-hidden="true" className="h-4 w-4" />{label}</button>)}
        </div>

        {lastSuccess ? (
          <div className="mt-5 flex items-start gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-100" role="status">
            <CheckCircle2 aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0" />
            <div><p className="font-semibold">{t('checkin.successWithMethod', undefined, { method: lastSuccess.method === 'FACE' ? t('checkin.tabFace') : lastSuccess.method === 'QR' ? 'QR' : t('checkin.tabManual') })}</p><p className="mt-1">{t('checkin.successDetail', undefined, { bookingId: String(lastSuccess.bookingId), student: lastSuccess.studentName ?? lastSuccess.booking?.studentName ?? lastSuccess.booking?.studentEmail ?? `${t('common.user')} #${lastSuccess.booking?.userId ?? ''}` })}</p></div>
          </div>
        ) : null}

        {activeTab === 'QR' ? <><section className="mt-6 rounded-lg border border-blue-200 bg-blue-50 p-4">
          <h2 className="font-semibold text-slate-950">{t('checkin.qrPendingRequests')}</h2>
          {qrRequests.isLoading ? <p className="mt-4 text-sm text-slate-500">{t('common.loading')}</p> : qrRequests.isError ? <ErrorState className="mt-4" onRetry={() => void qrRequests.refetch()}>{t('checkin.qrLoadFailed')}</ErrorState> : !qrRequests.data?.length ? <EmptyState className="mt-4">{t('checkin.qrEmptyPending')}</EmptyState> : (
            <div className="mt-4 space-y-3">{qrRequests.data.map((request) => (
              <article className="rounded-md border border-blue-200 bg-white p-4" key={request.requestId}>
                <p className="font-semibold text-slate-900">{request.studentName ?? `${t('common.user')} #${request.studentId}`} · {t('checkin.bookingLabel')} #{request.bookingId}</p>
                <p className="mt-1 break-words text-sm text-slate-700">{t('checkin.reason')}: {getCheckinQrReasonLabel(request.fallbackReason, request.reason, locale)}</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <Button disabled={reviewQrRequest.isPending} onClick={() => reviewQrRequest.mutate({ requestId: request.requestId, approved: true })}>{t('checkin.approveAndIssueQr')}</Button>
                  <Button disabled={reviewQrRequest.isPending} variant="danger" onClick={() => reviewQrRequest.mutate({ requestId: request.requestId, approved: false })}>{t('common.reject')}</Button>
                </div>
              </article>
            ))}</div>
          )}
        </section><div className="mt-6 rounded-lg border border-slate-200 bg-slate-50 p-4">
          <div className="flex flex-col gap-2 sm:flex-row">
            <button
              className="min-h-11 w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 disabled:bg-slate-400 sm:w-auto"
              disabled={confirmCheckIn.isPending || isCameraActive}
              type="button"
              onClick={startCamera}
            >
              {isCameraActive ? t('checkin.scanningQr') : t('checkin.openQrCamera')}
            </button>
          </div>
          {cameraError && !qrCameraDialogOpen ? <p className="mt-3 text-sm font-medium text-amber-700">{cameraError}</p> : null}
        </div>

        <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
          <label className="block text-sm font-semibold text-slate-700" htmlFor="checkin-token">
            {t('checkin.fallbackCode')}
          </label>
          <textarea
            id="checkin-token"
            className="min-h-24 w-full rounded-md border border-slate-300 px-3 py-2 text-base text-slate-900 outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-200"
            placeholder={t('checkin.tokenPlaceholder')}
            value={token}
            onChange={(event) => setToken(event.target.value)}
          />

          {error ? (
            <div className="rounded-md border border-red-200 bg-red-50 px-3 py-3 text-sm font-semibold text-red-700">
              {error}
            </div>
          ) : null}

          <button
            className="min-h-11 w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 disabled:cursor-not-allowed disabled:bg-slate-400 sm:w-auto"
            disabled={confirmCheckIn.isPending}
            type="submit"
          >
            {confirmCheckIn.isPending ? t('checkin.confirming') : t('common.confirm')}
          </button>
        </form>
        </> : null}

        {activeTab === 'FACE' ? <section className="mt-6" role="tabpanel">
          <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-semibold text-slate-950">{t('face.managerResultTitle')}</h2><div className="flex flex-wrap gap-2"><Button type="button" variant="outline" onClick={() => void faceLogs.refetch()}>{t('common.retry')}</Button><Link className="app-button-link text-sm font-semibold" to="/app/operational-logs">{t('checkin.viewAllLogs')}</Link></div></div>
          <ManagerFaceCheckinPanel onCompleted={(result) => { if (result.bookingId !== null) setLastSuccess({ method: 'FACE', bookingId: result.bookingId, studentName: result.studentName }); void faceLogs.refetch(); }} />
          <h3 className="mt-8 text-base font-semibold text-slate-950">{t('checkin.recentFaceLogs')}</h3>
          {faceLogs.isLoading ? <div className="mt-4 h-28 animate-pulse rounded bg-slate-100" /> : faceLogs.isError ? <ErrorState className="mt-4" onRetry={() => void faceLogs.refetch()}>{t('checkin.logsLoadFailed')}</ErrorState> : faceAttempts.length === 0 ? <EmptyState className="mt-4">{t('checkin.logsEmpty')}</EmptyState> : (
            <>
              <div className="mt-4 space-y-3 md:hidden">
                {faceAttempts.map((attempt) => (
                  <article className="min-w-0 rounded-lg border border-slate-200 p-4 text-sm" key={attempt.id}>
                    <p className="break-words font-semibold text-slate-950">#{attempt.bookingId} · {attempt.studentName ?? `${t('common.user')} #${attempt.userId}`}</p>
                    <dl className="mt-3 space-y-2">
                      <div><dt className="text-slate-500">{t('operations.time')}</dt><dd className="mt-1 text-slate-800">{new Date(attempt.createdAt).toLocaleString(locale === 'en' ? 'en-US' : 'vi-VN')}</dd></div>
                      <div><dt className="text-slate-500">{t('operations.result')}</dt><dd className="mt-1 text-slate-800">{t(faceResultKeys[attempt.result] ?? '', attempt.result)}</dd></div>
                      <div><dt className="text-slate-500">{t('operations.safeDetail')}</dt><dd className="mt-1 break-words text-slate-800">{attempt.failureReason ? t(faceFailureKeys[attempt.failureReason] ?? '', attempt.failureReason) : t('operations.noFailure')}</dd></div>
                    </dl>
                  </article>
                ))}
              </div>
              <ResponsiveTable className="mt-4 hidden md:block"><table className="w-full min-w-[720px] text-left text-sm"><thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold tracking-normal text-slate-600 dark:border-slate-800 dark:bg-slate-800/60 dark:text-slate-300"><tr><th className="px-4 py-3 font-semibold">{t('operations.time')}</th><th className="px-4 py-3 font-semibold">{t('operations.bookingId')}</th><th className="px-4 py-3 font-semibold">{t('layout.studentRole')}</th><th className="px-4 py-3 font-semibold">{t('operations.result')}</th><th className="px-4 py-3 font-semibold">{t('operations.safeDetail')}</th></tr></thead><tbody className="divide-y divide-slate-200">{faceAttempts.map((attempt) => <tr key={attempt.id}><td className="whitespace-nowrap px-4 py-3">{new Date(attempt.createdAt).toLocaleString(locale === 'en' ? 'en-US' : 'vi-VN')}</td><td className="px-4 py-3 font-medium">#{attempt.bookingId}</td><td className="px-4 py-3">{attempt.studentName ?? `${t('common.user')} #${attempt.userId}`}</td><td className="px-4 py-3">{t(faceResultKeys[attempt.result] ?? '', attempt.result)}</td><td className="px-4 py-3">{attempt.failureReason ? t(faceFailureKeys[attempt.failureReason] ?? '', attempt.failureReason) : t('operations.noFailure')}</td></tr>)}</tbody></table></ResponsiveTable>
            </>
          )}
        </section> : null}

        {activeTab === 'MANUAL' ? <section className="mt-6 rounded-lg border border-amber-200 bg-amber-50 p-6 dark:border-amber-900 dark:bg-amber-950/30" role="tabpanel">
          <p className="text-sm font-semibold tracking-normal text-amber-700 dark:text-amber-300">{t('checkin.controlledFallback')}</p>
          <h2 className="mt-1 text-xl font-semibold text-slate-950 dark:text-white">{t('checkin.manualTitle')}</h2>
          {candidates.isError ? <ErrorState className="mt-5" onRetry={() => void candidates.refetch()}>{t('checkin.bookingWindowLoadFailed')}</ErrorState> : null}
          <form className="mt-5 grid gap-4 sm:grid-cols-2" onSubmit={handleManualCheckin}>
            <label className="text-sm font-semibold text-slate-700 dark:text-slate-200 sm:col-span-2" htmlFor="manual-booking-id">{t('checkin.memberAndSession')}<select id="manual-booking-id" className="mt-2 min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-base text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:cursor-wait disabled:opacity-60 dark:border-slate-700 dark:bg-slate-900 dark:text-white" disabled={candidates.isLoading || candidates.isError} value={manualBookingId} onChange={(event) => setManualBookingId(event.target.value)}><option value="">{candidates.isLoading ? t('common.loading') : t('checkin.chooseBookingWindow')}</option>{candidates.data?.map((candidate) => <option key={candidate.bookingId} value={candidate.bookingId}>{candidate.studentName ?? candidate.studentEmail} — {new Date(candidate.startTime).toLocaleString(locale === 'en' ? 'en-US' : 'vi-VN')}</option>)}</select></label>
            <label className="text-sm font-semibold text-slate-700 dark:text-slate-200 sm:col-span-2" htmlFor="manual-checkin-reason">{t('checkin.manualReasonLabel')}<textarea id="manual-checkin-reason" className="mt-2 min-h-24 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-base text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:border-slate-700 dark:bg-slate-900 dark:text-white" maxLength={1000} value={manualReason} onChange={(event) => setManualReason(event.target.value)} /></label>
            {manualError ? <p className="text-sm font-medium text-red-700 sm:col-span-2" role="alert">{manualError}</p> : null}
            <Button className="sm:col-span-2 sm:justify-self-start" loading={manualCheckIn.isPending} loadingText={t('checkin.confirming')} type="submit" variant="danger">{t('checkin.confirmManual')}</Button>
          </form>
        </section> : null}
      </section>

      <Modal
        backdropBlur
        centered
        closeDisabled={confirmCheckIn.isPending}
        closeOnEscape
        isOpen={qrCameraDialogOpen}
        onClose={closeQrCameraDialog}
        size={lastSuccess?.method === 'QR' ? 'lg' : '2xl'}
        title={lastSuccess?.method === 'QR' ? t('checkin.qrScanResult') : t('checkin.qrCameraTitle')}
      >
        {lastSuccess?.method === 'QR' ? (
          <div className="flex flex-col items-center py-8 text-center" role="status">
            <span className="flex h-16 w-16 items-center justify-center rounded-full bg-emerald-100 text-emerald-700"><CheckCircle2 aria-hidden="true" className="h-9 w-9" /></span>
            <h3 className="mt-4 text-xl font-semibold text-slate-950">{t('face.managerSuccessTitle')}</h3>
            <p className="mt-2 text-sm text-slate-600">{t('checkin.successDetail', undefined, { bookingId: String(lastSuccess.bookingId), student: lastSuccess.booking?.studentName ?? lastSuccess.booking?.studentEmail ?? `${t('common.user')} #${lastSuccess.booking?.userId ?? ''}` })}</p>
            <Button className="mt-6" type="button" onClick={closeQrCameraDialog}>{t('common.confirm')}</Button>
          </div>
        ) : (
          <div className="mx-auto w-full max-w-4xl">
            <div className="relative mx-auto aspect-[3/4] w-full overflow-hidden rounded-md bg-slate-950 sm:aspect-video">
              <video ref={videoRef} autoPlay className="h-full w-full object-cover" muted playsInline aria-label={t('checkin.qrCameraTitle')} />
              <div aria-hidden="true" className="pointer-events-none absolute left-1/2 top-1/2 aspect-square w-[68%] -translate-x-1/2 -translate-y-1/2 rounded-lg border-2 border-white shadow-[0_0_0_999px_rgba(15,23,42,0.35)] sm:w-[58%]" />
              <p className="absolute inset-x-3 bottom-3 rounded bg-slate-950/80 px-3 py-2 text-center text-sm font-semibold text-white" aria-live="polite">{confirmCheckIn.isPending ? t('checkin.qrReadConfirming') : isCameraActive ? t('checkin.searchingQr') : t('checkin.cameraStopped')}</p>
              {cameraError || error ? <p className="absolute inset-x-3 top-3 rounded bg-red-700/90 px-3 py-2 text-center text-sm font-medium text-white" role="alert">{cameraError || error}</p> : null}
            </div>
          </div>
        )}
      </Modal>

    </div>
  );
}
