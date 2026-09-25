import { useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import { useI18n } from '../../../shared/i18n';
import { useCompleteCleaningTask } from '../hooks';

interface ConfirmCleaningButtonProps {
  taskId?: number | null;
  status: string;
}

export function ConfirmCleaningButton({ taskId, status }: ConfirmCleaningButtonProps) {
  const { t } = useI18n();
  const [isOpen, setIsOpen] = useState(false);
  const completeTask = useCompleteCleaningTask();

  if (status === 'DONE' || status === 'COMPLETED') {
    return (
      <span className="inline-flex rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700 ring-1 ring-emerald-200">
        {t('cleaning.done')}
      </span>
    );
  }

  if (status === 'CANCELLED') {
    return (
      <span className="inline-flex rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600 ring-1 ring-slate-200">
        {t('booking.status.cancelled')}
      </span>
    );
  }

  if (status !== 'ASSIGNED' || !taskId) {
    return null;
  }

  return (
    <>
      <Button size="sm" onClick={() => setIsOpen(true)}>
        {t('cleaning.confirmComplete')}
      </Button>
      <Modal
        closeDisabled={completeTask.isPending}
        footer={(
          <>
            <Button disabled={completeTask.isPending} onClick={() => setIsOpen(false)} variant="outline">
              {t('common.cancel')}
            </Button>
            <Button
              loading={completeTask.isPending}
              loadingText={t('cleaning.confirming')}
              onClick={() => {
                completeTask.mutate(taskId, {
                  onSuccess: () => setIsOpen(false),
                });
              }}
            >
              {t('common.confirm')}
            </Button>
          </>
        )}
        isOpen={isOpen}
        onClose={() => setIsOpen(false)}
        size="sm"
        title={t('cleaning.confirmTitle')}
      >
        <p className="text-sm text-slate-600">
          {t('cleaning.confirmMessage')}
        </p>
      </Modal>
    </>
  );
}
