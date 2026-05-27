import { useState } from 'react';

import { Button, EmptyState, ErrorState, LoadingState, toast } from '../../../shared/components';
import { downloadReportFile } from '../api';
import { useReportsByTask } from '../hooks';
import type { ResearchReport } from '../types';
import { formatDate } from '../utils';
import { ReportStatusBadge } from './ReportStatusBadge';

interface ReportListProps {
  taskId: number;
}

export function ReportList({ taskId }: ReportListProps) {
  const { data: reports = [], isError, isLoading, refetch } = useReportsByTask(taskId);
  const orderedReports = sortReportsNewestFirst(reports);

  return (
    <section className="mt-4 border-t border-slate-100 pt-4">
      <h5 className="text-sm font-semibold text-slate-800">Lịch sử nộp báo cáo</h5>
      {isLoading ? (
        <LoadingState className="mt-3">Đang tải danh sách báo cáo...</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-3" onRetry={() => refetch()}>
          Không thể tải danh sách báo cáo.
        </ErrorState>
      ) : !orderedReports.length ? (
        <EmptyState className="mt-3">Chưa có báo cáo nào được nộp.</EmptyState>
      ) : (
        <div className="mt-3 space-y-3">
          {orderedReports.map((report, index) => (
            <ReportReadOnlyItem isLatest={index === 0} key={report.id} report={report} />
          ))}
        </div>
      )}
    </section>
  );
}

export function sortReportsNewestFirst(reports: ResearchReport[]) {
  return [...reports].sort((first, second) => (
    second.version - first.version
    || (second.createdAt ?? '').localeCompare(first.createdAt ?? '')
  ));
}

export function ReportReadOnlyItem({
  isLatest = false,
  report,
}: {
  isLatest?: boolean;
  report: ResearchReport;
}) {
  const [isDownloading, setIsDownloading] = useState(false);

  async function handleDownload() {
    setIsDownloading(true);
    try {
      const file = await downloadReportFile(report.id);
      const fileUrl = URL.createObjectURL(file);
      const link = document.createElement('a');
      link.href = fileUrl;
      link.download = report.fileName || `bao-cao-v${report.version}`;
      link.click();
      URL.revokeObjectURL(fileUrl);
    } catch {
      toast.error('Không thể tải tài liệu báo cáo. Vui lòng thử lại.');
    } finally {
      setIsDownloading(false);
    }
  }

  return (
    <article className="rounded-md border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm font-semibold text-slate-950">v{report.version}</span>
            {isLatest ? (
              <span className="rounded-full bg-slate-900 px-2 py-0.5 text-xs font-semibold text-white">
                Bản mới nhất
              </span>
            ) : null}
          </div>
          <h6 className="mt-1 font-semibold text-slate-900">{report.title}</h6>
        </div>
        <ReportStatusBadge status={report.status} />
      </div>

      <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
        <ReportField label="Người nộp" value={getSubmitterLabel(report)} />
        <ReportField label="Ngày nộp" value={formatDate(report.createdAt)} />
        {report.milestoneTitle ? <ReportField label="Mốc nghiên cứu" value={report.milestoneTitle} /> : null}
        {report.taskTitle ? <ReportField label="Nhiệm vụ" value={report.taskTitle} /> : null}
        <ReportField label="Tên file" value={report.fileName || 'Chưa cập nhật'} />
        <ReportField label="Dung lượng" value={formatFileSize(report.fileSize)} />
      </dl>

      <div className="mt-4 flex flex-wrap gap-3 text-sm">
        <Button
          loading={isDownloading}
          loadingText="Đang tải..."
          size="sm"
          variant="outline"
          onClick={handleDownload}
        >
          Tải tài liệu
        </Button>
        {report.evidenceLink ? (
          <a
            className="font-semibold text-blue-700 underline"
            href={report.evidenceLink}
            rel="noreferrer"
            target="_blank"
          >
            Link minh chứng
          </a>
        ) : null}
      </div>
    </article>
  );
}

function ReportField({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="font-semibold text-slate-700">{label}</dt>
      <dd className="mt-1 break-words text-slate-600">{value}</dd>
    </div>
  );
}

function getSubmitterLabel(report: ResearchReport) {
  if (report.submittedByName && report.submittedByEmail) {
    return `${report.submittedByName} (${report.submittedByEmail})`;
  }
  return report.submittedByName ?? report.submittedByEmail ?? `#${report.submittedById}`;
}

function formatFileSize(size?: number | null) {
  if (size == null) {
    return 'Chưa cập nhật';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(2)} MB`;
}
