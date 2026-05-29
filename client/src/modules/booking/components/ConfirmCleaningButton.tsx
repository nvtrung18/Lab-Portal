import { useState } from 'react';

import { Button, Modal } from '../../../shared/components';
import { useCompleteCleaningTask } from '../hooks';

interface ConfirmCleaningButtonProps {
  taskId?: number | null;
  status: string;
}

export function ConfirmCleaningButton({ taskId, status }: ConfirmCleaningButtonProps) {
  const [isOpen, setIsOpen] = useState(false);
  const completeTask = useCompleteCleaningTask();

  if (status === 'DONE' || status === 'COMPLETED') {
    return (
      <span className="inline-flex rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700 ring-1 ring-emerald-200">
        Đã hoàn thành
      </span>
    );
  }

  if (status === 'CANCELLED') {
    return (
      <span className="inline-flex rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600 ring-1 ring-slate-200">
        Đã hủy
      </span>
    );
  }

  if (status !== 'ASSIGNED' || !taskId) {
    return null;
  }

  return (
    <>
      <Button size="sm" onClick={() => setIsOpen(true)}>
        Xác nhận hoàn thành
      </Button>
      <Modal
        closeDisabled={completeTask.isPending}
        footer={(
          <>
            <Button disabled={completeTask.isPending} onClick={() => setIsOpen(false)} variant="outline">
              Hủy
            </Button>
            <Button
              loading={completeTask.isPending}
              loadingText="Đang xác nhận..."
              onClick={() => {
                completeTask.mutate(taskId, {
                  onSuccess: () => setIsOpen(false),
                });
              }}
            >
              Xác nhận
            </Button>
          </>
        )}
        isOpen={isOpen}
        onClose={() => setIsOpen(false)}
        size="sm"
        title="Xác nhận vệ sinh PTN"
      >
        <p className="text-sm text-slate-600">
          Bạn có chắc muốn xác nhận đã hoàn thành nhiệm vụ vệ sinh này không?
        </p>
      </Modal>
    </>
  );
}
