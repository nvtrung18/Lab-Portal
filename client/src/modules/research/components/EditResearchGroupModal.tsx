import { useEffect, useState } from 'react';

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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 px-4 py-6">
      <form
        className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-lg bg-white p-6 shadow-xl"
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
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-slate-950">Sửa thông tin nhóm nghiên cứu</h3>
            <p className="mt-1 text-sm text-slate-600">Đề tài: {group.projectTitle ?? 'Chưa cập nhật'}</p>
          </div>
          <button className="text-sm font-semibold text-slate-500 hover:text-slate-900" type="button" onClick={onClose}>
            Đóng
          </button>
        </div>

        <div className="mt-5 space-y-4">
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

          <label className="block text-sm font-medium text-slate-700">
            Trạng thái
            <select
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.status}
              onChange={(event) => setForm((current) => ({ ...current, status: event.target.value as GroupStatus }))}
            >
              <option value="ACTIVE">Đang hoạt động</option>
              <option value="PAUSED">Tạm dừng</option>
              <option value="COMPLETED">Hoàn thành</option>
              <option value="ARCHIVED">Đã lưu trữ</option>
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
            <p className="text-xs text-red-600">Nhóm phải có ít nhất một thành viên.</p>
          ) : null}
          {touched && !leaderIsMember ? (
            <p className="text-xs text-red-600">Trưởng nhóm phải nằm trong danh sách thành viên.</p>
          ) : null}
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
            {isSubmitting ? 'Đang lưu...' : 'Lưu thay đổi'}
          </button>
        </div>
      </form>
    </div>
  );
}
