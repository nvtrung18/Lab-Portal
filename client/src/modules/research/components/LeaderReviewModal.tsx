import { useState, useEffect } from 'react';

import { Button, Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import type { LeaderReportDecision } from '../types';

interface LeaderReviewModalProps {
  isOpen: boolean;
  isSubmitting: boolean;
  decision: LeaderReportDecision | null;
  onClose: () => void;
  onSubmit: (decision: LeaderReportDecision, comment: string) => void;
}

export function LeaderReviewModal({ isOpen, isSubmitting, decision, onClose, onSubmit }: LeaderReviewModalProps) {
  const { t } = useI18n();
  const [localDecision, setLocalDecision] = useState<LeaderReportDecision>('ACCEPT');
  const [note, setNote] = useState('');
  const trimmedNote = note.trim();

  useEffect(() => {
    if (isOpen) {
      setLocalDecision(decision ?? 'ACCEPT');
      setNote('');
    }
  }, [isOpen, decision]);

  const isSubmitDisabled = !trimmedNote;

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
            {t('common.close')}
          </Button>
          <Button
            disabled={isSubmitDisabled}
            loading={isSubmitting}
            loadingText={t('common.processing')}
            onClick={handleSubmit}
            size="sm"
            variant={localDecision === 'REJECT' ? 'danger' : 'primary'}
          >
            {getDecisionLabel(localDecision, t)}
          </Button>
        </>
      )}
      isOpen={isOpen}
      onClose={onClose}
      size="md"
      title={t('research.review.title')}
    >
      <div className="space-y-4">
        {/* Decision selector when opened from a generic action button */}
        {decision === null ? (
          <div>
            <label className="block text-xs font-medium uppercase tracking-wider text-slate-500">
              {t('research.review.decision')}
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
                    {getDecisionLabel(d, t)}
                  </button>
                );
              })}
            </div>
          </div>
        ) : null}

        {/* Comment Note Text Area */}
        <div>
          <label className="block text-xs font-medium uppercase tracking-wider text-slate-500" htmlFor="leader-review-note">
            {t('research.review.note')}
          </label>
          <textarea
            className="mt-2 min-h-32 w-full resize-y rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
            id="leader-review-note"
            maxLength={5000}
            required
            placeholder={getDecisionMessage(localDecision, t)}
            value={note}
            onChange={(event) => setNote(event.target.value)}
          />
          <p className="mt-2 text-right text-xs text-slate-500">{trimmedNote.length}/5000</p>
        </div>
      </div>
    </Modal>
  );
}

function getDecisionLabel(decision: LeaderReportDecision, t: ReturnType<typeof useI18n>['t']) {
  return t(`research.review.${decision === 'REQUEST_REVISION' ? 'requestRevision' : decision === 'ACCEPT' ? 'accept' : 'reject'}`);
}

function getDecisionMessage(decision: LeaderReportDecision, t: ReturnType<typeof useI18n>['t']) {
  return t(`research.review.${decision === 'REQUEST_REVISION' ? 'requestRevisionMessage' : decision === 'ACCEPT' ? 'acceptMessage' : 'rejectMessage'}`);
}
