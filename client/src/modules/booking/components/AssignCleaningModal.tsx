import { useEffect, useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import type { CleaningTask } from '../api';
import { useAssignCleaningTask, useEligibleCleaners } from '../hooks';
import { formatDateTime } from '../../penalty/utils';

interface AssignCleaningModalProps {
  labId?: number | null;
  task: CleaningTask | null;
  isOpen: boolean;
  onClose: () => void;
}

export function AssignCleaningModal({ labId, task, isOpen, onClose }: AssignCleaningModalProps) {
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const { data: cleaners = [], isLoading } = useEligibleCleaners(task?.slotId);
  const assignCleaning = useAssignCleaningTask(labId);

  useEffect(() => {
    if (isOpen) {
      setSelectedIds([]);
    }
  }, [isOpen, task?.id]);

  if (!isOpen || !task) {
    return null;
  }

  const toggleCleaner = (userId: number) => {
    setSelectedIds((current) =>
      current.includes(userId)
        ? current.filter((id) => id !== userId)
        : [...current, userId],
    );
  };

  return (
    <Modal
      footer={(
        <>
          <Button disabled={assignCleaning.isPending} onClick={onClose} variant="outline">
            Hủy
          </Button>
          <Button
            disabled={!selectedIds.length}
            loading={assignCleaning.isPending}
            loadingText="Đang phân công..."
            onClick={() => {
              assignCleaning.mutate(
                { slotId: task.slotId, assigneeIds: selectedIds },
                { onSuccess: onClose },
              );
            }}
          >
            Xác nhận phân công
          </Button>
        </>
      )}
      onClose={onClose}
      size="lg"
      subtitle={(
        <>
          <p>{task.labName ?? 'PTN'}</p>
          <p className="mt-1">
              {formatDateTime(task.startTime)} - {formatDateTime(task.endTime)}
            </p>
        </>
      )}
      title="Phân công vệ sinh"
    >
        <div className="space-y-3">
          {isLoading ? (
            <div className="h-20 animate-pulse rounded-md bg-slate-100" />
          ) : !cleaners.length ? (
            <div className="rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
              Không có sinh viên đủ điều kiện phân công cho ca này.
            </div>
          ) : (
            cleaners.map((cleaner) => (
              <label
                key={cleaner.userId}
                className="flex cursor-pointer items-start gap-3 rounded-md border border-slate-200 p-3 transition hover:bg-slate-50"
              >
                <input
                  className="mt-1 h-4 w-4"
                  type="checkbox"
                  checked={selectedIds.includes(cleaner.userId)}
                  onChange={() => toggleCleaner(cleaner.userId)}
                />
                <span>
                  <span className="block text-sm font-semibold text-slate-900">
                    {cleaner.fullName || cleaner.email}
                  </span>
                  <span className="block text-sm text-slate-600">{cleaner.email}</span>
                  <span className="mt-1 block text-xs font-medium text-slate-500">
                    Đăng ký: {cleaner.bookingStatus}
                    {cleaner.checkedIn ? ' - Đã check-in' : ''}
                  </span>
                </span>
              </label>
            ))
          )}
        </div>

    </Modal>
  );
}
