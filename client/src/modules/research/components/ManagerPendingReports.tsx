import { EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { usePendingManagerReports } from '../hooks';
import { ReportItem } from './ReportItem';

interface ManagerPendingReportsProps {
  labId: number;
}

export function ManagerPendingReports({ labId }: ManagerPendingReportsProps) {
  const { data: reports = [], isError, isLoading, refetch } = usePendingManagerReports(labId);

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <h3 className="text-lg font-semibold text-slate-950">Báo cáo chờ duyệt</h3>
      <p className="mt-1 text-sm text-slate-600">
        Báo cáo đã được trưởng nhóm kiểm tra và đang chờ quyết định cuối của quản lý PTN.
      </p>

      {isLoading ? (
        <LoadingState className="mt-5">Đang tải báo cáo chờ duyệt...</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          Không thể tải danh sách báo cáo chờ duyệt.
        </ErrorState>
      ) : !reports.length ? (
        <EmptyState className="mt-5">Không có báo cáo nào đang chờ duyệt.</EmptyState>
      ) : (
        <div className="mt-5 space-y-4">
          {reports.map((report) => (
            <ReportItem
              canComment
              key={report.id}
              labId={labId}
              milestoneId={report.milestoneId}
              projectId={report.projectId}
              report={report}
              role="LAB_MANAGER"
            />
          ))}
        </div>
      )}
    </section>
  );
}
