export type ToastVariant = 'success' | 'error' | 'warning' | 'info';

export interface ToastPayload {
  message: string;
  variant: ToastVariant;
  title?: string;
}

export const TOAST_EVENT = 'lab-portal-toast';
export const PENDING_TOAST_KEY = 'lab-portal-pending-toast';

export const TOAST_MESSAGES = {
  success: 'Thao tác thành công.',
  error: 'Đã xảy ra lỗi. Vui lòng thử lại.',
  permission: 'Bạn không có quyền thực hiện thao tác này.',
  network: 'Không thể kết nối tới máy chủ.',
  sessionExpired: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
} as const;

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
  warning(message: string = TOAST_MESSAGES.error) {
    notify(message, 'warning');
  },
  info(message: string = TOAST_MESSAGES.success, title?: string) {
    notify(message, 'info', title);
  },
};
