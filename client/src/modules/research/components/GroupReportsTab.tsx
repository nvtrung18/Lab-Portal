import { EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { useGroupReports } from '../hooks';
import { ReportReadOnlyItem } from './ReportList';

interface GroupReportsTabProps {
  groupId: number;
  currentUserId?: number | null;
}

export function GroupReportsTab({ groupId, currentUserId }: GroupReportsTabProps) {
  const { data: reports = [], isError, isLoading, refetch } = useGroupReports(groupId);

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <h3 className="text-lg font-semibold text-slate-950">Báo cáo nhóm</h3>
      <p className="mt-1 text-sm text-slate-600">
        Xem phiên bản, trạng thái và tài liệu báo cáo đã nộp của thành viên trong nhóm.
      </p>

      {isLoading ? (
        <LoadingState className="mt-5">Đang tải báo cáo của nhóm...</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          Không thể tải báo cáo của nhóm.
        </ErrorState>
      ) : !reports.length ? (
        <EmptyState className="mt-5">Nhóm này chưa có báo cáo nào được nộp.</EmptyState>
      ) : (
        <div className="mt-5 space-y-4">
          {reports.map((report) => (
            <ReportReadOnlyItem
              currentUserId={currentUserId}
              groupId={groupId}
              key={report.id}
              report={report}
              role="GROUP_LEADER"
            />
          ))}
        </div>
      )}
    </section>
  );
}
