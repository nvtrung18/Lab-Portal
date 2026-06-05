import { useState, useEffect } from 'react';

import { Button, Modal } from '../../../shared/components';
import type { LeaderReportDecision } from '../types';

interface LeaderReviewModalProps {
  isOpen: boolean;
  isSubmitting: boolean;
  decision: LeaderReportDecision | null;
  onClose: () => void;
  onSubmit: (decision: LeaderReportDecision, comment: string) => void;
}

const DECISION_LABELS: Record<LeaderReportDecision, string> = {
  ACCEPT: 'Chấp nhận cấp nhóm',
  REQUEST_REVISION: 'Yêu cầu chỉnh sửa',
  REJECT: 'Từ chối',
};

const NOTE_PLACEHOLDERS: Record<LeaderReportDecision, string> = {
  ACCEPT: 'Báo cáo đã đạt yêu cầu cấp nhóm để chuyển quản lý.',
  REQUEST_REVISION: 'Cần bổ sung kết quả và chỉnh sửa biểu đồ theo góp ý.',
  REJECT: 'Báo cáo chưa đúng tiến độ và nội dung được giao.',
};

export function LeaderReviewModal({ isOpen, isSubmitting, decision, onClose, onSubmit }: LeaderReviewModalProps) {
  const [localDecision, setLocalDecision] = useState<LeaderReportDecision>('ACCEPT');
  const [note, setNote] = useState('');
  const trimmedNote = note.trim();

  useEffect(() => {
    if (isOpen) {
      setLocalDecision(decision ?? 'ACCEPT');
      setNote('');
    }
  }, [isOpen, decision]);

  const isCommentRequired = localDecision === 'REQUEST_REVISION' || localDecision === 'REJECT';
  const isSubmitDisabled = isCommentRequired && !trimmedNote;

  function handleSubmit() {
    if (isSubmitDisabled) {
      return;
    }
    onSubmit(localDecision, trimmedNote);
  }

  return (
    <Modal
      footer={(
        <>
          <Button disabled={isSubmitting} onClick={onClose} size="sm" variant="outline">
            Đóng
          </Button>
          <Button
            disabled={isSubmitDisabled}
            loading={isSubmitting}
            loadingText="Đang xử lý..."
            onClick={handleSubmit}
            size="sm"
            variant={localDecision === 'REJECT' ? 'danger' : 'primary'}
          >
            {DECISION_LABELS[localDecision]}
          </Button>
        </>
      )}
      isOpen={isOpen}
      onClose={onClose}
      size="md"
      title="Xử lý báo cáo"
    >
      <div className="space-y-4">
        {/* Decision selector when opened from a generic action button */}
        {decision === null ? (
          <div>
            <label className="block text-xs font-medium uppercase tracking-wider text-slate-500">
              Quyết định kiểm tra
            </label>
            <div className="mt-2 flex flex-wrap gap-2">
              {(['ACCEPT', 'REQUEST_REVISION', 'REJECT'] as LeaderReportDecision[]).map((d) => {
                const isSelected = localDecision === d;
                return (
                  <button
                    key={d}
                    type="button"
                    onClick={() => setLocalDecision(d)}
                    className={`rounded-md px-3 py-2 text-xs font-semibold ring-1 transition duration-150 ${
                      isSelected
                        ? d === 'REJECT'
                          ? 'bg-red-50 text-red-700 ring-red-300 font-medium'
                          : d === 'REQUEST_REVISION'
                            ? 'bg-amber-50 text-amber-700 ring-amber-300 font-medium'
                            : 'bg-blue-50 text-blue-700 ring-blue-300 font-medium'
                        : 'bg-white text-slate-600 ring-slate-200 hover:bg-slate-50'
                    }`}
                  >
                    {DECISION_LABELS[d]}
                  </button>
                );
              })}
            </div>
          </div>
        ) : null}

        {/* Comment Note Text Area */}
        <div>
          <label className="block text-xs font-medium uppercase tracking-wider text-slate-500" htmlFor="leader-review-note">
            Ghi chú / Nhận xét của Trưởng nhóm
          </label>
          <textarea
            className="mt-2 min-h-32 w-full resize-y rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
            id="leader-review-note"
            maxLength={5000}
            placeholder={NOTE_PLACEHOLDERS[localDecision]}
            value={note}
            onChange={(event) => setNote(event.target.value)}
          />
          <p className="mt-2 text-right text-xs text-slate-500">{trimmedNote.length}/5000</p>
        </div>
      </div>
    </Modal>
  );
}
