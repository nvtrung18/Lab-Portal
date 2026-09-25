import { useEffect, useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import type { CreateTopicPayload } from '../types';

interface CreateTopicModalProps {
  isOpen: boolean;
  labId: number | null;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (payload: CreateTopicPayload) => void;
}

const initialForm = {
  name: '',
  description: '',
  requirements: '',
  references: '',
  status: 'RECRUITING' as const,
};

export function CreateTopicModal({
  isOpen,
  labId,
  isSubmitting,
  onClose,
  onSubmit,
}: CreateTopicModalProps) {
  const { t } = useI18n();
  const [form, setForm] = useState(initialForm);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!isOpen) {
      setForm(initialForm);
      setTouched(false);
    }
  }, [isOpen]);

  if (!isOpen) {
    return null;
  }

  const trimmedName = form.name.trim();
  const nameError = touched && trimmedName.length < 3 ? t('research.topic.nameError') : null;
  const canSubmit = Boolean(labId) && trimmedName.length >= 3;

  return (
    <form
      onSubmit={(event) => {
          event.preventDefault();
          setTouched(true);
          if (!canSubmit || !labId) {
            return;
          }
          onSubmit({
            labId,
            name: trimmedName,
            description: form.description.trim() || undefined,
            requirements: form.requirements.trim() || undefined,
            references: form.references.trim() || undefined,
            status: form.status,
          });
      }}
    >
      <Modal
        footer={(
          <>
            <Button onClick={onClose} variant="outline">
              {t('common.cancel')}
            </Button>
            <Button loading={isSubmitting} loadingText={t('common.creating')} type="submit">
              {t('research.topic.createTitle')}
            </Button>
          </>
        )}
        onClose={onClose}
        size="lg"
        title={t('research.topic.createTitle')}
      >
        <div className="space-y-4">
          <label className="block text-sm font-medium text-slate-700">
            {t('research.topic.name')}
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.name}
              onBlur={() => setTouched(true)}
              onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
            />
            {nameError ? <span className="mt-1 block text-xs text-red-600">{nameError}</span> : null}
          </label>

          <label className="block text-sm font-medium text-slate-700">
            {t('research.topic.description')}
            <textarea
              className="mt-1 min-h-24 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.description}
              onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            {t('research.topic.inputRequirements')}
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.requirements}
              onChange={(event) => setForm((current) => ({ ...current, requirements: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            {t('research.topic.references')}
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.references}
              onChange={(event) => setForm((current) => ({ ...current, references: event.target.value }))}
            />
          </label>
        </div>

      </Modal>
    </form>
  );
}
