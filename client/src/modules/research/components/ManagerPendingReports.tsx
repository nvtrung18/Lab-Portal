import { EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { useCurrentUser } from '../../user/hooks';
import { usePendingManagerReports } from '../hooks';
import { ReportItem } from './ReportItem';

interface ManagerPendingReportsProps {
  labId: number;
}

export function ManagerPendingReports({ labId }: ManagerPendingReportsProps) {
  const { t } = useI18n();
  const { data: reports = [], isError, isLoading, refetch } = usePendingManagerReports(labId);
  const { data: currentUser } = useCurrentUser();

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <h3 className="text-lg font-semibold text-slate-950">{t('research.report.managerPending')}</h3>

      {isLoading ? (
        <LoadingState className="mt-5">{t('research.report.pendingLoading')}</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          {t('research.report.pendingLoadFailed')}
        </ErrorState>
      ) : !reports.length ? (
        <EmptyState className="mt-5">{t('research.report.emptyPending')}</EmptyState>
      ) : (
        <div className="mt-5 space-y-4">
          {reports.map((report) => (
            <ReportItem
              canComment
              currentUserId={currentUser?.id}
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
