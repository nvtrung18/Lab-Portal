import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { getManagedLabId } from '../../../shared/utils/membership';
import { useCurrentUser } from '../../user/hooks';
import { CancelSlotModal } from '../components';
import { useReviewBooking, useSlot, useSlotRegistrations } from '../hooks';
import { getBookingStatusLabel } from '../utils';

function formatDateTime(value?: string) {
  if (!value) {
    return 'Chưa cập nhật';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return 'Chưa cập nhật';
  }
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function SlotDetailPage() {
  const { slotId } = useParams();
  const numericSlotId = Number(slotId);
  const [cancelSlotId, setCancelSlotId] = useState<number | null>(null);
  const { data: currentUser } = useCurrentUser();
  const managedLabId = getManagedLabId(currentUser);
  const { data: slot, isLoading: isLoadingSlot } = useSlot(numericSlotId);
  const { data: registrations = [], isLoading, isError, refetch } = useSlotRegistrations(numericSlotId);
  const reviewBooking = useReviewBooking(managedLabId, numericSlotId);

  const counts = useMemo(
    () => ({
      pending: registrations.filter((booking) => booking.status === 'PENDING_APPROVAL').length,
      approved: registrations.filter((booking) => booking.status === 'APPROVED').length,
    }),
    [registrations],
  );

  const handleReview = (bookingId: number, decision: 'APPROVE' | 'REJECT') => {
    const message =
      decision === 'APPROVE'
        ? 'Phê duyệt đăng ký sử dụng PTN này?'
        : 'Từ chối đăng ký sử dụng PTN này?';
    if (window.confirm(message)) {
      reviewBooking.mutate({ bookingId, decision });
    }
  };

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <Link className="text-sm font-semibold text-slate-600 hover:text-slate-950" to="/app/lab-slots">
          Quay lại danh sách
        </Link>
        <div className="mt-4 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h2 className="text-xl font-semibold text-slate-950">Chi tiết khung giờ</h2>
            <p className="mt-2 text-sm text-slate-600">Danh sách đăng ký sử dụng khung giờ của sinh viên.</p>
          </div>
          {slot && slot.status !== 'CANCELLED' ? (
            <button
              type="button"
              className="w-fit rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700"
              onClick={() => setCancelSlotId(slot.id)}
            >
              Hủy khung giờ
            </button>
          ) : null}
        </div>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        {isLoadingSlot ? (
          <div className="h-20 animate-pulse rounded bg-slate-100" />
        ) : slot ? (
          <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <dt className="text-sm text-slate-500">Thời gian</dt>
              <dd className="mt-1 text-sm font-semibold text-slate-950">
                {formatDateTime(slot.startTime)} - {formatDateTime(slot.endTime)}
              </dd>
            </div>
            <div>
              <dt className="text-sm text-slate-500">Sức chứa</dt>
              <dd className="mt-1 text-sm font-semibold text-slate-950">{slot.capacity}</dd>
            </div>
            <div>
              <dt className="text-sm text-slate-500">Chờ phê duyệt</dt>
              <dd className="mt-1 text-sm font-semibold text-slate-950">{counts.pending}</dd>
            </div>
            <div>
              <dt className="text-sm text-slate-500">Đã phê duyệt</dt>
              <dd className="mt-1 text-sm font-semibold text-slate-950">{counts.approved}</dd>
            </div>
          </dl>
        ) : (
          <p className="text-sm text-slate-600">Không tìm thấy khung giờ sử dụng.</p>
        )}
      </div>

      <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-200 px-5 py-4">
          <h3 className="text-lg font-semibold text-slate-950">Danh sách đăng ký</h3>
        </div>
        {isLoading ? (
          <div className="p-5 text-sm text-slate-600">Đang tải danh sách đăng ký...</div>
        ) : isError ? (
          <div className="p-5 text-sm text-red-700">
            Không thể tải danh sách đăng ký.
            <button className="ml-3 font-semibold underline" type="button" onClick={() => refetch()}>
              Tải lại
            </button>
          </div>
        ) : registrations.length ? (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-5 py-3 font-semibold">Sinh viên</th>
                  <th className="px-5 py-3 font-semibold">Email</th>
                  <th className="px-5 py-3 font-semibold">Thời gian đăng ký</th>
                  <th className="px-5 py-3 font-semibold">Trạng thái</th>
                  <th className="px-5 py-3 font-semibold">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {registrations.map((booking) => (
                  <tr key={booking.id}>
                    <td className="px-5 py-3 font-medium text-slate-950">{booking.studentName || 'Chưa cập nhật'}</td>
                    <td className="px-5 py-3 text-slate-600">{booking.studentEmail || 'Chưa cập nhật'}</td>
                    <td className="px-5 py-3 text-slate-600">{formatDateTime(booking.createdAt)}</td>
                    <td className="px-5 py-3 text-slate-700">{getBookingStatusLabel(booking.status)}</td>
                    <td className="px-5 py-3">
                      {booking.status === 'PENDING_APPROVAL' ? (
                        <div className="flex gap-2">
                          <button
                            type="button"
                            className="rounded-md bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white"
                            disabled={reviewBooking.isPending}
                            onClick={() => handleReview(booking.id, 'APPROVE')}
                          >
                            Phê duyệt
                          </button>
                          <button
                            type="button"
                            className="rounded-md bg-red-600 px-3 py-1.5 text-xs font-semibold text-white"
                            disabled={reviewBooking.isPending}
                            onClick={() => handleReview(booking.id, 'REJECT')}
                          >
                            Từ chối
                          </button>
                        </div>
                      ) : (
                        <span className="text-slate-500">Không có thao tác</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="p-5 text-sm text-slate-600">Chưa có sinh viên đăng ký khung giờ này.</div>
        )}
      </div>

      {managedLabId ? (
        <CancelSlotModal
          labId={managedLabId}
          slotId={cancelSlotId}
          isOpen={Boolean(cancelSlotId)}
          onClose={() => setCancelSlotId(null)}
        />
      ) : null}
    </section>
  );
}
