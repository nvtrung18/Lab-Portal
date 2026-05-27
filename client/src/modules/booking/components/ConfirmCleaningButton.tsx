import { Button } from '../../../shared/components';
import { useCompleteCleaningTask } from '../hooks';

interface ConfirmCleaningButtonProps {
  taskId?: number | null;
  status: string;
}

export function ConfirmCleaningButton({ taskId, status }: ConfirmCleaningButtonProps) {
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
    <Button
      loading={completeTask.isPending}
      loadingText="Đang xác nhận..."
      size="sm"
      onClick={() => {
        if (window.confirm('Bạn có chắc muốn xác nhận đã hoàn thành nhiệm vụ vệ sinh này không?')) {
          completeTask.mutate(taskId);
        }
      }}
    >
      Xác nhận hoàn thành
    </Button>
  );
}
