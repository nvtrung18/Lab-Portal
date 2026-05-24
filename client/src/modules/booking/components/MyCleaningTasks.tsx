import { useMyCleaningTasks } from '../hooks';
import { CleaningItem } from './CleaningItem';

export function MyCleaningTasks() {
  const { data: tasks = [], isError, isFetching, isLoading, refetch } = useMyCleaningTasks();

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="h-5 w-40 animate-pulse rounded bg-slate-200" />
        <div className="mt-4 space-y-3">
          {[1, 2].map((item) => (
            <div key={item} className="h-32 animate-pulse rounded-md bg-slate-100" />
          ))}
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-lg border border-red-200 bg-white p-5 text-sm text-red-700 shadow-sm">
        <p>Không thể tải danh sách nhiệm vụ vệ sinh.</p>
        <button
          type="button"
          className="mt-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700 transition hover:bg-red-100"
          onClick={() => refetch()}
        >
          Tải lại
        </button>
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">Nhiệm vụ vệ sinh của tôi</h3>
          <p className="mt-1 text-sm text-slate-600">
            Danh sách các nhiệm vụ vệ sinh đang được phân công cho bạn.
          </p>
        </div>
        {isFetching ? (
          <span className="text-xs font-medium text-slate-500">Đang cập nhật...</span>
        ) : null}
      </div>

      {!tasks.length ? (
        <div className="rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
          Hiện chưa có nhiệm vụ vệ sinh nào cần thực hiện.
        </div>
      ) : (
        <div className="space-y-3">
          {tasks.map((task) => (
            <CleaningItem key={task.id ?? `slot-${task.slotId}`} task={task} />
          ))}
        </div>
      )}
    </section>
  );
}
