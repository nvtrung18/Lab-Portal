import { useState } from 'react';

import { Button, Modal } from '../../../shared/components';

interface LeaderReviewModalProps {
  isOpen: boolean;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (note: string) => void;
}

export function LeaderReviewModal({ isOpen, isSubmitting, onClose, onSubmit }: LeaderReviewModalProps) {
  const [note, setNote] = useState('');
  const trimmedNote = note.trim();

  function handleSubmit() {
    if (!trimmedNote) {
      return;
    }
    onSubmit(trimmedNote);
  }

  return (
    <Modal
      footer={(
        <>
          <Button disabled={isSubmitting} onClick={onClose} size="sm" variant="outline">
            Đóng
          </Button>
          <Button disabled={!trimmedNote} loading={isSubmitting} loadingText="Đang kiểm tra..." onClick={handleSubmit} size="sm">
            Đánh dấu đã kiểm tra
          </Button>
        </>
      )}
      isOpen={isOpen}
      onClose={onClose}
      size="md"
      title="Ghi chú kiểm tra"
    >
      <label className="block text-sm font-semibold text-slate-700" htmlFor="leader-review-note">
        Ghi chú kiểm tra
      </label>
      <textarea
        className="mt-2 min-h-32 w-full resize-y rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
        id="leader-review-note"
        maxLength={5000}
        placeholder="Báo cáo đã đủ nội dung để chuyển quản lý kiểm tra."
        value={note}
        onChange={(event) => setNote(event.target.value)}
      />
      <p className="mt-2 text-xs text-slate-500">{trimmedNote.length}/5000</p>
    </Modal>
  );
}
