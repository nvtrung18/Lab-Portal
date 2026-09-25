import { Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { ReportDiscussionPanel } from './ReportDiscussionPanel';

interface ReportCommentsModalProps {
  isOpen: boolean;
  onClose: () => void;
  reportId: number;
  taskTitle: string;
  version: number;
  currentUserId?: number | null;
}

export function ReportCommentsModal({
  isOpen,
  onClose,
  reportId,
  taskTitle,
  version,
}: ReportCommentsModalProps) {
  const { t } = useI18n();
  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      size="lg"
      title={t('research.report.commentsTitle', undefined, { task: taskTitle, version })}
    >
      <div className="space-y-4 max-h-[70vh] overflow-y-auto pr-1">
        <ReportDiscussionPanel
          reportId={reportId}
          canComment={true}
        />
      </div>
    </Modal>
  );
}
