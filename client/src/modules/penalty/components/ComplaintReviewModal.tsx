import { useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import type { Complaint } from '../types';

interface ComplaintReviewModalProps {
  complaint: Complaint | null;
  decision: 'APPROVE' | 'REJECT' | null;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (note: string) => void;
}

export function ComplaintReviewModal({
  complaint,
  decision,
  isSubmitting,
  onClose,
  onSubmit,
}: ComplaintReviewModalProps) {
  const [note, setNote] = useState('');

  if (!complaint || !decision) {
    return null;
  }

  return (
    <Modal
      footer={(
        <>
          <Button disabled={isSubmitting} onClick={onClose} variant="outline">
            Hủy
          </Button>
          <Button
            loading={isSubmitting}
            loadingText="Đang xử lý..."
            onClick={() => onSubmit(note.trim())}
            variant={decision === 'REJECT' ? 'danger' : 'primary'}
          >
            Xác nhận
          </Button>
        </>
      )}
      onClose={onClose}
      size="md"
      title={decision === 'APPROVE' ? 'Chấp nhận khiếu nại' : 'Từ chối khiếu nại'}
    >
        <p className="mt-2 text-sm text-slate-600">
          Sinh viên: {complaint.studentName || complaint.studentEmail || `#${complaint.userId}`}
        </p>
        <label className="mt-5 block text-sm font-semibold text-slate-800" htmlFor="resolution-note">
          Ghi chú xử lý
        </label>
        <textarea
          id="resolution-note"
          className="mt-2 min-h-28 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
          maxLength={1000}
          value={note}
          onChange={(event) => setNote(event.target.value)}
        />
    </Modal>
  );
}
