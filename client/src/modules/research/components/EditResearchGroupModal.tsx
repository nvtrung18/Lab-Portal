import { useEffect, useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { VALIDATION_MESSAGES } from '../../../shared/utils';
import type {
  GroupStatus,
  ResearchEligibleStudent,
  ResearchGroup,
  UpdateResearchGroupPayload,
} from '../types';
import { GroupMemberSelector } from './GroupMemberSelector';

interface EditResearchGroupModalProps {
  group: ResearchGroup | null;
  students: ResearchEligibleStudent[];
  isLoadingStudents?: boolean;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (payload: UpdateResearchGroupPayload) => void;
}

const emptyForm = {
  name: '',
  objective: '',
  plan: '',
  leaderStudentId: null as number | null,
  memberIds: [] as number[],
  status: 'ACTIVE' as GroupStatus,
};

export function EditResearchGroupModal({
  group,
  students,
  isLoadingStudents,
  isSubmitting,
  onClose,
  onSubmit,
}: EditResearchGroupModalProps) {
  const { t } = useI18n();
  const [form, setForm] = useState(emptyForm);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!group) {
      setForm(emptyForm);
      setTouched(false);
      return;
    }
    setForm({
      name: group.name,
      objective: group.objective ?? '',
      plan: group.plan ?? '',
      leaderStudentId: group.leaderId ?? null,
      memberIds: group.members?.map((member) => member.userId) ?? [],
      status: group.status ?? 'ACTIVE',
    });
    setTouched(false);
  }, [group]);

  if (!group) {
    return null;
  }

  const trimmedName = form.name.trim();
  const hasMembers = form.memberIds.length > 0;
  const leaderIsMember = Boolean(form.leaderStudentId && form.memberIds.includes(form.leaderStudentId));
  const canSubmit = trimmedName.length >= 3 && hasMembers && leaderIsMember;

  return (
    <form
      onSubmit={(event) => {
          event.preventDefault();
          setTouched(true);
          if (!canSubmit || !form.leaderStudentId) {
            return;
          }
          onSubmit({
            name: trimmedName,
            objective: form.objective.trim() || undefined,
            plan: form.plan.trim() || undefined,
            leaderStudentId: form.leaderStudentId,
            memberIds: form.memberIds,
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
        title={t('research.group.editTitle')}
      >
        <div className="space-y-4">
          <label className="block text-sm font-medium text-slate-700">
            {t('research.group.nameLabel')}
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.name}
              onBlur={() => setTouched(true)}
              onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
            />
            {touched && trimmedName.length < 3 ? (
              <span className="mt-1 block text-xs text-red-600">{VALIDATION_MESSAGES.groupNameRequired}</span>
            ) : null}
          </label>

          <label className="block text-sm font-medium text-slate-700">
            {t('research.group.objectiveLabel')}
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.objective}
              onChange={(event) => setForm((current) => ({ ...current, objective: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            {t('research.group.planLabel')}
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.plan}
              onChange={(event) => setForm((current) => ({ ...current, plan: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            {t('research.group.status')}
            <select
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.status}
              onChange={(event) => setForm((current) => ({ ...current, status: event.target.value as GroupStatus }))}
            >
              <option value="ACTIVE">{t('research.group.active')}</option>
              <option value="PAUSED">{t('research.group.paused')}</option>
              <option value="COMPLETED">{t('research.group.completed')}</option>
              <option value="ARCHIVED">{t('research.group.archived')}</option>
            </select>
          </label>

          <GroupMemberSelector
            students={students}
            leaderStudentId={form.leaderStudentId}
            memberIds={form.memberIds}
            isLoading={isLoadingStudents}
            onLeaderChange={(leaderStudentId) => setForm((current) => ({ ...current, leaderStudentId }))}
            onMembersChange={(memberIds) => setForm((current) => ({ ...current, memberIds }))}
          />
          {touched && !hasMembers ? (
            <p className="text-xs text-red-600">{VALIDATION_MESSAGES.memberRequired}</p>
          ) : null}
          {touched && !leaderIsMember ? (
            <p className="text-xs text-red-600">{VALIDATION_MESSAGES.leaderMustBeMember}</p>
          ) : null}
        </div>

      </Modal>
    </form>
  );
}
