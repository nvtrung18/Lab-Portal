import { useEffect, useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import type { CleaningTask } from '../api';
import { useAssignCleaningTask, useEligibleCleaners } from '../hooks';
import { getBookingStatusLabel } from '../utils';
import { formatDateTime } from '../../penalty/utils';

interface AssignCleaningModalProps {
  labId?: number | null;
  task: CleaningTask | null;
  isOpen: boolean;
  onClose: () => void;
}

export function AssignCleaningModal({ labId, task, isOpen, onClose }: AssignCleaningModalProps) {
  const { locale, t } = useI18n();
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
            {t('common.cancel')}
          </Button>
          <Button
            disabled={!selectedIds.length}
            loading={assignCleaning.isPending}
            loadingText={t('booking.assigningCleaning')}
            onClick={() => {
              assignCleaning.mutate(
                { slotId: task.slotId, assigneeIds: selectedIds },
                { onSuccess: onClose },
              );
            }}
          >
            {t('booking.assignCleaningConfirm')}
          </Button>
        </>
      )}
      onClose={onClose}
      size="lg"
      subtitle={(
        <>
          <p>{task.labName ?? t('cleaning.noLab')}</p>
          <p className="mt-1">
              {formatDateTime(task.startTime, locale)} - {formatDateTime(task.endTime, locale)}
            </p>
        </>
      )}
      title={t('cleaning.assignTitle')}
    >
        <div className="space-y-3">
          {isLoading ? (
            <div className="h-20 animate-pulse rounded-md bg-slate-100" />
          ) : !cleaners.length ? (
            <div className="rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
              {t('booking.noEligibleStudents')}
            </div>
          ) : (
            cleaners.map((cleaner) => (
              <label
                key={cleaner.userId}
                className="flex min-h-11 cursor-pointer items-start gap-3 rounded-md border border-slate-200 p-3 transition hover:bg-slate-50"
              >
                <input
                  className="mt-1 h-4 w-4"
                  type="checkbox"
                  checked={selectedIds.includes(cleaner.userId)}
                  onChange={() => toggleCleaner(cleaner.userId)}
                />
                <span className="min-w-0">
                  <span className="block break-words text-sm font-semibold text-slate-900">
                    {cleaner.fullName || cleaner.email}
                  </span>
                  <span className="block break-all text-sm text-slate-600">{cleaner.email}</span>
                  <span className="mt-1 block text-xs font-medium text-slate-500">
                    {t('booking.registration')}: {getBookingStatusLabel(cleaner.bookingStatus, locale)}
                    {cleaner.checkedIn ? ` - ${t('booking.studentCheckedIn')}` : ''}
                  </span>
                </span>
              </label>
            ))
          )}
        </div>

    </Modal>
  );
}
