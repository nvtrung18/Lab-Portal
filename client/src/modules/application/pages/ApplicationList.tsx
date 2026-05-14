import { useState } from 'react';

import { useApplications, useReviewApplication } from '../hooks';
import type { ApplicationStatus } from '../api';

function statusClassName(status: ApplicationStatus) {
  if (status === 'APPROVED') {
    return 'bg-emerald-50 text-emerald-700 ring-emerald-200';
  }

  if (status === 'REJECTED') {
    return 'bg-red-50 text-red-700 ring-red-200';
  }

  return 'bg-amber-50 text-amber-700 ring-amber-200';
}

export function ApplicationList() {
  const { data: applications = [], isLoading, isError } = useApplications();
  const reviewMutation = useReviewApplication();
  const [processingId, setProcessingId] = useState<number | null>(null);

  const handleReview = async (
    appId: number,
    status: Extract<ApplicationStatus, 'APPROVED' | 'REJECTED'>,
  ) => {
    setProcessingId(appId);
    try {
      await reviewMutation.mutateAsync({ appId, status });
    } finally {
      setProcessingId(null);
    }
  };

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="h-6 w-48 animate-pulse rounded bg-slate-200" />
        <div className="mt-6 space-y-3">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="h-10 animate-pulse rounded bg-slate-100" />
          ))}
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-lg border border-red-200 bg-white p-6 text-sm text-red-700 shadow-sm">
        Không thể tải danh sách đơn ứng tuyển.
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-slate-950">Đơn ứng tuyển</h2>
        <span className="text-sm text-slate-500">{applications.length} đơn</span>
      </div>

      {applications.length === 0 ? (
        <div className="mt-6 rounded-md border border-dashed border-slate-300 p-8 text-center text-sm text-slate-600">
          Hiện chưa có đơn ứng tuyển nào.
        </div>
      ) : (
        <div className="mt-6 overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase text-slate-500">
                <th className="px-3 py-3">Người ứng tuyển</th>
                <th className="px-3 py-3">Lab</th>
                <th className="px-3 py-3">CV</th>
                <th className="px-3 py-3">Trạng thái</th>
                <th className="px-3 py-3 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {applications.map((application) => (
                <tr key={application.id}>
                  <td className="px-3 py-4 text-slate-700">
                    <div className="font-medium text-slate-950">
                      {application.applicantName || `User #${application.userId}`}
                    </div>
                    <div className="mt-1 text-xs text-slate-500">
                      {application.applicantEmail}
                    </div>
                  </td>
                  <td className="px-3 py-4 text-slate-700">
                    {application.labName}
                  </td>
                  <td className="px-3 py-4">
                    <a
                      className="font-medium text-slate-950 underline-offset-2 hover:underline"
                      href={application.cvUrl}
                      rel="noreferrer"
                      target="_blank"
                    >
                      Xem CV
                    </a>
                  </td>
                  <td className="px-3 py-4">
                    <span
                      className={[
                        'inline-flex rounded-full px-2 py-1 text-xs font-semibold ring-1',
                        statusClassName(application.status),
                      ].join(' ')}
                    >
                      {application.status}
                    </span>
                  </td>
                  <td className="px-3 py-4 text-right">
                    {application.status === 'PENDING' ? (
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          disabled={processingId === application.id}
                          className="rounded-md bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-emerald-300"
                          onClick={() => void handleReview(application.id, 'APPROVED')}
                        >
                          Approve
                        </button>
                        <button
                          type="button"
                          disabled={processingId === application.id}
                          className="rounded-md bg-red-600 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-red-300"
                          onClick={() => void handleReview(application.id, 'REJECTED')}
                        >
                          Reject
                        </button>
                      </div>
                    ) : (
                      <span className="text-xs text-slate-400">Đã xử lý</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
