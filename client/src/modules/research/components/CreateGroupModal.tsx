import { useEffect, useState } from 'react';

import type { CreateGroupPayload, ResearchTopic } from '../types';

interface CreateGroupModalProps {
  isOpen: boolean;
  labId: number | null;
  topic: ResearchTopic | null;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (payload: CreateGroupPayload) => void;
}

const initialForm = {
  name: '',
  description: '',
  objective: '',
  plan: '',
  status: 'ACTIVE' as const,
};

export function CreateGroupModal({
  isOpen,
  labId,
  topic,
  isSubmitting,
  onClose,
  onSubmit,
}: CreateGroupModalProps) {
  const [form, setForm] = useState(initialForm);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!isOpen) {
      setForm(initialForm);
      setTouched(false);
    }
  }, [isOpen]);

  if (!isOpen || !topic) {
    return null;
  }

  const trimmedName = form.name.trim();
  const nameError = touched && trimmedName.length < 3 ? 'Tên nhóm nghiên cứu cần tối thiểu 3 ký tự.' : null;
  const canSubmit = Boolean(labId) && trimmedName.length >= 3;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 px-4">
      <form
        className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-lg bg-white p-6 shadow-xl"
        onSubmit={(event) => {
          event.preventDefault();
          setTouched(true);
          if (!canSubmit || !labId) {
            return;
          }
          onSubmit({
            labId,
            topicId: topic.id,
            name: trimmedName,
            description: form.description.trim() || undefined,
            objective: form.objective.trim() || undefined,
            plan: form.plan.trim() || undefined,
            status: form.status,
          });
        }}
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-slate-950">Tạo nhóm nghiên cứu</h3>
            <p className="mt-1 text-sm text-slate-600">Chủ đề nghiên cứu: {topic.name}</p>
          </div>
          <button className="text-sm font-semibold text-slate-500 hover:text-slate-900" type="button" onClick={onClose}>
            Đóng
          </button>
        </div>

        <div className="mt-5 space-y-4">
          <label className="block text-sm font-medium text-slate-700">
            Tên nhóm nghiên cứu
            <input className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10" value={form.name} onBlur={() => setTouched(true)} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} />
            {nameError ? <span className="mt-1 block text-xs text-red-600">{nameError}</span> : null}
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Mục tiêu nghiên cứu
            <textarea className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10" value={form.objective} onChange={(event) => setForm((current) => ({ ...current, objective: event.target.value }))} />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Kế hoạch thực hiện
            <textarea className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10" value={form.plan} onChange={(event) => setForm((current) => ({ ...current, plan: event.target.value }))} />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Mô tả
            <textarea className="mt-1 min-h-24 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10" value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} />
          </label>
        </div>

        <div className="mt-6 flex justify-end gap-2">
          <button className="rounded-md border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700" type="button" onClick={onClose}>
            Hủy
          </button>
          <button className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" disabled={isSubmitting} type="submit">
            {isSubmitting ? 'Đang tạo...' : 'Tạo nhóm nghiên cứu'}
          </button>
        </div>
      </form>
    </div>
  );
}
