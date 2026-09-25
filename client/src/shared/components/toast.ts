import { translate } from '../i18n';

export type ToastVariant = 'success' | 'error' | 'warning' | 'info';

export interface ToastPayload {
  message: string;
  variant: ToastVariant;
  title?: string;
}

export const TOAST_EVENT = 'lab-portal-toast';
export const PENDING_TOAST_KEY = 'lab-portal-pending-toast';

export const TOAST_MESSAGES = {
  get success() { return translate('common.success'); },
  get error() { return translate('common.error'); },
  get warning() { return translate('common.warning'); },
  get info() { return translate('common.info'); },
  get permission() { return translate('common.permissionDenied'); },
  get network() { return translate('common.networkError'); },
  get sessionExpired() { return translate('common.sessionExpired'); },
};

const TOAST_DEDUPE_MS = 2500;
const toastHistory = new Map<string, number>();

function notify(message: string, variant: ToastVariant, title?: string) {
  const key = `${variant}:${message}`;
  const now = Date.now();
  const lastShownAt = toastHistory.get(key) ?? 0;

  if (now - lastShownAt < TOAST_DEDUPE_MS) {
    return;
  }

  toastHistory.set(key, now);
  window.dispatchEvent(
    new CustomEvent<ToastPayload>(TOAST_EVENT, {
      detail: { message, variant, title },
    }),
  );
}

export const toast = {
  success(message: string = TOAST_MESSAGES.success) {
    notify(message, 'success');
  },
  error(message: string = TOAST_MESSAGES.error) {
    notify(message, 'error');
  },
  warning(message: string = TOAST_MESSAGES.warning) {
    notify(message, 'warning');
  },
  info(message: string = TOAST_MESSAGES.info, title?: string) {
    notify(message, 'info', title);
  },
};
