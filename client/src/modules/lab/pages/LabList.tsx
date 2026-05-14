import { useMemo, useState } from 'react';

import { ApplyModal } from '../components';
import { useLabs } from '../hooks';

export function LabList() {
  const { data: labs = [], isLoading, isError } = useLabs();
  const [selectedLabId, setSelectedLabId] = useState<number | null>(null);

  const selectedLab = useMemo(
    () => labs.find((lab) => lab.id === selectedLabId),
    [labs, selectedLabId],
  );

  if (isLoading) {
    return (
      <section className="space-y-4">
        <div className="h-7 w-48 animate-pulse rounded bg-slate-200" />
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, index) => (
            <div
              key={index}
              className="h-44 animate-pulse rounded-lg border border-slate-200 bg-white"
            />
          ))}
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-lg border border-red-200 bg-white p-6 text-sm text-red-700 shadow-sm">
        Không thể tải danh sách lab.
      </section>
    );
  }

  return (
    <section>
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="text-xl font-semibold text-slate-950">Danh sách Lab</h2>
          <p className="mt-1 text-sm text-slate-600">
            Chọn lab phù hợp và nộp CV ứng tuyển.
          </p>
        </div>
        <span className="text-sm text-slate-500">{labs.length} lab</span>
      </div>

      {labs.length === 0 ? (
        <div className="rounded-lg border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-600">
          Hiện chưa có lab nào.
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {labs.map((lab) => (
            <article
              key={lab.id}
              className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h3 className="text-base font-semibold text-slate-950">
                    {lab.labName}
                  </h3>
                  <p className="mt-1 text-sm text-slate-600">
                    {lab.department || 'Chưa phân khoa'}
                  </p>
                </div>
                <span className="rounded-full bg-emerald-50 px-2 py-1 text-xs font-semibold text-emerald-700 ring-1 ring-emerald-200">
                  {lab.status}
                </span>
              </div>

              <p className="mt-4 line-clamp-3 min-h-12 text-sm text-slate-600">
                {lab.description || 'Lab chưa có mô tả.'}
              </p>

              <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-slate-500">Sức chứa</dt>
                  <dd className="font-medium text-slate-950">{lab.capacity}</dd>
                </div>
                <div>
                  <dt className="text-slate-500">Địa điểm</dt>
                  <dd className="font-medium text-slate-950">{lab.location}</dd>
                </div>
              </dl>

              <button
                type="button"
                className="mt-5 w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-800"
                onClick={() => setSelectedLabId(lab.id)}
              >
                Ứng tuyển
              </button>
            </article>
          ))}
        </div>
      )}

      <ApplyModal
        labId={selectedLabId}
        labName={selectedLab?.labName}
        onClose={() => setSelectedLabId(null)}
      />
    </section>
  );
}
