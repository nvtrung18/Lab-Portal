export type ToastVariant = 'success' | 'error';

export interface ToastPayload {
  message: string;
  variant: ToastVariant;
}

export const TOAST_EVENT = 'lab-portal-toast';

export const toast = {
  success(message: string) {
    window.dispatchEvent(
      new CustomEvent<ToastPayload>(TOAST_EVENT, {
        detail: { message, variant: 'success' },
      }),
    );
  },
  error(message: string) {
    window.dispatchEvent(
      new CustomEvent<ToastPayload>(TOAST_EVENT, {
        detail: { message, variant: 'error' },
      }),
    );
  },
};
