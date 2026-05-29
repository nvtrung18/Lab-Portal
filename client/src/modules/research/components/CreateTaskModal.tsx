import { useEffect, useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import { VALIDATION_MESSAGES } from '../../../shared/utils';
import type { CreateTaskPayload, ResearchGroupMember } from '../types';

interface CreateTaskModalProps {
  isOpen: boolean;
  groupMembers: ResearchGroupMember[];
  isLoadingMembers?: boolean;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (payload: CreateTaskPayload) => void;
}

const initialForm = {
  title: '',
  description: '',
  assignedToStudentId: '',
  deadline: '',
};

export function CreateTaskModal({
  isOpen,
  groupMembers,
  isLoadingMembers,
  isSubmitting,
  onClose,
  onSubmit,
}: CreateTaskModalProps) {
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
  const titleError = touched && trimmedTitle.length < 3 ? VALIDATION_MESSAGES.required : null;
  const assigneeError = touched && !form.assignedToStudentId ? 'Vui lòng chọn người thực hiện.' : null;
  const canSubmit = trimmedTitle.length >= 3 && form.assignedToStudentId;

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
          assignedToStudentId: Number(form.assignedToStudentId),
          deadline: form.deadline || undefined,
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
              disabled={isLoadingMembers}
              loading={isSubmitting}
              loadingText="Đang tạo..."
              type="submit"
            >
              Tạo nhiệm vụ
            </Button>
          </>
        )}
        onClose={onClose}
        size="lg"
        title="Tạo nhiệm vụ mới"
      >
        <div className="space-y-4">
          <label className="block text-sm font-medium text-slate-700">
            Tên nhiệm vụ
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.title}
              onBlur={() => setTouched(true)}
              onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
              placeholder="Ví dụ: Đọc và tổng hợp tài liệu FaceNet"
            />
            {titleError ? <span className="mt-1 block text-xs text-red-600">{titleError}</span> : null}
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Mô tả nhiệm vụ
            <textarea
              className="mt-1 min-h-24 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.description}
              onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
              placeholder="Chi tiết công việc cần thực hiện..."
            />
          </label>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm font-medium text-slate-700">
              Người thực hiện
              <select
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                disabled={isLoadingMembers}
                value={form.assignedToStudentId}
                onBlur={() => setTouched(true)}
                onChange={(event) => setForm((current) => ({ ...current, assignedToStudentId: event.target.value }))}
              >
                <option value="">Chọn thành viên nhóm</option>
                {groupMembers.map((member) => (
                  <option key={member.userId} value={member.userId}>
                    {member.fullName ?? member.email} ({member.role === 'LEADER' ? 'Trưởng nhóm' : 'Thành viên'})
                  </option>
                ))}
              </select>
              {assigneeError ? <span className="mt-1 block text-xs text-red-600">{assigneeError}</span> : null}
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
        </div>
      </Modal>
    </form>
  );
}
