import type { ResearchReportStatus } from '../types';

interface ReportStatusBadgeProps {
  status?: ResearchReportStatus | null;
}

const STATUS_LABELS: Record<ResearchReportStatus, string> = {
  SUBMITTED: 'Đã nộp, chờ kiểm tra',
  LEADER_REVIEWED: 'Trưởng nhóm đã kiểm tra',
  NEEDS_REVISION: 'Cần chỉnh sửa',
  APPROVED: 'Báo cáo đã được duyệt',
  REJECTED: 'Không đạt',
};

const STATUS_CLASSES: Record<ResearchReportStatus, string> = {
  SUBMITTED: 'bg-blue-50 text-blue-700 ring-blue-200',
  LEADER_REVIEWED: 'bg-amber-50 text-amber-700 ring-amber-200',
  NEEDS_REVISION: 'bg-orange-50 text-orange-700 ring-orange-200',
  APPROVED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  REJECTED: 'bg-red-50 text-red-700 ring-red-200',
};

export function ReportStatusBadge({ status }: ReportStatusBadgeProps) {
  if (!status) {
    return (
      <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600 ring-1 ring-slate-200">
        Chưa nộp báo cáo
      </span>
    );
  }

  return (
    <span className={`rounded-full px-3 py-1 text-xs font-semibold ring-1 ${STATUS_CLASSES[status]}`}>
      {STATUS_LABELS[status]}
    </span>
  );
}
