import { useMemo } from 'react';
import { useI18n } from '../../../shared/i18n';
import type { ResearchReport, ResearchReportStatus } from '../types';

interface LatestReportSummaryProps {
  reports: ResearchReport[];
}

export function LatestReportSummary({ reports }: LatestReportSummaryProps) {
  const { t } = useI18n();
  const latestReport = useMemo(() => {
    if (reports.length === 0) {
      return null;
    }
    const sorted = [...reports].sort(
      (a, b) => b.version - a.version || (b.createdAt ?? '').localeCompare(a.createdAt ?? '')
    );
    return sorted[0] ?? null;
  }, [reports]);

  const reportStatus = latestReport?.status ?? null;
  const latestVersion = latestReport?.version ?? null;

  return (
    <div>
      <p className="font-semibold text-slate-700">{t('research.report.latest')}</p>
      <div className="mt-1 flex items-center justify-between gap-2">
        <p className={`font-medium ${reportStatus === 'APPROVED' ? 'text-emerald-700 font-medium' : 'text-slate-600'}`}>
          {reportStatus ? getReportStatusLabel(reportStatus, t) : t('research.report.notSubmitted')}
        </p>
        {latestVersion ? (
          <span className="rounded bg-slate-200 px-1.5 py-0.5 text-[10px] font-medium text-slate-800 shrink-0">
            v{latestVersion}
          </span>
        ) : null}
      </div>
    </div>
  );
}

function getReportStatusLabel(status: ResearchReportStatus, t: ReturnType<typeof useI18n>['t']) {
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
