import { useEffect, useState } from 'react';

import { TOAST_EVENT, type ToastPayload } from './toast';

interface ToastState extends ToastPayload {
  id: number;
}

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
    <div className="fixed right-4 top-4 z-50">
      <div
        className={[
          'rounded-md border px-4 py-3 text-sm shadow-lg',
          toast.variant === 'success'
            ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
            : 'border-red-200 bg-red-50 text-red-700',
        ].join(' ')}
      >
        {toast.message}
      </div>
    </div>
  );
}
