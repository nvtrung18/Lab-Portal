import { useEffect, useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import { VALIDATION_MESSAGES } from '../../../shared/utils';
import type { CreateProjectPayload, ResearchGroup } from '../types';

interface CreateProjectModalProps {
  group: ResearchGroup | null;
  isOpen: boolean;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (payload: CreateProjectPayload) => void;
}

const initialForm = {
  code: '',
  title: '',
  description: '',
  objective: '',
  startDate: '',
  expectedEndDate: '',
  priority: 'MEDIUM' as const,
  requiredProducts: '',
  evaluationCriteria: '',
  status: 'DRAFT' as const,
};

export function CreateProjectModal({ group, isOpen, isSubmitting, onClose, onSubmit }: CreateProjectModalProps) {
  const [form, setForm] = useState(initialForm);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!isOpen) {
      setForm(initialForm);
      setTouched(false);
    }
  }, [isOpen]);

  if (!isOpen || !group) {
    return null;
  }

  const trimmedTitle = form.title.trim();
  const titleError = touched && trimmedTitle.length < 3 ? VALIDATION_MESSAGES.researchTitleRequired : null;
  const dateError =
    touched && form.startDate && form.expectedEndDate && form.expectedEndDate <= form.startDate
      ? VALIDATION_MESSAGES.dateRange
      : null;
  const canSubmit = trimmedTitle.length >= 3 && !dateError;

  return (
    <form
      onSubmit={(event) => {
          event.preventDefault();
          setTouched(true);
          if (!canSubmit) {
            return;
          }
          onSubmit({
            groupId: group.id,
            code: form.code.trim() || undefined,
            title: trimmedTitle,
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
              Hủy
            </Button>
            <Button loading={isSubmitting} loadingText="Đang tạo..." type="submit">
              Tạo đề tài nghiên cứu
            </Button>
          </>
        )}
        onClose={onClose}
        size="xl"
        subtitle={<>Nhóm thực hiện: {group.name}</>}
        title="Tạo đề tài nghiên cứu"
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm font-medium text-slate-700">
            Mã đề tài
            <input className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10" value={form.code} onChange={(event) => setForm((current) => ({ ...current, code: event.target.value }))} />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Mức độ ưu tiên
            <select className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10" value={form.priority} onChange={(event) => setForm((current) => ({ ...current, priority: event.target.value as typeof form.priority }))}>
              <option value="HIGH">Cao</option>
              <option value="MEDIUM">Trung bình</option>
              <option value="LOW">Thấp</option>
            </select>
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            Tên đề tài
            <input className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10" value={form.title} onBlur={() => setTouched(true)} onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))} />
            {titleError ? <span className="mt-1 block text-xs text-red-600">{titleError}</span> : null}
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            Mục tiêu nghiên cứu
            <textarea className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10" value={form.objective} onChange={(event) => setForm((current) => ({ ...current, objective: event.target.value }))} />
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            Mô tả đề tài
            <textarea className="mt-1 min-h-24 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10" value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Thời gian bắt đầu
            <input className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10" type="date" value={form.startDate} onBlur={() => setTouched(true)} onChange={(event) => setForm((current) => ({ ...current, startDate: event.target.value }))} />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Thời gian kết thúc dự kiến
            <input className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10" type="date" value={form.expectedEndDate} onBlur={() => setTouched(true)} onChange={(event) => setForm((current) => ({ ...current, expectedEndDate: event.target.value }))} />
            {dateError ? <span className="mt-1 block text-xs text-red-600">{dateError}</span> : null}
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            Sản phẩm cần nộp
            <textarea className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10" value={form.requiredProducts} onChange={(event) => setForm((current) => ({ ...current, requiredProducts: event.target.value }))} />
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            Tiêu chí đánh giá
            <textarea className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10" value={form.evaluationCriteria} onChange={(event) => setForm((current) => ({ ...current, evaluationCriteria: event.target.value }))} />
          </label>
        </div>

      </Modal>
    </form>
  );
}
