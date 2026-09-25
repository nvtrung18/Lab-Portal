import axios from 'axios';
import { Camera, CircleCheck, ScanFace } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import type { BookingResponse } from '../../booking/api';
import { Button, Modal } from '../../../shared/components';
import { translate, translateBackendMessage, useI18n } from '../../../shared/i18n';
import { cameraErrorMessage, getCurrentCoordinates, openCamera } from '../../../shared/utils';
import { getFaceGuidance, startStudentFaceCheckinPassiveSession } from '../api';
import { useStudentFaceCheckin } from '../hooks';
import type { FaceChallenge, FaceCheckinResult, FaceGuidanceResult, FaceImageRequest } from '../types';

type CameraFrame = Pick<FaceImageRequest, 'imageBase64' | 'contentType'>;

function wait(milliseconds: number) {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}

function errorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as { message?: string; errors?: string[] } | undefined;
    return translateBackendMessage(body?.message ?? body?.errors?.[0]) ?? translate('face.error');
  }
  return translateBackendMessage(error instanceof Error ? error.message : null) ?? translate('face.error');
}

function faceFailureMessage(reason: string | null) {
  const messages: Record<string, string> = {
    NO_FACE: translate('face.noFace'),
    MULTIPLE_FACES: translate('face.multipleFaces'),
    LOW_QUALITY: translate('face.lowQuality'),
    NO_MATCH: translate('face.noMatch'),
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

function captureFrame(video: HTMLVideoElement, maxWidth?: number, quality = 0.84): CameraFrame {
  const scale = maxWidth && video.videoWidth > maxWidth ? maxWidth / video.videoWidth : 1;
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(video.videoWidth * scale);
  canvas.height = Math.round(video.videoHeight * scale);
  const context = canvas.getContext('2d');
  if (!context) throw new Error(translate('face.readCameraError'));
  context.drawImage(video, 0, 0, canvas.width, canvas.height);
  const encoded = canvas.toDataURL('image/jpeg', quality).split(',', 2)[1];
  if (!encoded) throw new Error(translate('face.invalidImageData'));
  return { imageBase64: encoded, contentType: 'image/jpeg' };
}

function guidanceMessage(result: FaceGuidanceResult) {
  if (!result.singleFace) return result.detectedFaces === 0 ? translate('face.putOneFace') : translate('face.multipleFaces');
  if (!result.faceInGuide) return translate('face.centerFace');
  if (!result.facingForward || !result.landmarksVisible) return translate('face.lookForward');
  if (!result.lightingGood) return translate('face.frontLighting');
  if (!result.sharpnessGood) return translate('face.keepStill');
  return translate('face.ready');
}

function isGuidanceReady(result: FaceGuidanceResult) {
  return result.singleFace && result.faceInGuide && result.facingForward
    && result.landmarksVisible && result.lightingGood && result.sharpnessGood;
}

interface StudentFaceCheckinPanelProps {
  booking: BookingResponse;
  disabled?: boolean;
}

export function StudentFaceCheckinPanel({ booking, disabled = false }: StudentFaceCheckinPanelProps) {
  const { t } = useI18n();
  const [cameraDialogOpen, setCameraDialogOpen] = useState(false);
  const [cameraActive, setCameraActive] = useState(false);
  const [cameraReady, setCameraReady] = useState(false);
  const [faceReady, setFaceReady] = useState(false);
  const [challenge, setChallenge] = useState<FaceChallenge | null>(null);
  const [instruction, setInstruction] = useState(() => t('face.centerFace'));
  const [localError, setLocalError] = useState('');
  const [completed, setCompleted] = useState<FaceCheckinResult | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const capturingRef = useRef(false);
  const checkin = useStudentFaceCheckin(booking);

  const stopCamera = () => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
    setCameraActive(false);
    setCameraReady(false);
    setFaceReady(false);
  };

  useEffect(() => () => stopCamera(), []);
  useEffect(() => {
    if (cameraActive && videoRef.current && streamRef.current) {
      videoRef.current.srcObject = streamRef.current;
    }
  }, [cameraActive]);

  useEffect(() => {
    if (!cameraActive || !cameraReady || completed) return undefined;
    let cancelled = false;
    let timer: number | undefined;

    const evaluate = async () => {
      const video = videoRef.current;
      if (!video || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) {
        timer = window.setTimeout(evaluate, 800);
        return;
      }
      try {
        const result = await getFaceGuidance(null, captureFrame(video, 640, 0.76));
        if (cancelled) return;
        setFaceReady(isGuidanceReady(result));
        setInstruction(guidanceMessage(result));
      } catch (error) {
        if (!cancelled) setLocalError(errorMessage(error));
      } finally {
        if (!cancelled) timer = window.setTimeout(evaluate, 900);
      }
    };

    void evaluate();
    return () => {
      cancelled = true;
      if (timer) window.clearTimeout(timer);
    };
  }, [cameraActive, cameraReady, completed]);

  const startCamera = async () => {
    if (disabled) return;
    setCameraDialogOpen(true);
    setCompleted(null);
    setLocalError('');
    try {
      const stream = await openCamera('user');
      streamRef.current = stream;
      const activeChallenge = await startStudentFaceCheckinPassiveSession();
      if (activeChallenge.action !== 'OBSERVE') throw new Error(t('face.cameraSessionInvalid'));
      setChallenge(activeChallenge);
      setInstruction(t('face.centerFace'));
      setCameraActive(true);
    } catch (error) {
      stopCamera();
      setLocalError(cameraErrorMessage(error));
    }
  };

  const captureAndCheckIn = async () => {
    const video = videoRef.current;
    if (!video || !challenge || !faceReady || capturingRef.current || checkin.isPending) return;
    capturingRef.current = true;
    try {
      setLocalError('');
      setInstruction(t('face.gettingLocation'));
      const coordinates = await getCurrentCoordinates();
      setInstruction(t('face.capturing'));
      const primary = captureFrame(video, undefined, 0.92);
      const frames: CameraFrame[] = [];
      for (let index = 0; index < 4; index += 1) {
        setInstruction(`${t('face.liveVerification')} (${index + 1}/4)`);
        await wait(400);
        frames.push(captureFrame(video, undefined, 0.92));
      }
      setInstruction(t('face.checking'));
      const result = await checkin.mutateAsync({
        bookingId: booking.id,
        latitude: coordinates.latitude,
        longitude: coordinates.longitude,
        accuracyMeters: coordinates.accuracyMeters,
        ...primary,
        challengeFrames: frames,
        challengeToken: challenge.challengeToken,
      });
      if (!result.checkedIn) {
        setLocalError(faceFailureMessage(result.failureReason ?? result.result));
        return;
      }
      setCompleted(result);
      stopCamera();
    } catch (error) {
      setLocalError(errorMessage(error));
    } finally {
      capturingRef.current = false;
    }
  };

  const closeCameraDialog = () => {
    if (checkin.isPending || capturingRef.current) return;
    stopCamera();
    setCameraDialogOpen(false);
    setChallenge(null);
    setCompleted(null);
    setLocalError('');
  };

  return (
    <>
      <Button className="mt-3 w-full sm:w-auto" disabled={disabled} type="button" onClick={() => void startCamera()}>
        <ScanFace aria-hidden="true" className="h-4 w-4" />
        {t('face.checkinButton')}
      </Button>
      <Modal
        backdropBlur
        centered
        closeDisabled={checkin.isPending || capturingRef.current}
        closeOnEscape
        isOpen={cameraDialogOpen}
        onClose={closeCameraDialog}
        size={completed ? 'lg' : '2xl'}
        title={completed ? t('face.successTitle') : t('face.verifyAndCheckin')}
      >
        {completed ? (
          <div className="flex flex-col items-center py-8 text-center" role="status">
            <span className="flex h-16 w-16 items-center justify-center rounded-full bg-emerald-100 text-emerald-700">
              <CircleCheck aria-hidden="true" className="h-9 w-9" />
            </span>
            <h3 className="mt-4 text-xl font-semibold text-slate-950 dark:text-white">{t('face.confirmed')}</h3>
            <p className="mt-2 text-sm text-slate-600 dark:text-slate-300">
              {t('face.confirmedDetail')} {completed.bookingId}.
            </p>
            <Button className="mt-6" type="button" onClick={closeCameraDialog}>{t('common.close')}</Button>
          </div>
        ) : (
          <div className="mx-auto w-full max-w-4xl">
            <div className="relative aspect-[3/4] overflow-hidden rounded-md bg-slate-950 shadow-lg sm:aspect-video">
              {cameraActive ? (
                <video
                  ref={videoRef}
                  autoPlay
                  muted
                  playsInline
                  className="h-full w-full -scale-x-100 object-cover"
                  aria-label={t('face.cameraLabel')}
                  onLoadedMetadata={() => setCameraReady(true)}
                />
              ) : (
                <div className="flex h-full items-center justify-center text-slate-400"><Camera aria-hidden="true" className="h-16 w-16" /></div>
              )}
              {cameraActive ? <div aria-hidden="true" className="pointer-events-none absolute inset-x-[18%] inset-y-8 rounded-full border-2 border-dashed border-white/90 shadow-[0_0_0_999px_rgba(15,23,42,0.28)] sm:inset-x-1/4 sm:inset-y-4" /> : null}
              {cameraActive ? <p aria-live="assertive" className="absolute inset-x-3 bottom-3 rounded bg-slate-950/80 px-3 py-2 text-center text-sm font-semibold text-white">{instruction}</p> : null}
              {localError ? <p className="absolute inset-x-3 top-3 rounded bg-red-700/90 px-3 py-2 text-center text-sm font-medium text-white" role="alert">{localError}</p> : null}
            </div>
            <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-sm text-slate-600 dark:text-slate-300">{t('face.requirements')}</p>
              <Button className="w-full sm:w-auto" disabled={!cameraActive || !cameraReady || !faceReady || checkin.isPending || capturingRef.current} loading={checkin.isPending || capturingRef.current} loadingText={t('face.verifying')} type="button" onClick={() => void captureAndCheckIn()}>
                <ScanFace aria-hidden="true" className="h-4 w-4" />
                {t('face.verifyAndCheckin')}
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </>
  );
}
