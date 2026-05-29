import { useEffect, useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import { VALIDATION_MESSAGES } from '../../../shared/utils';
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
  const nameError = touched && trimmedName.length < 3 ? VALIDATION_MESSAGES.groupNameRequired : null;
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
            topicId: topic.id,
            name: trimmedName,
            description: form.description.trim() || undefined,
            objective: form.objective.trim() || undefined,
            plan: form.plan.trim() || undefined,
            status: form.status,
          });
      }}
    >
      <Modal
        footer={(
          <>
            <Button onClick={onClose} variant="outline">
              Hủy
            </Button>
            <Button loading={isSubmitting} loadingText="Đang tạo..." type="submit">
              Tạo nhóm nghiên cứu
            </Button>
          </>
        )}
        onClose={onClose}
        size="lg"
        subtitle={<>Chủ đề nghiên cứu: {topic.name}</>}
        title="Tạo nhóm nghiên cứu"
      >
        <div className="space-y-4">
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

      </Modal>
    </form>
  );
}
