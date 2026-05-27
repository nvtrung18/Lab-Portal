import { useState } from 'react';

import { Button, Modal } from '../../../shared/components';
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
    <Modal
      footer={(
        <>
          <Button disabled={cancelSlot.isPending} onClick={onClose} variant="outline">
            Đóng
          </Button>
          <Button
            loading={cancelSlot.isPending}
            loadingText="Đang hủy..."
            onClick={handleSubmit}
            variant="danger"
          >
            Hủy khung giờ
          </Button>
        </>
      )}
      onClose={onClose}
      size="md"
      subtitle="Nhập lý do hủy để thông báo cho sinh viên."
      title="Hủy khung giờ sử dụng"
    >
        <div className="space-y-4">
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
    </Modal>
  );
}
