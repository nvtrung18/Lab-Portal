import { useState } from 'react';

import { useCancelSlot } from '../hooks';

interface CancelSlotModalProps {
  labId: number;
  slotId: number | null;
  isOpen: boolean;
  onClose: () => void;
}

export function CancelSlotModal({ labId, slotId, isOpen, onClose }: CancelSlotModalProps) {
  const [reason, setReason] = useState('');
  const [notifyByEmail, setNotifyByEmail] = useState(true);
  const cancelSlot = useCancelSlot(labId, slotId);

  if (!isOpen || !slotId) {
    return null;
  }

  const handleSubmit = async () => {
    if (
      !window.confirm(
        'Bạn có chắc muốn hủy khung giờ sử dụng này không? Các sinh viên đã đăng ký sẽ được thông báo qua email.',
      )
    ) {
      return;
    }

    await cancelSlot.mutateAsync({
      slotId,
      reason,
      notifyByEmail,
    });
    setReason('');
    setNotifyByEmail(true);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 px-4 py-6">
      <div className="w-full max-w-lg rounded-lg bg-white shadow-xl">
        <div className="border-b border-slate-200 px-5 py-4">
          <h2 className="text-lg font-semibold text-slate-950">Hủy khung giờ sử dụng</h2>
          <p className="mt-1 text-sm text-slate-600">Nhập lý do hủy để thông báo cho sinh viên.</p>
        </div>
        <div className="space-y-4 px-5 py-5">
          <div>
            <label className="text-sm font-medium text-slate-700" htmlFor="cancel-reason">
              Lý do hủy
            </label>
            <textarea
              id="cancel-reason"
              className="mt-1 min-h-28 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none transition focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            />
          </div>
          <label className="flex items-center gap-2 text-sm font-medium text-slate-700">
            <input
              type="checkbox"
              checked={notifyByEmail}
              onChange={(event) => setNotifyByEmail(event.target.checked)}
            />
            Gửi thông báo qua email cho sinh viên đã đăng ký
          </label>
        </div>
        <div className="flex justify-end gap-3 border-t border-slate-200 px-5 py-4">
          <button
            type="button"
            className="rounded-md border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700"
            disabled={cancelSlot.isPending}
            onClick={onClose}
          >
            Đóng
          </button>
          <button
            type="button"
            className="rounded-md bg-red-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
            disabled={cancelSlot.isPending}
            onClick={handleSubmit}
          >
            {cancelSlot.isPending ? 'Đang hủy...' : 'Hủy khung giờ'}
          </button>
        </div>
      </div>
    </div>
  );
}
