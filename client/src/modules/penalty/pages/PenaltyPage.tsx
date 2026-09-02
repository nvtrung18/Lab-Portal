import { PenaltyItem } from '../components';
import { useMyPenalties, useSubmitComplaint } from '../hooks';

export function PenaltyPage() {
  const { data: penalties = [], isLoading, isError, refetch } = useMyPenalties();
  const submitComplaint = useSubmitComplaint();

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="h-6 w-56 animate-pulse rounded bg-slate-200" />
        <div className="mt-5 space-y-3">
          <div className="h-36 animate-pulse rounded bg-slate-100" />
          <div className="h-36 animate-pulse rounded bg-slate-100" />
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-lg border border-red-200 bg-white p-6 text-sm text-red-700 shadow-sm">
        Không thể tải danh sách vi phạm.
        <button className="ml-3 font-semibold underline" type="button" onClick={() => refetch()}>
          Tải lại
        </button>
      </section>
    );
  }

  return (
    <section className="space-y-4">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-950">Vi phạm & khiếu nại</h1>
        <p className="mt-2 text-sm text-slate-600">
          Danh sách các vi phạm được ghi nhận trong quá trình sử dụng PTN.
        </p>
      </div>

      {!penalties.length ? (
        <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600 shadow-sm">
          Bạn chưa có vi phạm nào được ghi nhận.
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {penalties.map((penalty) => (
            <PenaltyItem
              key={penalty.id}
              isSubmitting={submitComplaint.isPending}
              penalty={penalty}
              onSubmitComplaint={(penaltyId, content) =>
                submitComplaint.mutate({ penaltyId, content })
              }
            />
          ))}
        </div>
      )}
    </section>
  );
}
