import { useState } from 'react';

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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 px-4 py-6">
      <div className="w-full max-w-xl rounded-lg bg-white p-6 shadow-xl">
        <h3 className="text-lg font-semibold text-slate-950">
          {decision === 'APPROVE' ? 'Chấp nhận khiếu nại' : 'Từ chối khiếu nại'}
        </h3>
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
        <div className="mt-6 flex justify-end gap-2">
          <button
            className="rounded-md border border-slate-200 px-3 py-2 text-sm font-semibold text-slate-700"
            disabled={isSubmitting}
            type="button"
            onClick={onClose}
          >
            Hủy
          </button>
          <button
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
            disabled={isSubmitting}
            type="button"
            onClick={() => onSubmit(note.trim())}
          >
            {isSubmitting ? 'Đang xử lý...' : 'Xác nhận'}
          </button>
        </div>
      </div>
    </div>
  );
}
