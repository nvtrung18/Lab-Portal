import { getStoredLocale, translate } from '../i18n';

export async function openCamera(facingMode: 'user' | 'environment'): Promise<MediaStream> {
  const isLocalhost = ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname);
  if (!window.isSecureContext && !isLocalhost) throw new Error('CAMERA_REQUIRES_SECURE_CONTEXT');
  if (!navigator.mediaDevices?.getUserMedia) throw new Error('CAMERA_NOT_SUPPORTED');

  try {
    return await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: facingMode } },
      audio: false,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'OverconstrainedError') {
      return navigator.mediaDevices.getUserMedia({ video: true, audio: false });
    }
    throw error;
  }
}

export function cameraErrorMessage(error: unknown): string {
  const t = (key: string) => translate(key, getStoredLocale());
  if (error instanceof Error) {
    if (error.message === 'CAMERA_REQUIRES_SECURE_CONTEXT') return t('common.cameraSecureContext');
    if (error.message === 'CAMERA_NOT_SUPPORTED') return t('common.cameraUnsupported');
  }
  if (error instanceof DOMException) {
    if (error.name === 'NotAllowedError' || error.name === 'SecurityError') return t('common.cameraPermissionDenied');
    if (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError') return t('common.cameraNotFound');
    if (error.name === 'NotReadableError' || error.name === 'TrackStartError') return t('common.cameraBusy');
  }
  return t('common.cameraOpenFailed');
}
