import { EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { useReportsByMilestone } from '../hooks';
import { ReportReadOnlyItem } from './ReportList';

interface ManagerReportsListProps {
  milestoneId: number;
}

export function ManagerReportsList({ milestoneId }: ManagerReportsListProps) {
  const { data: reports = [], isError, isLoading, refetch } = useReportsByMilestone(milestoneId);

  return (
    <section className="rounded-lg border border-slate-200 bg-slate-50 p-5">
      <h4 className="text-base font-semibold text-slate-950">Báo cáo đã nộp</h4>
      <p className="mt-1 text-sm text-slate-600">
        Danh sách báo cáo của mốc nghiên cứu để theo dõi phiên bản và tài liệu đính kèm.
      </p>

      {isLoading ? (
        <LoadingState className="mt-5">Đang tải danh sách báo cáo...</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          Không thể tải danh sách báo cáo.
        </ErrorState>
      ) : !reports.length ? (
        <EmptyState className="mt-5">Mốc nghiên cứu này chưa có báo cáo nào được nộp.</EmptyState>
      ) : (
        <div className="mt-5 space-y-4">
          {reports.map((report) => (
            <ReportReadOnlyItem key={report.id} report={report} />
          ))}
        </div>
      )}
    </section>
  );
}
