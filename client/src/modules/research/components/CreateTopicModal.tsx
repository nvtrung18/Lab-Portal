import { useEffect, useState } from 'react';

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
  const nameError = touched && trimmedName.length < 3 ? 'Tên chủ đề cần tối thiểu 3 ký tự.' : null;
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
            name: trimmedName,
            description: form.description.trim() || undefined,
            requirements: form.requirements.trim() || undefined,
            references: form.references.trim() || undefined,
            status: form.status,
          });
        }}
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-slate-950">Tạo chủ đề nghiên cứu</h3>
            <p className="mt-1 text-sm text-slate-600">Khai báo hướng nghiên cứu chính trong PTN.</p>
          </div>
          <button className="text-sm font-semibold text-slate-500 hover:text-slate-900" type="button" onClick={onClose}>
            Đóng
          </button>
        </div>

        <div className="mt-5 space-y-4">
          <label className="block text-sm font-medium text-slate-700">
            Tên chủ đề
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.name}
              onBlur={() => setTouched(true)}
              onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
            />
            {nameError ? <span className="mt-1 block text-xs text-red-600">{nameError}</span> : null}
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Mô tả
            <textarea
              className="mt-1 min-h-24 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.description}
              onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Yêu cầu đầu vào
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.requirements}
              onChange={(event) => setForm((current) => ({ ...current, requirements: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Tài liệu tham khảo
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.references}
              onChange={(event) => setForm((current) => ({ ...current, references: event.target.value }))}
            />
          </label>
        </div>

        <div className="mt-6 flex justify-end gap-2">
          <button className="rounded-md border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700" type="button" onClick={onClose}>
            Hủy
          </button>
          <button className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" disabled={isSubmitting} type="submit">
            {isSubmitting ? 'Đang tạo...' : 'Tạo chủ đề nghiên cứu'}
          </button>
        </div>
      </form>
    </div>
  );
}
