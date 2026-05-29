import { useEffect, useState } from 'react';

import { Button, EmptyState, Modal } from '../../../shared/components';
import type { ResearchTask, ResearchReport } from '../types';
import { ReportUpload } from './ReportUpload';

interface ReportUploadModalProps {
  isOpen: boolean;
  milestoneId: number;
  projectId?: number | null;
  groupId?: number | null;
  tasks: ResearchTask[];
  title?: string;
  mode?: 'create' | 'replace' | 'resubmit';
  reportId?: number | null;
  initialValues?: ResearchReport | null;
  onClose: () => void;
}

export function ReportUploadModal({
  isOpen,
  milestoneId,
  projectId,
  groupId,
  tasks,
  title = 'Nộp báo cáo tiến độ',
  mode = 'create',
  reportId,
  initialValues,
  onClose,
}: ReportUploadModalProps) {
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null);

  useEffect(() => {
    if (isOpen) {
      setSelectedTaskId(tasks[0]?.id ?? null);
    }
  }, [isOpen, tasks]);

  if (!isOpen) {
    return null;
  }

  return (
    <Modal
      footer={<Button onClick={onClose} variant="outline">Đóng</Button>}
      onClose={onClose}
      size="lg"
      title={title}
    >
      {!tasks.length || !selectedTaskId ? (
        <EmptyState>Bạn chưa có nhiệm vụ được giao để nộp báo cáo.</EmptyState>
      ) : (
        <div className="space-y-4">
          {tasks.length > 1 ? (
            <label className="block text-sm">
              <span className="mb-1 block font-semibold text-slate-700">Nhiệm vụ báo cáo</span>
              <select
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                value={selectedTaskId}
                onChange={(event) => setSelectedTaskId(Number(event.target.value))}
              >
                {tasks.map((task) => (
                  <option key={task.id} value={task.id}>{task.title}</option>
                ))}
              </select>
            </label>
          ) : null}
          <ReportUpload
            key={selectedTaskId}
            groupId={groupId}
            milestoneId={milestoneId}
            projectId={projectId}
            taskId={selectedTaskId}
            mode={mode}
            reportId={reportId}
            initialValues={initialValues}
            onSuccess={() => onClose()}
          />
        </div>
      )}
    </Modal>
  );
}
