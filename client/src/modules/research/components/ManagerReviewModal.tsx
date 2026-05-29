import { useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import type { ManagerReportDecision } from '../types';

interface ManagerReviewModalProps {
  decision: ManagerReportDecision | null;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (decision: ManagerReportDecision, comment: string) => void;
}

const DECISION_LABELS: Record<ManagerReportDecision, string> = {
  APPROVE: 'Chấp nhận báo cáo',
  REQUEST_REVISION: 'Yêu cầu nộp lại',
  REJECT: 'Từ chối',
};

const COMMENT_PLACEHOLDERS: Record<ManagerReportDecision, string> = {
  APPROVE: 'Báo cáo đạt yêu cầu.',
  REQUEST_REVISION: 'Cần bổ sung kết quả thử nghiệm và biểu đồ.',
  REJECT: 'Báo cáo chưa đúng nội dung được giao.',
};

export function ManagerReviewModal({ decision, isSubmitting, onClose, onSubmit }: ManagerReviewModalProps) {
  const [comment, setComment] = useState('');
  const trimmedComment = comment.trim();
  const isOpen = decision != null;

  const isCommentRequired = decision === 'REQUEST_REVISION' || decision === 'REJECT';
  const isSubmitDisabled = isCommentRequired && !trimmedComment;

  function handleSubmit() {
    if (!decision || isSubmitDisabled) {
      return;
    }
    onSubmit(decision, trimmedComment);
  }

  return (
    <Modal
      footer={decision ? (
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
            variant={decision === 'REJECT' ? 'danger' : 'primary'}
          >
            {DECISION_LABELS[decision]}
          </Button>
        </>
      ) : null}
      isOpen={isOpen}
      onClose={onClose}
      size="md"
      title="Duyệt báo cáo"
    >
      {decision ? (
        <>
          <label className="block text-sm font-semibold text-slate-700" htmlFor="manager-review-comment">
            Nhận xét của quản lý
          </label>
          <textarea
            className="mt-2 min-h-32 w-full resize-y rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
            id="manager-review-comment"
            maxLength={5000}
            placeholder={COMMENT_PLACEHOLDERS[decision]}
            value={comment}
            onChange={(event) => setComment(event.target.value)}
          />
          <p className="mt-2 text-xs text-slate-500">{trimmedComment.length}/5000</p>
        </>
      ) : null}
    </Modal>
  );
}
