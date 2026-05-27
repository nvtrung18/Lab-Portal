import { useEffect, useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import type { CreateResearchGroupPayload, ResearchEligibleStudent, ResearchProject } from '../types';
import { GroupMemberSelector } from './GroupMemberSelector';

interface CreateResearchGroupModalProps {
  isOpen: boolean;
  project: ResearchProject | null;
  students: ResearchEligibleStudent[];
  isLoadingStudents?: boolean;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (payload: CreateResearchGroupPayload) => void;
}

const initialForm = {
  name: '',
  objective: '',
  plan: '',
  leaderStudentId: null as number | null,
  memberIds: [] as number[],
};

export function CreateResearchGroupModal({
  isOpen,
  project,
  students,
  isLoadingStudents,
  isSubmitting,
  onClose,
  onSubmit,
}: CreateResearchGroupModalProps) {
  const [form, setForm] = useState(initialForm);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!isOpen) {
      setForm(initialForm);
      setTouched(false);
    }
  }, [isOpen]);

  if (!isOpen || !project) {
    return null;
  }

  const trimmedName = form.name.trim();
  const canSubmit =
    trimmedName.length >= 3 &&
    Boolean(form.leaderStudentId) &&
    form.memberIds.length > 0 &&
    Boolean(form.leaderStudentId && form.memberIds.includes(form.leaderStudentId));

  return (
    <form
      onSubmit={(event) => {
          event.preventDefault();
          setTouched(true);
          if (!canSubmit || !form.leaderStudentId) {
            return;
          }
          onSubmit({
            projectId: project.id,
            name: trimmedName,
            objective: form.objective.trim() || undefined,
            plan: form.plan.trim() || undefined,
            leaderStudentId: form.leaderStudentId,
            memberIds: form.memberIds,
          });
      }}
    >
      <Modal
        footer={(
          <>
            <Button onClick={onClose} variant="outline">
              Hủy
            </Button>
            <Button
              disabled={isLoadingStudents}
              loading={isSubmitting}
              loadingText="Đang tạo..."
              type="submit"
            >
              Tạo nhóm nghiên cứu
            </Button>
          </>
        )}
        onClose={onClose}
        size="lg"
        subtitle={<>Đề tài: {project.title}</>}
        title="Tạo nhóm nghiên cứu"
      >
        <div className="space-y-4">
          <label className="block text-sm font-medium text-slate-700">
            Tên nhóm
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.name}
              onBlur={() => setTouched(true)}
              onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
            />
            {touched && trimmedName.length < 3 ? (
              <span className="mt-1 block text-xs text-red-600">Tên nhóm cần tối thiểu 3 ký tự.</span>
            ) : null}
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Mục tiêu nhóm
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.objective}
              onChange={(event) => setForm((current) => ({ ...current, objective: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Kế hoạch thực hiện
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.plan}
              onChange={(event) => setForm((current) => ({ ...current, plan: event.target.value }))}
            />
          </label>

          <GroupMemberSelector
            students={students}
            leaderStudentId={form.leaderStudentId}
            memberIds={form.memberIds}
            isLoading={isLoadingStudents}
            onLeaderChange={(leaderStudentId) => setForm((current) => ({ ...current, leaderStudentId }))}
            onMembersChange={(memberIds) => setForm((current) => ({ ...current, memberIds }))}
          />
          {touched && (!form.leaderStudentId || !form.memberIds.includes(form.leaderStudentId)) ? (
            <p className="text-xs text-red-600">Trưởng nhóm phải nằm trong danh sách thành viên.</p>
          ) : null}
        </div>

      </Modal>
    </form>
  );
}
