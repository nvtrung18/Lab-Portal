import { useState, useEffect } from 'react';

import { Button, toast } from '../../../shared/components';
import { downloadReportFile } from '../api';
import type { ResearchReport } from '../types';
import { formatDate } from '../utils';
import type { TaskBoardRole } from '../taskBoardHelpers';
import { LeaderReviewButton } from './LeaderReviewButton';
import { ManagerReviewActions } from './ManagerReviewActions';
import { ReportStatusBadge } from './ReportStatusBadge';
import { ReviewPanel } from './ReviewPanel';

interface ReportVersionItemProps {
  report: ResearchReport;
  isLatest?: boolean;
  defaultOpenReview?: boolean;
  canComment?: boolean;
  currentUserId?: number | null;
  groupId?: number | null;
  labId?: number | null;
  role?: TaskBoardRole;
}

export function ReportVersionItem({
  report,
  isLatest = false,
  defaultOpenReview = false,
  canComment = true,
  currentUserId,
  groupId,
  labId,
  role,
}: ReportVersionItemProps) {
  const [isDownloading, setIsDownloading] = useState(false);
  const [isReviewOpen, setIsReviewOpen] = useState(defaultOpenReview);

  // Sync state if defaultOpenReview changes
  useEffect(() => {
    if (defaultOpenReview) {
      setIsReviewOpen(true);
    }
  }, [defaultOpenReview]);

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

      <ReportStatusNotice status={report.status} />

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
        {canComment ? (
          <Button onClick={() => setIsReviewOpen((current) => !current)} size="sm" variant="outline">
            {isReviewOpen ? 'Ẩn góp ý' : getReviewButtonLabel(report.commentCount)}
          </Button>
        ) : null}
        {role === 'GROUP_LEADER' ? (
          <LeaderReviewButton currentUserId={currentUserId} groupId={groupId} report={report} />
        ) : null}
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

      {role === 'LAB_MANAGER' ? <ManagerReviewActions labId={labId} report={report} /> : null}

      {canComment && isReviewOpen ? (
        <ReviewPanel canComment currentUserId={currentUserId} reportId={report.id} />
      ) : null}
    </article>
  );
}

function getReviewButtonLabel(commentCount?: number | null) {
  if (commentCount == null) {
    return 'Xem góp ý';
  }
  return `Xem góp ý (${commentCount})`;
}

function ReportStatusNotice({ status }: { status: ResearchReport['status'] }) {
  if (status === 'NEEDS_REVISION') {
    return (
      <p className="mt-3 rounded-md border border-orange-200 bg-orange-50 px-3 py-2 text-sm font-semibold text-orange-800">
        Báo cáo cần chỉnh sửa
      </p>
    );
  }
  if (status === 'APPROVED') {
    return (
      <p className="mt-3 rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-semibold text-emerald-800">
        Báo cáo đã được duyệt
      </p>
    );
  }
  return null;
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
