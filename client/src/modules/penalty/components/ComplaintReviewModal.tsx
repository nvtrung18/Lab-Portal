import { useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
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
  const { t } = useI18n();
  const [note, setNote] = useState('');

  if (!complaint || !decision) {
    return null;
  }

  return (
    <Modal
      footer={(
        <>
          <Button disabled={isSubmitting} onClick={onClose} variant="outline">
            {t('common.cancel')}
          </Button>
          <Button
            loading={isSubmitting}
            loadingText={t('common.processing')}
            onClick={() => onSubmit(note.trim())}
            variant={decision === 'REJECT' ? 'danger' : 'primary'}
          >
            {t('common.confirm')}
          </Button>
        </>
      )}
      onClose={onClose}
      size="md"
      title={decision === 'APPROVE' ? t('penalty.complaint.accept') : t('penalty.complaint.reject')}
    >
        <p className="mt-2 text-sm text-slate-600">
          {t('penalty.complaint.student')}: {complaint.studentName || complaint.studentEmail || `#${complaint.userId}`}
        </p>
        <label className="mt-5 block text-sm font-semibold text-slate-800" htmlFor="resolution-note">
          {t('penalty.complaint.processingNote')}
        </label>
        <textarea
          id="resolution-note"
          className="mt-2 min-h-28 w-full rounded-md border border-slate-300 px-3 py-2 text-base outline-none focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
          maxLength={1000}
          value={note}
          onChange={(event) => setNote(event.target.value)}
        />
    </Modal>
  );
}
