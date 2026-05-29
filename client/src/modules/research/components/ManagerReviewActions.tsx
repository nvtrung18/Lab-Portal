import { useState } from 'react';

import { Button } from '../../../shared/components';
import { useManagerReviewReport } from '../hooks';
import type { ManagerReportDecision, ResearchReport } from '../types';
import { ManagerReviewModal } from './ManagerReviewModal';

interface ManagerReviewActionsProps {
  labId?: number | null;
  report: ResearchReport;
}

export function ManagerReviewActions({ labId, report }: ManagerReviewActionsProps) {
  const [decision, setDecision] = useState<ManagerReportDecision | null>(null);
  const managerReview = useManagerReviewReport(
    report.id,
    report.milestoneId,
    report.projectId,
    labId,
    report.taskId,
    report.groupId,
  );

  if (report.status !== 'LEADER_REVIEWED') {
    return null;
  }

  return (
    <section className="mt-4 rounded-md border border-amber-200 bg-amber-50 p-4">
      <h6 className="text-sm font-semibold text-slate-900">Nhận xét của quản lý</h6>
      <div className="mt-3 flex flex-wrap gap-2">
        <Button onClick={() => setDecision('APPROVE')} size="sm">
          Duyệt báo cáo
        </Button>
        <Button onClick={() => setDecision('REQUEST_REVISION')} size="sm" variant="outline">
          Yêu cầu chỉnh sửa
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
