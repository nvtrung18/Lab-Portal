import { useEffect, useState } from 'react';

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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 px-4">
      <form
        className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-lg bg-white p-6 shadow-xl"
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
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-slate-950">Tao nhom nghien cuu</h3>
            <p className="mt-1 text-sm text-slate-600">De tai: {project.title}</p>
          </div>
          <button className="text-sm font-semibold text-slate-500 hover:text-slate-900" type="button" onClick={onClose}>
            Dong
          </button>
        </div>

        <div className="mt-5 space-y-4">
          <label className="block text-sm font-medium text-slate-700">
            Ten nhom
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.name}
              onBlur={() => setTouched(true)}
              onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
            />
            {touched && trimmedName.length < 3 ? (
              <span className="mt-1 block text-xs text-red-600">Ten nhom can toi thieu 3 ky tu.</span>
            ) : null}
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Muc tieu nhom
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.objective}
              onChange={(event) => setForm((current) => ({ ...current, objective: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Ke hoach thuc hien
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
            <p className="text-xs text-red-600">Truong nhom phai nam trong danh sach thanh vien.</p>
          ) : null}
        </div>

        <div className="mt-6 flex justify-end gap-2">
          <button className="rounded-md border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700" type="button" onClick={onClose}>
            Huy
          </button>
          <button className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" disabled={isSubmitting || isLoadingStudents} type="submit">
            {isSubmitting ? 'Dang tao...' : 'Tao nhom nghien cuu'}
          </button>
        </div>
      </form>
    </div>
  );
}
