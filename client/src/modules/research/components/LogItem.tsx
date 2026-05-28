import { memo } from 'react';

import type { ResearchLog } from '../types';

interface LogItemProps {
  log: ResearchLog;
}

const roleLabels: Record<string, string> = {
  LAB_MANAGER: 'Quản lý PTN',
  LEADER: 'Trưởng nhóm',
  MEMBER: 'Thành viên',
  STUDENT: 'Thành viên',
  SYSTEM: 'Hệ thống',
};

const logTypeLabels: Record<ResearchLog['logType'], string> = {
  MANUAL: 'Nhật ký thủ công',
  SYSTEM: 'Sự kiện hệ thống',
};

function formatDate(value?: string | null) {
  if (!value) {
    return 'Chưa cập nhật';
  }
  return new Intl.DateTimeFormat('vi-VN').format(new Date(value));
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return 'Chưa cập nhật';
  }
  return new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(value));
}

function formatDuration(minutes: number) {
  if (!minutes) {
    return '0 phút';
  }
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  if (!hours) {
    return `${remainingMinutes} phút`;
  }
  if (!remainingMinutes) {
    return `${hours} giờ`;
  }
  return `${hours} giờ ${remainingMinutes} phút`;
}

function resolveRoleLabel(log: ResearchLog) {
  if (log.logType === 'SYSTEM') {
    return roleLabels.SYSTEM;
  }
  if (log.groupRole && roleLabels[log.groupRole]) {
    return roleLabels[log.groupRole];
  }
  return roleLabels[log.authorRole ?? ''] ?? log.authorRole ?? 'Chưa cập nhật';
}

function Field({ label, value }: { label: string; value?: string | number | null }) {
  return (
    <div>
      <dt className="text-xs font-semibold uppercase text-slate-500">{label}</dt>
      <dd className="mt-1 break-words text-sm text-slate-800">{value || 'Chưa cập nhật'}</dd>
    </div>
  );
}

function LogItemComponent({ log }: LogItemProps) {
  return (
    <article className="relative pl-7">
      <span className="absolute left-0 top-1.5 h-3 w-3 rounded-full border-2 border-white bg-slate-900 shadow ring-2 ring-slate-300" />
      <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:p-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700">
                {logTypeLabels[log.logType]}
              </span>
              <span className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-700 ring-1 ring-blue-100">
                {formatDate(log.workDate)}
              </span>
            </div>
            <h4 className="mt-3 break-words text-base font-semibold text-slate-950">{log.content}</h4>
          </div>
          <p className="shrink-0 text-sm text-slate-500">{formatDateTime(log.createdAt)}</p>
        </div>

        <dl className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field label="Ngày làm việc" value={formatDate(log.workDate)} />
          <Field label="Người ghi" value={log.authorName} />
          <Field label="Vai trò người ghi" value={resolveRoleLabel(log)} />
          <Field label="Nhóm" value={log.groupName} />
          <Field label="Mốc nghiên cứu" value={log.milestoneTitle} />
          <Field label="Nhiệm vụ liên quan" value={log.taskTitle} />
          <Field label="Thời gian làm" value={formatDuration(log.durationMinutes)} />
        </dl>

        <dl className="mt-5 space-y-4">
          <Field label="Nội dung đã làm" value={log.content} />
          <Field label="Kết quả đạt được" value={log.result} />
          <Field label="Vấn đề gặp phải" value={log.problem} />
          <Field label="Hướng xử lý tiếp theo" value={log.nextPlan} />
          <div>
            <dt className="text-xs font-semibold uppercase text-slate-500">File/link minh chứng</dt>
            <dd className="mt-1 text-sm">
              {log.evidenceLink ? (
                <a
                  className="break-all font-medium text-blue-700 underline underline-offset-2"
                  href={log.evidenceLink}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {log.evidenceLink}
                </a>
              ) : (
                <span className="text-slate-800">Chưa cập nhật</span>
              )}
            </dd>
          </div>
          <Field label="Thời gian tạo log" value={formatDateTime(log.createdAt)} />
        </dl>
      </div>
    </article>
  );
}

export const LogItem = memo(LogItemComponent);
