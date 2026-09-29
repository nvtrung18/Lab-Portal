import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import { Camera, CircleCheck, ScanFace } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';

import { queryKeys } from '../../../shared/api';
import { Button, EmptyState, ErrorState, Modal, toast } from '../../../shared/components';
import { translate, translateBackendMessage, useI18n } from '../../../shared/i18n';
import type { Response } from '../../../shared/types';
import { cameraErrorMessage, openCamera } from '../../../shared/utils';
import { invalidateAttendanceQueries } from '../../booking/hooks';
import {
  faceCheckin,
  getFaceCheckinCandidates,
  getFaceCheckinGuidance,
  startFaceCheckinPassiveSession,
} from '../api';
import type { FaceChallenge, FaceCheckinResult, FaceGuidanceResult, FaceImageRequest } from '../types';

type CameraFrame = Pick<FaceImageRequest, 'imageBase64' | 'contentType'>;
type FacePosition = Pick<FaceGuidanceResult, 'centerX' | 'centerY' | 'faceWidthRatio' | 'faceHeightRatio'>;

function wait(milliseconds: number) {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}

function errorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as Partial<Response<unknown>> | undefined;
    return translateBackendMessage(body?.message ?? body?.errors?.[0]) ?? translate('face.error');
  }
  return translateBackendMessage(error instanceof Error ? error.message : null) ?? translate('face.error');
}

function faceFailureMessage(reason: string | null) {
  const messages: Record<string, string> = {
    NO_FACE: translate('face.noFace'),
    MULTIPLE_FACES: translate('face.multipleFaces'),
    LOW_QUALITY: translate('face.lowQuality'),
    NO_MATCH: translate('face.noMatchManager'),
    SPOOF_DETECTED: translate('face.spoofDetected'),
    CHALLENGE_MISSING: translate('face.challengeMissing'),
    CHALLENGE_INVALID: translate('face.challengeInvalid'),
    CHALLENGE_FACE_INVALID: translate('face.observationInvalid'),
    CHALLENGE_START_NOT_FRONTAL: translate('face.notFrontal'),
    CHALLENGE_TURN_NOT_DETECTED: translate('face.challengeInvalid'),
    OBSERVATION_TOO_SHORT: translate('face.observationTooShort'),
    OBSERVATION_FACE_INVALID: translate('face.observationInvalid'),
    OBSERVATION_NOT_FRONTAL: translate('face.notFrontal'),
    OBSERVATION_NOT_LIVE: translate('face.notLive'),
    SERVICE_ERROR: translate('face.serviceError'),
  };
  return reason ? messages[reason] ?? reason : translate('face.unknownFailure');
}

function captureFrame(video: HTMLVideoElement, maxWidth?: number, quality = 0.84): Promise<CameraFrame> {
  const scale = maxWidth && video.videoWidth > maxWidth ? maxWidth / video.videoWidth : 1;
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(video.videoWidth * scale);
  canvas.height = Math.round(video.videoHeight * scale);
  const context = canvas.getContext('2d');
  if (!context) return Promise.reject(new Error(translate('face.readCameraError')));
  context.drawImage(video, 0, 0, canvas.width, canvas.height);
  const dataUrl = canvas.toDataURL('image/jpeg', quality);
  return Promise.resolve({ imageBase64: dataUrl.split(',', 2)[1], contentType: 'image/jpeg' });
}

function stablePosition(previous: FacePosition | null, current: FaceGuidanceResult) {
  if (!previous || previous.centerX === null || previous.centerY === null
    || previous.faceWidthRatio === null || previous.faceHeightRatio === null
    || current.centerX === null || current.centerY === null
    || current.faceWidthRatio === null || current.faceHeightRatio === null) return false;
  return Math.abs(previous.centerX - current.centerX) <= 0.025
    && Math.abs(previous.centerY - current.centerY) <= 0.025
    && Math.abs(previous.faceWidthRatio - current.faceWidthRatio) <= 0.035
    && Math.abs(previous.faceHeightRatio - current.faceHeightRatio) <= 0.035;
}

export function ManagerFaceCheckinPanel({ onCompleted }: { onCompleted: (result: FaceCheckinResult) => void }) {
  const { locale, t } = useI18n();
  const queryClient = useQueryClient();
  const candidates = useQuery({
    queryKey: queryKeys.face.checkinCandidates,
    queryFn: getFaceCheckinCandidates,
  });
  const [slotId, setSlotId] = useState('');
  const [cameraDialogOpen, setCameraDialogOpen] = useState(false);
  const [completedBookingId, setCompletedBookingId] = useState<number | null>(null);
  const [recognizedStudentName, setRecognizedStudentName] = useState<string | null>(null);
  const [cameraActive, setCameraActive] = useState(false);
  const [cameraReady, setCameraReady] = useState(false);
  const [challenge, setChallenge] = useState<FaceChallenge | null>(null);
  const [instruction, setInstruction] = useState(() => t('face.centerFace'));
  const [localError, setLocalError] = useState('');
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const previousPositionRef = useRef<FacePosition | null>(null);
  const stablePassesRef = useRef(0);
  const capturingRef = useRef(false);
  const slots = useMemo(() => Array.from(new Map(
    (candidates.data ?? []).map((candidate) => [candidate.slotId, {
      id: candidate.slotId,
      labName: candidate.labName,
      startTime: candidate.startTime,
      endTime: candidate.endTime,
    }]),
  ).values()), [candidates.data]);

  const stopCamera = () => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
    setCameraActive(false);
    setCameraReady(false);
  };

  useEffect(() => () => stopCamera(), []);
  useEffect(() => {
    if (cameraActive && videoRef.current && streamRef.current) {
      videoRef.current.srcObject = streamRef.current;
    }
  }, [cameraActive]);

  const checkin = useMutation({
    mutationFn: ({ primary, frames, activeChallenge }: {
      primary: CameraFrame;
      frames: CameraFrame[];
      activeChallenge: FaceChallenge;
    }) => faceCheckin(Number(slotId), {
      ...primary,
      challengeFrames: frames,
      challengeToken: activeChallenge.challengeToken,
    }),
    onSuccess: (result) => {
      if (!result.checkedIn) {
        setLocalError(faceFailureMessage(result.failureReason ?? result.result));
        return;
      }
      if (result.bookingId === null) {
        setLocalError(t('face.error'));
        return;
      }
      toast.success(translate('face.managerSuccessToast'));
      const completedCandidate = candidates.data?.find((candidate) => candidate.bookingId === result.bookingId);
      if (completedCandidate) invalidateAttendanceQueries(queryClient, completedCandidate);
      stopCamera();
      setCompletedBookingId(result.bookingId);
      setRecognizedStudentName(result.studentName);
      onCompleted(result);
    },
    onError: (error) => setLocalError(errorMessage(error)),
  });

  const runCapture = async () => {
    const video = videoRef.current;
    const activeChallenge = challenge;
    if (!video || !activeChallenge || capturingRef.current) return;
    capturingRef.current = true;
    try {
      setInstruction(t('face.managerStarting'));
      const primary = await captureFrame(video, undefined, 0.92);
      const frames: CameraFrame[] = [];
      for (let index = 0; index < 6; index += 1) {
        setInstruction(`${t('face.managerObserving')} (${index + 1}/6)`);
        await wait(400);
        frames.push(await captureFrame(video, undefined, 0.92));
      }
      setInstruction(t('face.managerComparing'));
      await checkin.mutateAsync({ primary, frames, activeChallenge });
    } catch (error) {
      setLocalError(errorMessage(error));
    } finally {
      capturingRef.current = false;
    }
  };

  useEffect(() => {
    if (!cameraActive || !cameraReady) return undefined;
    let cancelled = false;
    let timer: number | undefined;
    const evaluate = async () => {
      const video = videoRef.current;
      if (!video || capturingRef.current || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) {
        timer = window.setTimeout(evaluate, 800);
        return;
      }
      try {
        const result = await getFaceCheckinGuidance(await captureFrame(video, 640, 0.76));
        if (cancelled) return;
        const stable = stablePosition(previousPositionRef.current, result);
        const passed = result.singleFace && result.faceInGuide && result.facingForward
          && result.landmarksVisible && result.lightingGood && result.sharpnessGood && stable;
        const nextInstruction = !result.singleFace
          ? result.detectedFaces === 0 ? t('face.putOneFace') : t('face.multipleFaces')
          : !result.faceInGuide
            ? t('face.centerFace')
            : !result.facingForward || !result.landmarksVisible
              ? t('face.lookForward')
              : !result.lightingGood
                ? t('face.frontLighting')
                : !result.sharpnessGood || !stable
                  ? t('face.keepStill')
                  : t('face.managerReady');
        previousPositionRef.current = result.singleFace ? result : null;
        stablePassesRef.current = passed ? stablePassesRef.current + 1 : 0;
        setInstruction(nextInstruction);
        if (stablePassesRef.current >= 3 && !capturingRef.current) void runCapture();
      } catch (error) {
        if (!cancelled) setLocalError(errorMessage(error));
      } finally {
        if (!cancelled) timer = window.setTimeout(evaluate, 800);
      }
    };
    void evaluate();
    return () => {
      cancelled = true;
      if (timer) window.clearTimeout(timer);
    };
  }, [cameraActive, cameraReady, challenge, slotId]);

  const startCamera = async () => {
    if (!slotId) {
      setLocalError(t('face.chooseSlot'));
      return;
    }
    setCameraDialogOpen(true);
    setCompletedBookingId(null);
    setRecognizedStudentName(null);
    try {
      const stream = await openCamera('user');
      streamRef.current = stream;
      const activeChallenge = await startFaceCheckinPassiveSession();
      if (activeChallenge.action !== 'OBSERVE') throw new Error(t('face.cameraSessionInvalid'));
      setChallenge(activeChallenge);
      setLocalError('');
      setInstruction(t('face.centerFace'));
      previousPositionRef.current = null;
      stablePassesRef.current = 0;
      setCameraActive(true);
    } catch (error) {
      stopCamera();
      setLocalError(cameraErrorMessage(error));
    }
  };

  const closeCameraDialog = () => {
    if (checkin.isPending || capturingRef.current) return;
    stopCamera();
    setCameraDialogOpen(false);
    setCompletedBookingId(null);
    setRecognizedStudentName(null);
    setSlotId('');
    setLocalError('');
  };

  if (candidates.isLoading) return <div className="mt-4 h-48 animate-pulse rounded bg-slate-100 dark:bg-slate-800" />;
  if (candidates.isError) return <ErrorState className="mt-4" onRetry={() => void candidates.refetch()}>{t('face.managerCandidatesError')}</ErrorState>;
  if ((candidates.data?.length ?? 0) === 0) return <EmptyState className="mt-4">{t('face.managerNoCandidates')}</EmptyState>;

  return (
    <div className="mt-4">
      <div className="max-w-3xl">
        <label className="block text-sm font-semibold text-slate-700 dark:text-slate-200" htmlFor="face-slot">
          {t('face.slot')}
          <select id="face-slot" className="mt-2 min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-base text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-700 dark:bg-slate-900 dark:text-white" disabled={cameraActive} value={slotId} onChange={(event) => setSlotId(event.target.value)}>
            <option value="">{t('face.chooseSlotOption')}</option>
            {slots.map((slot) => <option key={slot.id} value={slot.id}>{slot.labName} — {new Date(slot.startTime).toLocaleString(locale === 'en' ? 'en-US' : 'vi-VN')}–{new Date(slot.endTime).toLocaleTimeString(locale === 'en' ? 'en-US' : 'vi-VN')}</option>)}
          </select>
        </label>
        <div className="mt-3 flex flex-wrap gap-2">
          <Button className="w-full sm:w-auto" disabled={!slotId || cameraActive} type="button" onClick={() => void startCamera()}><Camera aria-hidden="true" className="h-4 w-4" /> {t('face.openAndRecognize')}</Button>
        </div>
        {localError && !cameraDialogOpen ? <p className="mt-3 rounded-md border border-red-200 bg-red-50 p-3 text-sm font-medium text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200" role="alert">{localError}</p> : null}
      </div>

      <Modal
        backdropBlur
        centered
        closeDisabled={checkin.isPending || capturingRef.current}
        closeOnEscape
        isOpen={cameraDialogOpen}
        onClose={closeCameraDialog}
        size={completedBookingId ? 'lg' : '2xl'}
        title={completedBookingId ? t('face.managerResultTitle') : t('face.managerCameraTitle')}
      >
        {completedBookingId ? (
          <div className="flex flex-col items-center py-8 text-center" role="status">
            <span className="flex h-16 w-16 items-center justify-center rounded-full bg-emerald-100 text-emerald-700"><CircleCheck aria-hidden="true" className="h-9 w-9" /></span>
            <h3 className="mt-4 text-xl font-semibold text-slate-950 dark:text-white">{t('face.managerSuccessTitle')}</h3>
            {recognizedStudentName ? <p className="mt-2 text-base font-semibold text-slate-800">{recognizedStudentName}</p> : null}
            <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">{t('face.managerSuccessDetail')} #{completedBookingId}.</p>
            <Button className="mt-6" type="button" onClick={closeCameraDialog}>{t('common.close')}</Button>
          </div>
        ) : (
          <div className="mx-auto w-full max-w-4xl">
              <div className="relative aspect-[3/4] overflow-hidden rounded-md bg-slate-950 shadow-lg sm:aspect-video">
                {cameraActive ? <video ref={videoRef} autoPlay muted playsInline className="h-full w-full -scale-x-100 object-cover" aria-label={t('face.managerCameraLabel')} onLoadedMetadata={() => setCameraReady(true)} /> : <div className="flex h-full items-center justify-center text-slate-400"><ScanFace aria-hidden="true" className="h-16 w-16" /></div>}
                {cameraActive ? <div aria-hidden="true" className="pointer-events-none absolute inset-x-[18%] inset-y-8 rounded-full border-2 border-dashed border-white/90 shadow-[0_0_0_999px_rgba(15,23,42,0.28)] sm:inset-x-1/4 sm:inset-y-4" /> : null}
                {cameraActive ? <p aria-live="assertive" className="absolute inset-x-3 bottom-3 rounded bg-slate-950/80 px-3 py-2 text-center text-sm font-semibold text-white">{instruction}</p> : null}
                {localError ? <p className="absolute inset-x-3 top-3 rounded bg-red-700/90 px-3 py-2 text-center text-sm font-medium text-white" role="alert">{localError}</p> : null}
              </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
