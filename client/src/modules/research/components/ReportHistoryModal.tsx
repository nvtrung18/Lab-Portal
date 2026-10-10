import { useMemo } from 'react';
import { Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { useReportsByTask } from '../hooks';
import { sortReportsNewestFirst } from './ReportList';
import { ReportVersionItem } from './ReportVersionItem';
import type { TaskBoardRole } from '../taskBoardHelpers';

interface ReportHistoryModalProps {
  isOpen: boolean;
  onClose: () => void;
  taskId: number;
  taskTitle: string;
  currentUserId?: number | null;
  role?: TaskBoardRole;
  labId?: number | null;
  groupId?: number | null;
  defaultOpenLatestReview?: boolean;
}

export function ReportHistoryModal({
  isOpen,
  onClose,
  taskId,
  taskTitle,
  currentUserId,
  role,
  labId,
  groupId,
  defaultOpenLatestReview = false,
}: ReportHistoryModalProps) {
  const { t } = useI18n();
  const { data: reports = [], isLoading, isError, refetch } = useReportsByTask(taskId);

  // Security check: regular member can only see their own reports
  const filteredReports = useMemo(() => {
    if (role === 'STUDENT_MEMBER' && currentUserId) {
      return reports.filter((r) => r.submittedById === currentUserId);
    }
    return reports;
  }, [reports, role, currentUserId]);

  const orderedReports = sortReportsNewestFirst(filteredReports);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      size="xl"
      title={t('research.report.historyTitle', undefined, { task: taskTitle })}
    >
      <div className="min-w-0 space-y-4">
        {isLoading ? (
          <p className="text-sm text-slate-500 py-4 text-center">{t('research.report.historyLoading')}</p>
        ) : isError ? (
          <div className="text-center py-4">
            <p className="text-sm text-red-500">{t('research.report.historyLoadFailed')}</p>
            <button onClick={() => refetch()} className="mt-2 text-xs font-semibold text-blue-600 underline" type="button">
              {t('common.retry')}
            </button>
          </div>
        ) : filteredReports.length === 0 ? (
          <p className="text-sm text-slate-500 py-8 text-center">{t('research.report.historyEmpty')}</p>
        ) : (
          orderedReports.map((report, index) => (
            <ReportVersionItem
              key={report.id}
              report={report}
              isLatest={index === 0}
              defaultOpenReview={index === 0 && defaultOpenLatestReview}
              currentUserId={currentUserId}
              role={role}
              labId={labId}
              groupId={groupId}
            />
          ))
        )}
      </div>
    </Modal>
  );
}
