import { useEffect, useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { VALIDATION_MESSAGES } from '../../../shared/utils';
import type { MilestoneStatus, ResearchEligibleStudent, ResearchMilestone, UpdateMilestonePayload } from '../types';

interface EditMilestoneModalProps {
  milestone: ResearchMilestone | null;
  students: ResearchEligibleStudent[];
  isLoadingStudents?: boolean;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (payload: UpdateMilestonePayload) => void;
}

const initialForm = {
  title: '',
  description: '',
  assignedToStudentId: '',
  deadline: '',
  status: 'NOT_STARTED' as MilestoneStatus,
  progressPercent: 0,
  evidenceUrl: '',
  managerComment: '',
};

export function EditMilestoneModal({
  milestone,
  students,
  isLoadingStudents,
  isSubmitting,
  onClose,
  onSubmit,
}: EditMilestoneModalProps) {
  const { t } = useI18n();
  const [form, setForm] = useState(initialForm);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!milestone) {
      setForm(initialForm);
      setTouched(false);
      return;
    }
    setForm({
      title: milestone.title,
      description: milestone.description ?? '',
      assignedToStudentId: milestone.assignedToStudentId ? String(milestone.assignedToStudentId) : '',
      deadline: milestone.deadline ?? '',
      status: milestone.status ?? 'NOT_STARTED',
      progressPercent: milestone.progressPercent ?? 0,
      evidenceUrl: milestone.evidenceUrl ?? '',
      managerComment: milestone.managerComment ?? '',
    });
    setTouched(false);
  }, [milestone]);

  if (!milestone) {
    return null;
  }

  const trimmedTitle = form.title.trim();
  const progressIsValid = Number.isFinite(form.progressPercent) && form.progressPercent >= 0 && form.progressPercent <= 100;
  const completedProgressIsValid = form.status !== 'COMPLETED' || form.progressPercent === 100;
  const canSubmit = trimmedTitle.length >= 3 && progressIsValid && completedProgressIsValid;

  return (
    <form
      onSubmit={(event) => {
          event.preventDefault();
          setTouched(true);
          if (!canSubmit) {
            return;
          }
          onSubmit({
            title: trimmedTitle,
            description: form.description.trim() || undefined,
            assignedToStudentId: form.assignedToStudentId ? Number(form.assignedToStudentId) : undefined,
            deadline: form.deadline || undefined,
            status: form.status,
            progressPercent: form.progressPercent,
            evidenceUrl: form.evidenceUrl.trim() || undefined,
            managerComment: form.managerComment.trim() || undefined,
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
              disabled={isLoadingStudents}
              loading={isSubmitting}
              loadingText={t('common.saving')}
              type="submit"
            >
              {t('common.save')}
            </Button>
          </>
        )}
        onClose={onClose}
        size="lg"
        title={t('research.milestone.editTitle')}
      >
        <div className="space-y-4">
          <label className="block text-sm font-medium text-slate-700">
            {t('research.milestone.name')}
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.title}
              onBlur={() => setTouched(true)}
              onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
            />
            {touched && trimmedTitle.length < 3 ? (
              <span className="mt-1 block text-xs text-red-600">{VALIDATION_MESSAGES.required}</span>
            ) : null}
          </label>

          <label className="block text-sm font-medium text-slate-700">
            {t('research.milestone.description')}
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.description}
              onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
            />
          </label>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm font-medium text-slate-700">
              {t('research.milestone.assignee')}
              <select
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                disabled={isLoadingStudents}
                value={form.assignedToStudentId}
                onChange={(event) => setForm((current) => ({ ...current, assignedToStudentId: event.target.value }))}
              >
                <option value="">{t('common.notAssigned')}</option>
                {students.map((student) => (
                  <option key={student.userId} value={student.userId}>
                    {student.fullName ?? student.email}
                  </option>
                ))}
              </select>
            </label>

            <label className="block text-sm font-medium text-slate-700">
              {t('research.milestone.dueDate')}
              <input
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                type="date"
                value={form.deadline}
                onChange={(event) => setForm((current) => ({ ...current, deadline: event.target.value }))}
              />
            </label>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm font-medium text-slate-700">
              {t('research.milestone.status')}
              <select
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                value={form.status}
                onChange={(event) => {
                  const status = event.target.value as MilestoneStatus;
                  setForm((current) => ({
                    ...current,
                    status,
                    progressPercent: status === 'COMPLETED' ? 100 : current.progressPercent,
                  }));
                }}
              >
                <option value="NOT_STARTED">{t('research.milestone.notStarted')}</option>
                <option value="IN_PROGRESS">{t('research.milestone.inProgress')}</option>
                <option value="WAITING_REVIEW">{t('research.milestone.waitingReview')}</option>
                <option value="COMPLETED">{t('research.milestone.completed')}</option>
                <option value="OVERDUE">{t('research.milestone.overdue')}</option>
                <option value="CANCELLED">{t('research.milestone.cancelled')}</option>
              </select>
            </label>

            <label className="block text-sm font-medium text-slate-700">
              {t('research.milestone.progressLabel')}
              <input
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                type="number"
                min={0}
                max={100}
                value={form.progressPercent}
                onChange={(event) => setForm((current) => ({ ...current, progressPercent: Number(event.target.value) }))}
              />
            </label>
          </div>
          {touched && !progressIsValid ? (
            <p className="text-xs text-red-600">{t('research.milestone.progressError')}</p>
          ) : null}
          {touched && !completedProgressIsValid ? (
            <p className="text-xs text-red-600">{t('research.milestone.completedError')}</p>
          ) : null}

          <label className="block text-sm font-medium text-slate-700">
            {t('research.milestone.evidence')}
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              type="url"
              value={form.evidenceUrl}
              onChange={(event) => setForm((current) => ({ ...current, evidenceUrl: event.target.value }))}
              placeholder="https://..."
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            {t('research.milestone.managerNote')}
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.managerComment}
              onChange={(event) => setForm((current) => ({ ...current, managerComment: event.target.value }))}
            />
          </label>
        </div>

      </Modal>
    </form>
  );
}
