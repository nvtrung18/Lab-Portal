import { useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import type { ManagerReportDecision } from '../types';

interface ManagerReviewModalProps {
  decision: ManagerReportDecision | null;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (decision: ManagerReportDecision, comment: string) => void;
}

export function ManagerReviewModal({ decision, isSubmitting, onClose, onSubmit }: ManagerReviewModalProps) {
  const { t } = useI18n();
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
            {t('common.close')}
          </Button>
          <Button
            disabled={isSubmitDisabled}
            loading={isSubmitting}
            loadingText={t('common.processing')}
            onClick={handleSubmit}
            size="sm"
            variant={decision === 'REJECT' ? 'danger' : 'primary'}
          >
            {getDecisionLabel(decision, t)}
          </Button>
        </>
      ) : null}
      isOpen={isOpen}
      onClose={onClose}
      size="md"
      title={t('research.report.approveTitle')}
    >
      {decision ? (
        <>
          <label className="block text-sm font-semibold text-slate-700" htmlFor="manager-review-comment">
            {t('research.report.managerComment')}
          </label>
          <textarea
            className="mt-2 min-h-32 w-full resize-y rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
            id="manager-review-comment"
            maxLength={5000}
            placeholder={getDecisionMessage(decision, t)}
            value={comment}
            onChange={(event) => setComment(event.target.value)}
          />
          <p className="mt-2 text-xs text-slate-500">{trimmedComment.length}/5000</p>
        </>
      ) : null}
    </Modal>
  );
}

function getDecisionLabel(decision: ManagerReportDecision, t: ReturnType<typeof useI18n>['t']) {
  return t(`research.report.${decision === 'APPROVE' ? 'accept' : decision === 'REQUEST_REVISION' ? 'requestRevision' : 'reject'}`);
}

function getDecisionMessage(decision: ManagerReportDecision, t: ReturnType<typeof useI18n>['t']) {
  return t(`research.report.${decision === 'APPROVE' ? 'acceptMessage' : decision === 'REQUEST_REVISION' ? 'requestRevisionMessage' : 'rejectMessage'}`);
}
