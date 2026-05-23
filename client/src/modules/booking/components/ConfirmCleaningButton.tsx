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
    <button
      className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
      disabled={completeTask.isPending}
      type="button"
      onClick={() => {
        if (window.confirm('Bạn có chắc muốn xác nhận đã hoàn thành nhiệm vụ vệ sinh này không?')) {
          completeTask.mutate(taskId);
        }
      }}
    >
      {completeTask.isPending ? 'Đang xác nhận...' : 'Xác nhận hoàn thành'}
    </button>
  );
}
