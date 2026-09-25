import { useState } from 'react';

import { Button } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import type { TaskBoardRole } from '../taskBoardHelpers';
import type { ResearchReport, ResearchReportStatus } from '../types';
import { formatDate, formatReportSubmitterName } from '../utils';
import { ManagerReviewActions } from './ManagerReviewActions';
import { ReportReviewActions } from './ReportReviewActions';
import { ReviewPanel } from './ReviewPanel';

interface ReportItemProps {
  report: ResearchReport;
  canComment: boolean;
  role?: TaskBoardRole;
  milestoneId: number;
  projectId: number;
  groupId?: number | null;
  labId?: number | null;
  currentUserId?: number | null;
}

export function ReportItem({
  report,
  canComment,
  role,
  milestoneId,
  projectId,
  groupId,
  labId,
  currentUserId,
}: ReportItemProps) {
  const { locale, t } = useI18n();
  const [isReviewOpen, setIsReviewOpen] = useState(false);

  return (
    <article className="rounded-md border border-slate-200 bg-white p-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h5 className="font-semibold text-slate-950">{report.title}</h5>
          <p className="mt-1 text-xs text-slate-500">
            {t('research.report.version', undefined, { version: report.version })} · {t('research.report.submittedDate', undefined, { date: formatDate(report.createdAt, locale) })}
          </p>
        </div>
        <span className="w-fit rounded-full bg-blue-50 px-3 py-1 text-xs font-semibold text-blue-700">
          {getStatusLabel(report.status, t)}
        </span>
      </div>

      <ReportStatusNotice status={report.status} />

      <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
        <ReportField
          label={t('research.report.uploader')}
          value={formatReportSubmitterName(report, locale)}
        />
        {report.groupName ? <ReportField label={t('research.report.group')} value={report.groupName} /> : null}
        {report.milestoneTitle ? <ReportField label={t('research.report.milestone')} value={report.milestoneTitle} /> : null}
        {report.taskTitle ? <ReportField label={t('research.report.task')} value={report.taskTitle} /> : null}
        <ReportField label={t('research.report.workDone')} value={report.contentDone} />
        <ReportField label={t('research.report.result')} value={report.result} />
        <ReportField label={t('research.report.difficulty')} value={report.difficulty} />
        <ReportField label={t('research.report.nextPlan')} value={report.nextPlan} />
        <ReportField label={t('research.report.selfAssessment')} value={report.selfAssessment} />
        <div>
          <dt className="font-semibold text-slate-700">{t('research.report.attachment')}</dt>
          <dd className="mt-1 text-slate-600">
            <a className="font-medium text-blue-700 underline" href={report.fileUrl} rel="noreferrer" target="_blank">
              {report.fileName || t('research.report.openDocument')}
            </a>
            {report.fileSize ? ` (${formatFileSize(report.fileSize)})` : ''}
          </dd>
        </div>
        {report.evidenceLink ? (
          <div>
            <dt className="font-semibold text-slate-700">{t('research.report.evidenceLink')}</dt>
            <dd className="mt-1">
              <a className="break-all font-medium text-blue-700 underline" href={report.evidenceLink} rel="noreferrer" target="_blank">
                {report.evidenceLink}
              </a>
            </dd>
          </div>
        ) : null}
        {report.leaderComment ? (
          <ReportField label={t('research.report.leaderNote')} value={report.leaderComment} />
        ) : null}
        {report.managerComment ? (
          <ReportField label={t('research.report.managerNote')} value={report.managerComment} />
        ) : null}
      </dl>

      <ReportReviewActions
        groupId={groupId}
        labId={labId}
        milestoneId={milestoneId}
        projectId={projectId}
        report={report}
        role={role}
      />
      {role === 'LAB_MANAGER' ? <ManagerReviewActions labId={labId} report={report} /> : null}

      {canComment ? (
        <div className="mt-4">
          <Button onClick={() => setIsReviewOpen((current) => !current)} size="sm" variant="outline">
            {isReviewOpen ? t('research.report.hideComments') : getReviewButtonLabel(report.commentCount, t)}
          </Button>
        </div>
      ) : null}
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

function ReportStatusNotice({ status }: { status: ResearchReportStatus }) {
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
      <dd className="mt-1 whitespace-pre-wrap text-slate-600">{value}</dd>
    </div>
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

function formatFileSize(size?: number | null) {
  if (!size) {
    return '';
  }
  return size >= 1024 * 1024
    ? `${(size / (1024 * 1024)).toFixed(1)} MB`
    : `${Math.ceil(size / 1024)} KB`;
}
