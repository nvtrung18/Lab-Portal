import { EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { useReportsByMilestone } from '../hooks';
import { ReportReadOnlyItem } from './ReportList';

interface ManagerReportsListProps {
  milestoneId: number;
  currentUserId?: number | null;
  labId?: number | null;
}

export function ManagerReportsList({ milestoneId, currentUserId, labId }: ManagerReportsListProps) {
  const { t } = useI18n();
  const { data: reports = [], isError, isLoading, refetch } = useReportsByMilestone(milestoneId);

  return (
    <section className="rounded-lg border border-slate-200 bg-slate-50 p-5">
      <h4 className="text-base font-semibold text-slate-950">{t('research.report.submitted')}</h4>

      {isLoading ? (
        <LoadingState className="mt-5">{t('research.report.loading')}</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          {t('research.report.loadFailed')}
        </ErrorState>
      ) : !reports.length ? (
        <EmptyState className="mt-5">{t('research.report.emptyMilestone')}</EmptyState>
      ) : (
        <div className="mt-5 space-y-4">
          {reports.map((report) => (
            <ReportReadOnlyItem
              currentUserId={currentUserId}
              key={report.id}
              labId={labId}
              report={report}
              role="LAB_MANAGER"
            />
          ))}
        </div>
      )}
    </section>
  );
}
