export type ToastVariant = 'success' | 'error' | 'warning' | 'info';

export interface ToastPayload {
  message: string;
  variant: ToastVariant;
}

export const TOAST_EVENT = 'lab-portal-toast';

export const TOAST_MESSAGES = {
  success: 'Thao tác thành công.',
  error: 'Đã xảy ra lỗi. Vui lòng thử lại.',
  permission: 'Bạn không có quyền thực hiện thao tác này.',
  network: 'Không thể kết nối tới máy chủ.',
} as const;

function notify(message: string, variant: ToastVariant) {
  window.dispatchEvent(
    new CustomEvent<ToastPayload>(TOAST_EVENT, {
      detail: { message, variant },
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
  warning(message: string) {
    notify(message, 'warning');
  },
  info(message: string) {
    notify(message, 'info');
  },
};
