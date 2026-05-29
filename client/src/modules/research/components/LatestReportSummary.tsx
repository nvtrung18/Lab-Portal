import { useMemo } from 'react';
import type { ResearchReport, ResearchReportStatus } from '../types';

const REPORT_STATUS_LABELS: Record<ResearchReportStatus, string> = {
  SUBMITTED: 'Đã nộp, chờ kiểm tra',
  LEADER_REVIEWED: 'Trưởng nhóm đã kiểm tra',
  NEEDS_REVISION: 'Cần chỉnh sửa',
  APPROVED: 'Báo cáo đã được duyệt',
  REJECTED: 'Báo cáo bị từ chối',
};

interface LatestReportSummaryProps {
  reports: ResearchReport[];
}

export function LatestReportSummary({ reports }: LatestReportSummaryProps) {
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
      <p className="font-semibold text-slate-700">Báo cáo mới nhất</p>
      <div className="mt-1 flex items-center justify-between gap-2">
        <p className={`font-medium ${reportStatus === 'APPROVED' ? 'text-emerald-700 font-bold' : 'text-slate-600'}`}>
          {reportStatus ? REPORT_STATUS_LABELS[reportStatus] : 'Chưa nộp báo cáo'}
        </p>
        {latestVersion ? (
          <span className="rounded bg-slate-200 px-1.5 py-0.5 text-[10px] font-bold text-slate-800 shrink-0">
            v{latestVersion}
          </span>
        ) : null}
      </div>
    </div>
  );
}
