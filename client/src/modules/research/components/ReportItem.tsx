import { useState } from 'react';

import { Button } from '../../../shared/components';
import type { TaskBoardRole } from '../taskBoardHelpers';
import type { ResearchReport, ResearchReportStatus } from '../types';
import { formatDate } from '../utils';
import { ManagerReviewActions } from './ManagerReviewActions';
import { ReportReviewActions } from './ReportReviewActions';
import { ReviewPanel } from './ReviewPanel';

const REPORT_STATUS_LABELS: Record<ResearchReportStatus, string> = {
  SUBMITTED: 'Đã nộp',
  LEADER_REVIEWED: 'Trưởng nhóm đã xem',
  NEEDS_REVISION: 'Cần chỉnh sửa',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
};

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
  const [isReviewOpen, setIsReviewOpen] = useState(false);

  return (
    <article className="rounded-md border border-slate-200 bg-white p-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h5 className="font-semibold text-slate-950">{report.title}</h5>
          <p className="mt-1 text-xs text-slate-500">
            Phiên bản {report.version} · Nộp ngày {formatDate(report.createdAt)}
          </p>
        </div>
        <span className="w-fit rounded-full bg-blue-50 px-3 py-1 text-xs font-semibold text-blue-700">
          {REPORT_STATUS_LABELS[report.status]}
        </span>
      </div>

      <ReportStatusNotice status={report.status} />

      <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
        {report.submittedByName || report.submittedByEmail ? (
          <ReportField
            label="Người nộp"
            value={report.submittedByName
              ? `${report.submittedByName}${report.submittedByEmail ? ` (${report.submittedByEmail})` : ''}`
              : report.submittedByEmail ?? ''}
          />
        ) : null}
        {report.groupName ? <ReportField label="Nhóm" value={report.groupName} /> : null}
        {report.milestoneTitle ? <ReportField label="Mốc nghiên cứu" value={report.milestoneTitle} /> : null}
        {report.taskTitle ? <ReportField label="Nhiệm vụ" value={report.taskTitle} /> : null}
        <ReportField label="Công việc đã làm" value={report.contentDone} />
        <ReportField label="Kết quả đạt được" value={report.result} />
        <ReportField label="Khó khăn gặp phải" value={report.difficulty} />
        <ReportField label="Kế hoạch tiếp theo" value={report.nextPlan} />
        <ReportField label="Tự đánh giá" value={report.selfAssessment} />
        <div>
          <dt className="font-semibold text-slate-700">Tài liệu đính kèm</dt>
          <dd className="mt-1 text-slate-600">
            <a className="font-medium text-blue-700 underline" href={report.fileUrl} rel="noreferrer" target="_blank">
              {report.fileName || 'Mở tài liệu'}
            </a>
            {report.fileSize ? ` (${formatFileSize(report.fileSize)})` : ''}
          </dd>
        </div>
        {report.evidenceLink ? (
          <div>
            <dt className="font-semibold text-slate-700">Link minh chứng</dt>
            <dd className="mt-1">
              <a className="break-all font-medium text-blue-700 underline" href={report.evidenceLink} rel="noreferrer" target="_blank">
                {report.evidenceLink}
              </a>
            </dd>
          </div>
        ) : null}
        {report.leaderComment ? (
          <ReportField label="Ghi chú kiểm tra của trưởng nhóm" value={report.leaderComment} />
        ) : null}
        {report.managerComment ? (
          <ReportField label="Nhận xét của quản lý" value={report.managerComment} />
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
            {isReviewOpen ? 'Ẩn góp ý' : getReviewButtonLabel(report.commentCount)}
          </Button>
        </div>
      ) : null}
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

function ReportStatusNotice({ status }: { status: ResearchReportStatus }) {
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
      <dd className="mt-1 whitespace-pre-wrap text-slate-600">{value}</dd>
    </div>
  );
}

function formatFileSize(size?: number | null) {
  if (!size) {
    return '';
  }
  return size >= 1024 * 1024
    ? `${(size / (1024 * 1024)).toFixed(1)} MB`
    : `${Math.ceil(size / 1024)} KB`;
}
