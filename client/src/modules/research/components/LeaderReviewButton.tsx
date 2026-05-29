import { useState } from 'react';

import { Button } from '../../../shared/components';
import { useLeaderReviewReport } from '../hooks';
import type { ResearchReport } from '../types';
import { LeaderReviewModal } from './LeaderReviewModal';

interface LeaderReviewButtonProps {
  report: ResearchReport;
  groupId?: number | null;
  currentUserId?: number | null;
}

export function LeaderReviewButton({ report, groupId, currentUserId }: LeaderReviewButtonProps) {
  const [isOpen, setIsOpen] = useState(false);
  const leaderReview = useLeaderReviewReport(
    report.id,
    report.milestoneId,
    report.projectId,
    groupId ?? report.groupId,
    report.taskId,
  );

  const canReview = currentUserId != null
    && Number(report.submittedById) !== Number(currentUserId)
    && report.status === 'SUBMITTED'
    && report.isLatestVersion === true;

  if (!canReview) {
    return null;
  }

  return (
    <>
      <Button onClick={() => setIsOpen(true)} size="sm">
        Đánh giá báo cáo
      </Button>
      <LeaderReviewModal
        decision={null}
        isOpen={isOpen}
        isSubmitting={leaderReview.isPending}
        onClose={() => setIsOpen(false)}
        onSubmit={(decision, comment) =>
          leaderReview.mutate({ decision, comment }, { onSuccess: () => setIsOpen(false) })
        }
      />
    </>
  );
}
