import { useEffect, useState } from 'react';

import type { CreateMilestonePayload, MilestoneStatus, ResearchEligibleStudent } from '../types';

interface CreateMilestoneModalProps {
  isOpen: boolean;
  projectId: number;
  students: ResearchEligibleStudent[];
  isLoadingStudents?: boolean;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (payload: CreateMilestonePayload) => void;
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

export function CreateMilestoneModal({
  isOpen,
  projectId,
  students,
  isLoadingStudents,
  isSubmitting,
  onClose,
  onSubmit,
}: CreateMilestoneModalProps) {
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

  const trimmedTitle = form.title.trim();
  const titleError = touched && trimmedTitle.length < 3 ? 'Tên mốc nghiên cứu cần tối thiểu 3 ký tự.' : null;
  const progressIsValid = Number.isFinite(form.progressPercent) && form.progressPercent >= 0 && form.progressPercent <= 100;
  const completedProgressIsValid = form.status !== 'COMPLETED' || form.progressPercent === 100;
  const progressError =
    touched && !progressIsValid
      ? 'Tỷ lệ hoàn thành phải nằm trong khoảng từ 0 đến 100.'
      : touched && !completedProgressIsValid
        ? 'Mốc đã hoàn thành phải có tỷ lệ hoàn thành là 100%.'
        : null;
  const canSubmit = trimmedTitle.length >= 3 && progressIsValid && completedProgressIsValid;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 px-4 py-6">
      <form
        className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-lg bg-white p-6 shadow-xl"
        onSubmit={(event) => {
          event.preventDefault();
          setTouched(true);
          if (!canSubmit) {
            return;
          }
          onSubmit({
            projectId,
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
        <div className="flex items-start justify-between gap-4">
          <h3 className="text-lg font-semibold text-slate-950">Tạo mốc nghiên cứu</h3>
          <button className="text-sm font-semibold text-slate-500 hover:text-slate-900" type="button" onClick={onClose}>
            Đóng
          </button>
        </div>

        <div className="mt-5 space-y-4">
          <label className="block text-sm font-medium text-slate-700">
            Tên mốc nghiên cứu
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.title}
              onBlur={() => setTouched(true)}
              onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
              placeholder="Ví dụ: Hoàn thành khảo sát tài liệu"
            />
            {titleError ? <span className="mt-1 block text-xs text-red-600">{titleError}</span> : null}
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Mô tả công việc
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.description}
              onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
            />
          </label>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm font-medium text-slate-700">
              Người phụ trách
              <select
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                disabled={isLoadingStudents}
                value={form.assignedToStudentId}
                onChange={(event) => setForm((current) => ({ ...current, assignedToStudentId: event.target.value }))}
              >
                <option value="">Chưa phân công</option>
                {students.map((student) => (
                  <option key={student.userId} value={student.userId}>
                    {student.fullName ?? student.email}
                  </option>
                ))}
              </select>
            </label>

            <label className="block text-sm font-medium text-slate-700">
              Hạn hoàn thành
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
              Trạng thái
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
                <option value="NOT_STARTED">Chưa bắt đầu</option>
                <option value="IN_PROGRESS">Đang thực hiện</option>
                <option value="WAITING_REVIEW">Chờ duyệt</option>
                <option value="COMPLETED">Hoàn thành</option>
                <option value="OVERDUE">Quá hạn</option>
                <option value="CANCELLED">Đã hủy</option>
              </select>
            </label>

            <label className="block text-sm font-medium text-slate-700">
              Tỷ lệ hoàn thành (%)
              <input
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                type="number"
                min={0}
                max={100}
                value={form.progressPercent}
                onChange={(event) => setForm((current) => ({ ...current, progressPercent: Number(event.target.value) }))}
              />
              {progressError ? <span className="mt-1 block text-xs text-red-600">{progressError}</span> : null}
            </label>
          </div>

          <label className="block text-sm font-medium text-slate-700">
            File/link minh chứng
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              type="url"
              value={form.evidenceUrl}
              onChange={(event) => setForm((current) => ({ ...current, evidenceUrl: event.target.value }))}
              placeholder="https://..."
            />
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Nhận xét của quản lý PTN
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.managerComment}
              onChange={(event) => setForm((current) => ({ ...current, managerComment: event.target.value }))}
            />
          </label>
        </div>

        <div className="mt-6 flex justify-end gap-2">
          <button className="rounded-md border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700" type="button" onClick={onClose}>
            Hủy
          </button>
          <button
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
            disabled={isSubmitting || isLoadingStudents}
            type="submit"
          >
            {isSubmitting ? 'Đang tạo...' : 'Tạo mốc nghiên cứu'}
          </button>
        </div>
      </form>
    </div>
  );
}
