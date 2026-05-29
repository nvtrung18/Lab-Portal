import { Modal } from '../../../shared/components';
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
  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      size="lg"
      title={`Ý kiến góp ý: ${taskTitle} (Bản v${version})`}
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
