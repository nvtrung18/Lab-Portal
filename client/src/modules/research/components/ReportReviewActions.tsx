import { useState } from 'react';

import { Button } from '../../../shared/components';
import { useLeaderReviewReport, useManagerReviewReport } from '../hooks';
import type { TaskBoardRole } from '../taskBoardHelpers';
import type { ManagerReportDecision, ResearchReport } from '../types';

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
  labId,
}: ReportReviewActionsProps) {
  const [note, setNote] = useState('');
  const leaderReview = useLeaderReviewReport(report.id, milestoneId, projectId, groupId);
  const managerReview = useManagerReviewReport(report.id, milestoneId, projectId, labId);
  const isLeaderAction = role === 'GROUP_LEADER'
    && (report.status === 'SUBMITTED' || report.status === 'NEEDS_REVISION');
  const isManagerAction = role === 'LAB_MANAGER' && report.status === 'LEADER_REVIEWED';

  if (!isLeaderAction && !isManagerAction) {
    return null;
  }

  function reviewAsManager(decision: ManagerReportDecision) {
    const comment = note.trim();
    if (!comment) {
      return;
    }
    managerReview.mutate({ decision, comment }, { onSuccess: () => setNote('') });
  }

  return (
    <section className="mt-4 rounded-md border border-amber-200 bg-amber-50 p-4">
      <h6 className="text-sm font-semibold text-slate-900">
        {isLeaderAction ? 'Kiểm tra báo cáo của thành viên' : 'Duyệt cuối báo cáo'}
      </h6>
      <textarea
        className="mt-3 min-h-20 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
        maxLength={5000}
        placeholder={isLeaderAction ? 'Nhập ghi chú kiểm tra...' : 'Nhập nhận xét duyệt báo cáo...'}
        value={note}
        onChange={(event) => setNote(event.target.value)}
      />
      <div className="mt-3 flex flex-wrap gap-2">
        {isLeaderAction ? (
          <Button
            disabled={!note.trim()}
            loading={leaderReview.isPending}
            onClick={() => leaderReview.mutate(note.trim(), { onSuccess: () => setNote('') })}
            size="sm"
          >
            Đánh dấu đã kiểm tra
          </Button>
        ) : (
          <>
            <Button
              disabled={!note.trim()}
              loading={managerReview.isPending}
              onClick={() => reviewAsManager('APPROVE')}
              size="sm"
            >
              Duyệt báo cáo
            </Button>
            <Button
              disabled={!note.trim()}
              loading={managerReview.isPending}
              onClick={() => reviewAsManager('REQUEST_REVISION')}
              size="sm"
              variant="outline"
            >
              Yêu cầu chỉnh sửa
            </Button>
            <Button
              disabled={!note.trim()}
              loading={managerReview.isPending}
              onClick={() => reviewAsManager('REJECT')}
              size="sm"
              variant="danger"
            >
              Từ chối
            </Button>
          </>
        )}
      </div>
    </section>
  );
}
