import { useEffect, useState } from 'react';

import { TOAST_EVENT, type ToastPayload } from './toast';

interface ToastState extends ToastPayload {
  id: number;
}

const VARIANT_CLASSES = {
  success: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  error: 'border-red-200 bg-red-50 text-red-700',
  warning: 'border-amber-200 bg-amber-50 text-amber-700',
  info: 'border-blue-200 bg-blue-50 text-blue-700',
};

export function ToastContainer() {
  const [toast, setToast] = useState<ToastState | null>(null);

  useEffect(() => {
    const handleToast = (event: Event) => {
      const customEvent = event as CustomEvent<ToastPayload>;
      setToast({ ...customEvent.detail, id: Date.now() });
    };

    window.addEventListener(TOAST_EVENT, handleToast);
    return () => window.removeEventListener(TOAST_EVENT, handleToast);
  }, []);

  useEffect(() => {
    if (!toast) {
      return undefined;
    }

    const timeoutId = window.setTimeout(() => setToast(null), 3000);
    return () => window.clearTimeout(timeoutId);
  }, [toast]);

  if (!toast) {
    return null;
  }

  return (
    <div className="fixed inset-x-3 top-3 z-[60] sm:inset-x-auto sm:right-4 sm:top-4 sm:max-w-sm">
      <div
        className={[
          'break-words rounded-md border px-4 py-3 text-sm shadow-lg',
          VARIANT_CLASSES[toast.variant],
        ].join(' ')}
        role="status"
      >
        {toast.message}
      </div>
    </div>
  );
}
