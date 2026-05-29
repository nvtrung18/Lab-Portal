import { useState } from 'react';

import { Button } from '../../../shared/components';
import { useLeaderReviewReport } from '../hooks';
import type { TaskBoardRole } from '../taskBoardHelpers';
import type { LeaderReportDecision, ResearchReport } from '../types';
import { LeaderReviewModal } from './LeaderReviewModal';

interface ReportReviewActionsProps {
  report: ResearchReport;
  role?: TaskBoardRole;
  milestoneId: number;
  projectId: number;
  groupId?: number | null;
  labId?: number | null;
}

export function ReportReviewActions({
  report,
  role,
  milestoneId,
  projectId,
  groupId,
}: ReportReviewActionsProps) {
  const [decision, setDecision] = useState<LeaderReportDecision | null>(null);
  const leaderReview = useLeaderReviewReport(report.id, milestoneId, projectId, groupId, report.taskId);
  const isLeaderAction = role === 'GROUP_LEADER' && report.status === 'SUBMITTED';

  if (!isLeaderAction) {
    return null;
  }

  return (
    <section className="mt-4 rounded-md border border-amber-200 bg-amber-50/50 p-4">
      <h6 className="text-sm font-semibold text-slate-900">Kiểm tra báo cáo của thành viên</h6>
      <div className="mt-3 flex flex-wrap gap-2">
        <Button onClick={() => setDecision('ACCEPT')} size="sm">
          Duyệt báo cáo
        </Button>
        <Button onClick={() => setDecision('REQUEST_REVISION')} size="sm" variant="outline">
          Yêu cầu chỉnh sửa
        </Button>
        <Button onClick={() => setDecision('REJECT')} size="sm" variant="danger">
          Từ chối
        </Button>
      </div>
      <LeaderReviewModal
        decision={decision}
        isOpen={decision !== null}
        isSubmitting={leaderReview.isPending}
        onClose={() => setDecision(null)}
        onSubmit={(nextDecision, comment) =>
          leaderReview.mutate(
            { decision: nextDecision, comment },
            { onSuccess: () => setDecision(null) },
          )}
      />
    </section>
  );
}
