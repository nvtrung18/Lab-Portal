import { useEffect, useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { VALIDATION_MESSAGES } from '../../../shared/utils';
import type { CreateResearchProjectPayload, ProjectStatus, ResearchPriority, ResearchProject } from '../types';

interface CreateResearchProjectModalProps {
  labId: number | null;
  project?: ResearchProject | null;
  isOpen: boolean;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (payload: CreateResearchProjectPayload) => void;
}

const initialForm = {
  code: '',
  title: '',
  researchDirection: '',
  description: '',
  objective: '',
  startDate: '',
  expectedEndDate: '',
  priority: 'MEDIUM' as ResearchPriority,
  requiredProducts: '',
  evaluationCriteria: '',
  status: 'DRAFT' as ProjectStatus,
};

function toForm(project?: ResearchProject | null) {
  if (!project) {
    return initialForm;
  }
  return {
    code: project.code ?? '',
    title: project.title,
    researchDirection: project.researchDirection ?? '',
    description: project.description ?? '',
    objective: project.objective ?? '',
    startDate: project.startDate ?? '',
    expectedEndDate: project.expectedEndDate ?? project.endDate ?? '',
    priority: project.priority ?? ('MEDIUM' as ResearchPriority),
    requiredProducts: project.requiredProducts ?? '',
    evaluationCriteria: project.evaluationCriteria ?? '',
    status: project.status ?? ('DRAFT' as ProjectStatus),
  };
}

export function CreateResearchProjectModal({
  labId,
  project,
  isOpen,
  isSubmitting,
  onClose,
  onSubmit,
}: CreateResearchProjectModalProps) {
  const { t } = useI18n();
  const [form, setForm] = useState(initialForm);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setForm(toForm(project));
      setTouched(false);
    }
  }, [isOpen, project]);

  if (!isOpen || !labId) {
    return null;
  }

  const trimmedTitle = form.title.trim();
  const titleError = touched && trimmedTitle.length < 3 ? VALIDATION_MESSAGES.researchTitleRequired : null;
  const dateError =
    touched && form.startDate && form.expectedEndDate && form.expectedEndDate <= form.startDate
      ? VALIDATION_MESSAGES.dateRange
      : null;
  const canSubmit = trimmedTitle.length >= 3 && !dateError;
  const isEditing = Boolean(project);

  return (
    <form
      onSubmit={(event) => {
          event.preventDefault();
          setTouched(true);
          if (!canSubmit) {
            return;
          }

          onSubmit({
            labId,
            code: form.code.trim() || undefined,
            title: trimmedTitle,
            researchDirection: form.researchDirection.trim() || undefined,
            description: form.description.trim() || undefined,
            objective: form.objective.trim() || undefined,
            startDate: form.startDate || undefined,
            expectedEndDate: form.expectedEndDate || undefined,
            priority: form.priority,
            requiredProducts: form.requiredProducts.trim() || undefined,
            evaluationCriteria: form.evaluationCriteria.trim() || undefined,
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
            <Button
              loading={isSubmitting}
              loadingText={isEditing ? t('common.saving') : t('common.creating')}
              type="submit"
            >
              {isEditing ? t('common.save') : t('research.project.createTitle')}
            </Button>
          </>
        )}
        onClose={onClose}
        size="xl"
        title={isEditing ? t('research.project.editTitle') : t('research.project.createTitle')}
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm font-medium text-slate-700">
            {t('research.project.code')}
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.code}
              onChange={(event) => setForm((current) => ({ ...current, code: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            {t('research.project.priority')}
            <select
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.priority}
              onChange={(event) => setForm((current) => ({ ...current, priority: event.target.value as ResearchPriority }))}
            >
              <option value="HIGH">{t('research.priority.high')}</option>
              <option value="MEDIUM">{t('research.priority.medium')}</option>
              <option value="LOW">{t('research.priority.low')}</option>
            </select>
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            {t('research.project.title')}
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.title}
              onBlur={() => setTouched(true)}
              onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
            />
            {titleError ? <span className="mt-1 block text-xs text-red-600">{titleError}</span> : null}
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            {t('research.project.direction')}
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.researchDirection}
              onChange={(event) => setForm((current) => ({ ...current, researchDirection: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            {t('research.project.description')}
            <textarea
              className="mt-1 min-h-24 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.description}
              onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            {t('research.project.objective')}
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.objective}
              onChange={(event) => setForm((current) => ({ ...current, objective: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            {t('research.project.startDateLabel')}
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              type="date"
              value={form.startDate}
              onBlur={() => setTouched(true)}
              onChange={(event) => setForm((current) => ({ ...current, startDate: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            {t('research.project.expectedEndDateLabel')}
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              type="date"
              value={form.expectedEndDate}
              onBlur={() => setTouched(true)}
              onChange={(event) => setForm((current) => ({ ...current, expectedEndDate: event.target.value }))}
            />
            {dateError ? <span className="mt-1 block text-xs text-red-600">{dateError}</span> : null}
          </label>

          <label className="block text-sm font-medium text-slate-700">
            {t('research.project.status')}
            <select
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.status}
              onChange={(event) => setForm((current) => ({ ...current, status: event.target.value as ProjectStatus }))}
            >
              <option value="DRAFT">{t('research.project.new')}</option>
              <option value="ONGOING">{t('research.project.ongoing')}</option>
              <option value="WAITING_REVIEW">{t('research.project.waitingReview')}</option>
              <option value="COMPLETED">{t('research.project.completed')}</option>
              <option value="CANCELLED">{t('research.project.cancelled')}</option>
            </select>
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            {t('research.project.requiredProducts')}
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.requiredProducts}
              onChange={(event) => setForm((current) => ({ ...current, requiredProducts: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            {t('research.project.evaluationCriteria')}
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.evaluationCriteria}
              onChange={(event) => setForm((current) => ({ ...current, evaluationCriteria: event.target.value }))}
            />
          </label>
        </div>

      </Modal>
    </form>
  );
}
