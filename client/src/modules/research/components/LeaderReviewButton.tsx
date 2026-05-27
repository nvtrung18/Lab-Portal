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
    && report.submittedById !== currentUserId
    && (report.status === 'SUBMITTED' || report.status === 'NEEDS_REVISION');

  if (!canReview) {
    return null;
  }

  return (
    <>
      <Button onClick={() => setIsOpen(true)} size="sm">
        Đánh dấu đã kiểm tra
      </Button>
      <LeaderReviewModal
        isOpen={isOpen}
        isSubmitting={leaderReview.isPending}
        onClose={() => setIsOpen(false)}
        onSubmit={(note) => leaderReview.mutate(note, { onSuccess: () => setIsOpen(false) })}
      />
    </>
  );
}
