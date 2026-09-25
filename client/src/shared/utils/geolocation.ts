import { getStoredLocale, translate } from '../i18n';

export interface BrowserCoordinates {
  latitude: number;
  longitude: number;
  accuracyMeters: number;
}

export function getCurrentCoordinates(): Promise<BrowserCoordinates> {
  const t = (key: string) => translate(key, getStoredLocale());
  if (!window.isSecureContext) {
    return Promise.reject(new Error(t('common.gpsSecureContext')));
  }
  if (!navigator.geolocation) {
    return Promise.reject(new Error(t('common.gpsUnsupported')));
  }

  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        if (!Number.isFinite(position.coords.accuracy) || position.coords.accuracy < 0) {
          reject(new Error(t('common.gpsAccuracyInvalid')));
          return;
        }
        resolve({
          latitude: Number(position.coords.latitude.toFixed(7)),
          longitude: Number(position.coords.longitude.toFixed(7)),
          accuracyMeters: Number(position.coords.accuracy.toFixed(2)),
        });
      },
      (error) => {
        const message = error.code === 1
          ? t('common.gpsPermissionDenied')
          : error.code === 3
            ? t('common.gpsTimeout')
            : t('common.gpsUnavailable');
        reject(new Error(message));
      },
      { enableHighAccuracy: true, maximumAge: 0, timeout: 15000 },
    );
  });
}
