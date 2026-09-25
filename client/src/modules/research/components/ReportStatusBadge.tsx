import { useI18n } from '../../../shared/i18n';
import type { ResearchReportStatus } from '../types';

interface ReportStatusBadgeProps {
  status?: ResearchReportStatus | null;
  submittedByGroupRole?: string | null;
}

const STATUS_CLASSES: Record<ResearchReportStatus, string> = {
  SUBMITTED: 'bg-blue-50 text-blue-700 ring-blue-200',
  LEADER_REVIEWED: 'bg-amber-50 text-amber-700 ring-amber-200',
  NEEDS_REVISION: 'bg-orange-50 text-orange-700 ring-orange-200',
  LEADER_REJECTED: 'bg-red-50 text-red-700 ring-red-200',
  APPROVED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  MANAGER_REJECTED: 'bg-rose-50 text-rose-700 ring-rose-200',
};

export function ReportStatusBadge({ status, submittedByGroupRole }: ReportStatusBadgeProps) {
  const { t } = useI18n();
  if (!status) {
    return (
      <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600 ring-1 ring-slate-200">
        {t('research.report.notSubmitted')}
      </span>
    );
  }

  let label = getStatusLabel(status, t);
  let cssClass = STATUS_CLASSES[status];

  if (status === 'SUBMITTED' && submittedByGroupRole === 'LEADER') {
    label = t('research.report.status.leaderReviewed');
    cssClass = 'bg-amber-50 text-amber-700 ring-amber-200';
  }

  return (
    <span className={`rounded-full px-3 py-1 text-xs font-semibold ring-1 ${cssClass}`}>
      {label}
    </span>
  );
}

function getStatusLabel(status: ResearchReportStatus, t: ReturnType<typeof useI18n>['t']) {
  const key = {
    SUBMITTED: 'submitted',
    LEADER_REVIEWED: 'leaderReviewed',
    NEEDS_REVISION: 'needsRevision',
    LEADER_REJECTED: 'leaderRejected',
    APPROVED: 'approved',
    MANAGER_REJECTED: 'managerRejected',
  }[status];
  return t(`research.report.status.${key}`);
}
