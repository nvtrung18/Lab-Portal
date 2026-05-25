import { useEffect, useState } from 'react';

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
  const titleError = touched && trimmedTitle.length < 3 ? 'Tên đề tài cần tối thiểu 3 ký tự.' : null;
  const dateError =
    touched && form.startDate && form.expectedEndDate && form.expectedEndDate <= form.startDate
      ? 'Ngày kết thúc dự kiến phải sau ngày bắt đầu.'
      : null;
  const canSubmit = trimmedTitle.length >= 3 && !dateError;
  const isEditing = Boolean(project);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 px-4 py-6">
      <form
        className="max-h-[90vh] w-full max-w-3xl overflow-y-auto rounded-lg bg-white p-6 shadow-xl"
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
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-slate-950">
              {isEditing ? 'Sửa đề tài nghiên cứu' : 'Tạo đề tài nghiên cứu'}
            </h3>
            <p className="mt-1 text-sm text-slate-600">
              {isEditing ? 'Cập nhật thông tin đề tài trong PTN đang quản lý.' : 'Đề tài được tạo trong PTN đang quản lý.'}
            </p>
          </div>
          <button className="text-sm font-semibold text-slate-500 hover:text-slate-900" type="button" onClick={onClose}>
            Đóng
          </button>
        </div>

        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          <label className="block text-sm font-medium text-slate-700">
            Mã đề tài
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.code}
              onChange={(event) => setForm((current) => ({ ...current, code: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Mức độ ưu tiên
            <select
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.priority}
              onChange={(event) => setForm((current) => ({ ...current, priority: event.target.value as ResearchPriority }))}
            >
              <option value="HIGH">Cao</option>
              <option value="MEDIUM">Trung bình</option>
              <option value="LOW">Thấp</option>
            </select>
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            Tên đề tài
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.title}
              onBlur={() => setTouched(true)}
              onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
            />
            {titleError ? <span className="mt-1 block text-xs text-red-600">{titleError}</span> : null}
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            Chủ đề nghiên cứu
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.researchDirection}
              onChange={(event) => setForm((current) => ({ ...current, researchDirection: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            Mô tả đề tài
            <textarea
              className="mt-1 min-h-24 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.description}
              onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            Mục tiêu nghiên cứu
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.objective}
              onChange={(event) => setForm((current) => ({ ...current, objective: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Ngày bắt đầu
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              type="date"
              value={form.startDate}
              onBlur={() => setTouched(true)}
              onChange={(event) => setForm((current) => ({ ...current, startDate: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Ngày kết thúc dự kiến
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
            Trạng thái
            <select
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.status}
              onChange={(event) => setForm((current) => ({ ...current, status: event.target.value as ProjectStatus }))}
            >
              <option value="DRAFT">Mới tạo</option>
              <option value="ONGOING">Đang thực hiện</option>
              <option value="WAITING_REVIEW">Chờ phản biện</option>
              <option value="COMPLETED">Hoàn thành</option>
              <option value="CANCELLED">Đã hủy</option>
            </select>
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            Sản phẩm cần nộp
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.requiredProducts}
              onChange={(event) => setForm((current) => ({ ...current, requiredProducts: event.target.value }))}
            />
          </label>

          <label className="block text-sm font-medium text-slate-700 sm:col-span-2">
            Tiêu chí đánh giá
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.evaluationCriteria}
              onChange={(event) => setForm((current) => ({ ...current, evaluationCriteria: event.target.value }))}
            />
          </label>
        </div>

        <div className="mt-6 flex justify-end gap-2">
          <button className="rounded-md border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700" type="button" onClick={onClose}>
            Hủy
          </button>
          <button className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" disabled={isSubmitting} type="submit">
            {isSubmitting ? (isEditing ? 'Đang lưu...' : 'Đang tạo...') : (isEditing ? 'Lưu thay đổi' : 'Tạo đề tài nghiên cứu')}
          </button>
        </div>
      </form>
    </div>
  );
}
