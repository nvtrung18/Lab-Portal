import { useEffect, useMemo, useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import type { UserProfileResponse } from '../../user/api/user.api';
import { useMilestonesByProject, useTasksByMilestone } from '../hooks';
import type { CreateResearchLogPayload, ResearchGroupRole, ResearchLogVisibility } from '../types';

interface CreateLogModalProps {
  isOpen: boolean;
  projectId: number;
  currentUser?: UserProfileResponse | null;
  role: string;
  groupRole?: ResearchGroupRole | null;
  groupId?: number | null;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (payload: CreateResearchLogPayload) => void;
}

const today = new Date().toISOString().slice(0, 10);

const initialForm = {
  workDate: today,
  durationMinutes: 0,
  milestoneId: '',
  taskId: '',
  content: '',
  result: '',
  problem: '',
  nextPlan: '',
  evidenceLink: '',
  visibility: 'GROUP' as ResearchLogVisibility,
};

const visibilityLabels: Record<ResearchLogVisibility, string> = {
  PRIVATE: 'Chỉ mình tôi',
  GROUP: 'Nhóm nghiên cứu',
  PROJECT: 'Toàn bộ đề tài',
};

function isValidUrl(value: string) {
  if (!value.trim()) {
    return true;
  }
  try {
    const url = new URL(value.trim());
    return url.protocol === 'http:' || url.protocol === 'https:';
  } catch {
    return false;
  }
}

function resolveVisibilityOptions(role: string, groupRole?: ResearchGroupRole | null): ResearchLogVisibility[] {
  if (role === 'LAB_MANAGER') {
    return ['PROJECT'];
  }
  if (groupRole === 'LEADER') {
    return ['GROUP'];
  }
  return ['PRIVATE', 'GROUP'];
}

export function CreateLogModal({
  isOpen,
  projectId,
  currentUser,
  role,
  groupRole,
  groupId,
  isSubmitting,
  onClose,
  onSubmit,
}: CreateLogModalProps) {
  const [form, setForm] = useState(initialForm);
  const [touched, setTouched] = useState(false);
  const { data: milestones = [], isLoading: isLoadingMilestones } = useMilestonesByProject(isOpen ? projectId : null);
  const selectedMilestoneId = form.milestoneId ? Number(form.milestoneId) : null;
  const { data: tasks = [], isLoading: isLoadingTasks } = useTasksByMilestone(isOpen ? selectedMilestoneId : null);
  const visibilityOptions = useMemo(() => resolveVisibilityOptions(role, groupRole), [role, groupRole]);
  const isManager = role === 'LAB_MANAGER';
  const isLeader = groupRole === 'LEADER';
  const isMember = role !== 'LAB_MANAGER' && groupRole !== 'LEADER';

  useEffect(() => {
    if (isOpen) {
      setForm({ ...initialForm, visibility: visibilityOptions[0] ?? 'GROUP' });
      setTouched(false);
    }
  }, [isOpen, visibilityOptions]);

  if (!isOpen) {
    return null;
  }

  const currentUserId = currentUser?.id;
  const selectedMilestone = milestones.find((milestone) => String(milestone.id) === form.milestoneId);
  const visibleTasks = isMember && currentUserId
    ? tasks.filter((task) => task.assignedToStudentId === currentUserId)
    : tasks;
  const selectedTask = visibleTasks.find((task) => String(task.id) === form.taskId);
  const ownsSelectedMilestone = Boolean(
    selectedMilestone?.assignedToStudentId && selectedMilestone.assignedToStudentId === currentUserId,
  );
  const ownsSelectedTask = Boolean(selectedTask?.assignedToStudentId && selectedTask.assignedToStudentId === currentUserId);

  const trimmedContent = form.content.trim();
  const durationIsValid = Number.isFinite(form.durationMinutes) && form.durationMinutes >= 0;
  const evidenceIsValid = isValidUrl(form.evidenceLink);
  const memberScopeIsValid = !isMember || ownsSelectedMilestone || ownsSelectedTask;
  const workDateError = touched && !form.workDate ? 'Ngày làm việc là bắt buộc.' : null;
  const contentError = touched && !trimmedContent ? 'Nội dung đã làm là bắt buộc.' : null;
  const durationError = touched && !durationIsValid ? 'Thời gian làm phải lớn hơn hoặc bằng 0.' : null;
  const evidenceError = touched && !evidenceIsValid ? 'Link minh chứng không hợp lệ.' : null;
  const memberScopeError = touched && !memberScopeIsValid
    ? 'Thành viên chỉ có thể tạo log cho mốc hoặc nhiệm vụ được phân công.'
    : null;
  const canSubmit = Boolean(form.workDate && trimmedContent && durationIsValid && evidenceIsValid && memberScopeIsValid);

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        setTouched(true);
        if (!canSubmit) {
          return;
        }
        onSubmit({
          projectId,
          groupId: isManager ? undefined : groupId,
          milestoneId: form.milestoneId ? Number(form.milestoneId) : undefined,
          taskId: form.taskId ? Number(form.taskId) : undefined,
          workDate: form.workDate,
          durationMinutes: Number(form.durationMinutes),
          content: trimmedContent,
          result: form.result.trim() || undefined,
          problem: form.problem.trim() || undefined,
          nextPlan: form.nextPlan.trim() || undefined,
          evidenceLink: form.evidenceLink.trim() || undefined,
          visibility: form.visibility,
        });
      }}
    >
      <Modal
        footer={(
          <>
            <Button onClick={onClose} type="button" variant="outline">
              Hủy
            </Button>
            <Button loading={isSubmitting} loadingText="Đang lưu..." type="submit">
              Lưu nhật ký
            </Button>
          </>
        )}
        onClose={onClose}
        size="xl"
        title="Tạo nhật ký nghiên cứu"
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm font-medium text-slate-700">
            Ngày làm việc
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              type="date"
              value={form.workDate}
              onBlur={() => setTouched(true)}
              onChange={(event) => setForm((current) => ({ ...current, workDate: event.target.value }))}
            />
            {workDateError ? <span className="mt-1 block text-xs text-red-600">{workDateError}</span> : null}
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Thời gian làm, phút
            <input
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              min={0}
              type="number"
              value={form.durationMinutes}
              onBlur={() => setTouched(true)}
              onChange={(event) => setForm((current) => ({ ...current, durationMinutes: Number(event.target.value) }))}
            />
            {durationError ? <span className="mt-1 block text-xs text-red-600">{durationError}</span> : null}
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Mốc nghiên cứu
            <select
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              disabled={isLoadingMilestones}
              value={form.milestoneId}
              onChange={(event) => setForm((current) => ({ ...current, milestoneId: event.target.value, taskId: '' }))}
            >
              <option value="">Không chọn</option>
              {milestones.map((milestone) => (
                <option key={milestone.id} value={milestone.id}>
                  {milestone.title}
                </option>
              ))}
            </select>
          </label>

          <label className="block text-sm font-medium text-slate-700">
            Nhiệm vụ liên quan
            <select
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              disabled={!selectedMilestoneId || isLoadingTasks}
              value={form.taskId}
              onChange={(event) => setForm((current) => ({ ...current, taskId: event.target.value }))}
            >
              <option value="">Không chọn</option>
              {visibleTasks.map((task) => (
                <option key={task.id} value={task.id}>
                  {task.title}
                </option>
              ))}
            </select>
            {memberScopeError ? <span className="mt-1 block text-xs text-red-600">{memberScopeError}</span> : null}
          </label>
        </div>

        <div className="mt-4 space-y-4">
          <label className="block text-sm font-medium text-slate-700">
            Nội dung đã làm
            <textarea
              className="mt-1 min-h-24 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.content}
              onBlur={() => setTouched(true)}
              onChange={(event) => setForm((current) => ({ ...current, content: event.target.value }))}
            />
            {contentError ? <span className="mt-1 block text-xs text-red-600">{contentError}</span> : null}
          </label>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm font-medium text-slate-700">
              Kết quả đạt được
              <textarea
                className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                value={form.result}
                onChange={(event) => setForm((current) => ({ ...current, result: event.target.value }))}
              />
            </label>

            <label className="block text-sm font-medium text-slate-700">
              Vấn đề gặp phải
              <textarea
                className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                value={form.problem}
                onChange={(event) => setForm((current) => ({ ...current, problem: event.target.value }))}
              />
            </label>
          </div>

          <label className="block text-sm font-medium text-slate-700">
            Hướng xử lý tiếp theo
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
              value={form.nextPlan}
              onChange={(event) => setForm((current) => ({ ...current, nextPlan: event.target.value }))}
            />
          </label>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm font-medium text-slate-700">
              File/link minh chứng
              <input
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                placeholder="https://..."
                type="url"
                value={form.evidenceLink}
                onBlur={() => setTouched(true)}
                onChange={(event) => setForm((current) => ({ ...current, evidenceLink: event.target.value }))}
              />
              {evidenceError ? <span className="mt-1 block text-xs text-red-600">{evidenceError}</span> : null}
            </label>

            <label className="block text-sm font-medium text-slate-700">
              Phạm vi hiển thị
              <select
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10"
                value={form.visibility}
                onChange={(event) => setForm((current) => ({
                  ...current,
                  visibility: event.target.value as ResearchLogVisibility,
                }))}
              >
                {visibilityOptions.map((visibility) => (
                  <option key={visibility} value={visibility}>
                    {visibilityLabels[visibility]}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>
      </Modal>
    </form>
  );
}
