import { useState, useEffect } from 'react';

import { Button, toast } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { downloadReportFile } from '../api';
import type { ResearchReport } from '../types';
import { formatDate, formatReportSubmitterName } from '../utils';
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
  const { locale, t } = useI18n();
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
      toast.error(t('research.report.downloadFailed'));
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
                {t('research.report.latestVersion')}
              </span>
            ) : null}
          </div>
          <h6 className="mt-1 font-semibold text-slate-900">{report.title}</h6>
        </div>
        <ReportStatusBadge status={report.status} submittedByGroupRole={report.submittedByGroupRole} />
      </div>

      <ReportStatusNotice status={report.status} />

      <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
        <ReportField label={t('research.report.uploader')} value={getSubmitterLabel(report, locale)} />
        <ReportField label={t('research.report.submittedAt')} value={formatDate(report.createdAt, locale)} />
        {report.milestoneTitle ? <ReportField label={t('research.report.milestone')} value={report.milestoneTitle} /> : null}
        {report.taskTitle ? <ReportField label={t('research.report.task')} value={report.taskTitle} /> : null}
        <ReportField label={t('research.product.fileName')} value={report.fileName || t('common.notUpdated')} />
        <ReportField label={t('research.product.fileSize')} value={formatFileSize(report.fileSize, t('common.notUpdated'))} />
      </dl>

      <div className="mt-4 flex flex-wrap gap-3 text-sm">
        <Button
          loading={isDownloading}
          loadingText={t('common.loading')}
          size="sm"
          variant="outline"
          onClick={handleDownload}
        >
          {t('research.report.openDocument')}
        </Button>
        {canComment ? (
          <Button onClick={() => setIsReviewOpen((current) => !current)} size="sm" variant="outline">
            {isReviewOpen ? t('research.report.hideComments') : getReviewButtonLabel(report.commentCount, t)}
          </Button>
        ) : null}
        {role === 'GROUP_LEADER' && isLatest ? (
          <LeaderReviewButton currentUserId={currentUserId} groupId={groupId} report={report} />
        ) : null}
        {report.evidenceLink ? (
          <a
            className="font-semibold text-blue-700 underline"
            href={report.evidenceLink}
            rel="noreferrer"
            target="_blank"
          >
            {t('research.report.evidenceLink')}
          </a>
        ) : null}
      </div>

      {role === 'LAB_MANAGER' && isLatest ? <ManagerReviewActions labId={labId} report={report} /> : null}

      {canComment && isReviewOpen ? (
        <ReviewPanel canComment currentUserId={currentUserId} reportId={report.id} />
      ) : null}
    </article>
  );
}

function getReviewButtonLabel(commentCount: number | null | undefined, t: ReturnType<typeof useI18n>['t']) {
  if (commentCount == null) {
    return t('research.report.viewComments');
  }
  return t('research.report.viewCommentsCount', undefined, { count: commentCount });
}

function ReportStatusNotice({ status }: { status: ResearchReport['status'] }) {
  const { t } = useI18n();
  if (status === 'NEEDS_REVISION') {
    return (
      <p className="mt-3 rounded-md border border-orange-200 bg-orange-50 px-3 py-2 text-sm font-semibold text-orange-800">
        {t('research.report.needsRevision')}
      </p>
    );
  }
  if (status === 'APPROVED') {
    return (
      <p className="mt-3 rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-semibold text-emerald-800">
        {t('research.report.approvedNotice')}
      </p>
    );
  }
  if (status === 'LEADER_REJECTED') {
    return (
      <p className="mt-3 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-800">
        {t('research.report.leaderRejectedNotice')}
      </p>
    );
  }
  if (status === 'MANAGER_REJECTED') {
    return (
      <p className="mt-3 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-800">
        {t('research.report.managerRejectedNotice')}
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

function getSubmitterLabel(report: ResearchReport, locale: 'vi' | 'en') {
  return formatReportSubmitterName(report, locale);
}

function formatFileSize(size: number | null | undefined, fallback: string) {
  if (size == null) {
    return fallback;
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(2)} MB`;
}
