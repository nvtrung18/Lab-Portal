import { useEffect, useState } from 'react';

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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 px-4 py-6">
      <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-lg bg-white p-6 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold text-slate-950">Phân công vệ sinh</h3>
            <p className="mt-1 text-sm text-slate-600">{task.labName ?? 'PTN'}</p>
            <p className="mt-1 text-sm text-slate-600">
              {formatDateTime(task.startTime)} - {formatDateTime(task.endTime)}
            </p>
          </div>
          <button
            className="rounded-md border border-slate-200 px-3 py-2 text-sm font-semibold text-slate-700"
            type="button"
            onClick={onClose}
          >
            Đóng
          </button>
        </div>

        <div className="mt-5 space-y-3">
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
                    Booking: {cleaner.bookingStatus}
                    {cleaner.checkedIn ? ' - Đã check-in' : ''}
                  </span>
                </span>
              </label>
            ))
          )}
        </div>

        <div className="mt-6 flex justify-end gap-2">
          <button
            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700"
            disabled={assignCleaning.isPending}
            type="button"
            onClick={onClose}
          >
            Hủy
          </button>
          <button
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
            disabled={!selectedIds.length || assignCleaning.isPending}
            type="button"
            onClick={() => {
              assignCleaning.mutate(
                { slotId: task.slotId, assigneeIds: selectedIds },
                { onSuccess: onClose },
              );
            }}
          >
            {assignCleaning.isPending ? 'Đang phân công...' : 'Xác nhận phân công'}
          </button>
        </div>
      </div>
    </div>
  );
}
