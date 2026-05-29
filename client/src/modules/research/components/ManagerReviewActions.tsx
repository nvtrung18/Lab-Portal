import { useState } from 'react';

import { Button } from '../../../shared/components';
import { useSystemConfig } from '../../admin/hooks';
import { useManagerReviewReport } from '../hooks';
import type { ManagerReportDecision, ResearchReport } from '../types';
import { ManagerReviewModal } from './ManagerReviewModal';

interface ManagerReviewActionsProps {
  labId?: number | null;
  report: ResearchReport;
}

export function ManagerReviewActions({ labId, report }: ManagerReviewActionsProps) {
  const [decision, setDecision] = useState<ManagerReportDecision | null>(null);
  const { data: systemConfig } = useSystemConfig();
  const managerReview = useManagerReviewReport(
    report.id,
    report.milestoneId,
    report.projectId,
    labId,
    report.taskId,
    report.groupId,
  );

  const requireLeaderReview = systemConfig?.research?.requireLeaderReviewBeforeManagerReview ?? true;
  const isSubmittedByLeader = report.submittedByGroupRole === 'LEADER';
  const isLatest = report.isLatestVersion !== false;

  const isStatusAllowed = isLatest && (requireLeaderReview
    ? (report.status === 'LEADER_REVIEWED' || (report.status === 'SUBMITTED' && isSubmittedByLeader))
    : (report.status === 'SUBMITTED' || report.status === 'LEADER_REVIEWED'));

  if (!isStatusAllowed) {
    return null;
  }

  return (
    <section className="mt-4 rounded-md border border-amber-200 bg-amber-50 p-4">
      <h6 className="text-sm font-semibold text-slate-900">Nhận xét của quản lý</h6>
      <div className="mt-3 flex flex-wrap gap-2">
        <Button onClick={() => setDecision('APPROVE')} size="sm">
          Chấp nhận báo cáo
        </Button>
        <Button onClick={() => setDecision('REQUEST_REVISION')} size="sm" variant="outline">
          Yêu cầu nộp lại
        </Button>
        <Button onClick={() => setDecision('REJECT')} size="sm" variant="danger">
          Từ chối
        </Button>
      </div>
      <ManagerReviewModal
        decision={decision}
        isSubmitting={managerReview.isPending}
        onClose={() => setDecision(null)}
        onSubmit={(nextDecision, comment) =>
          managerReview.mutate(
            { decision: nextDecision, comment },
            { onSuccess: () => setDecision(null) },
          )}
      />
    </section>
  );
}
