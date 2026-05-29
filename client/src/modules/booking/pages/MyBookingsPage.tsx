import { EmptyState, ErrorState, LoadingState } from '../../../shared/components';
import { CheckinButton } from '../components';
import { useCancelBooking, useMyBookings } from '../hooks';
import { getBookingStatusLabel, isCancellableBooking } from '../utils';

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

export function MyBookingsPage() {
  const { data: bookings = [], isLoading, isError, refetch } = useMyBookings();
  const cancelBooking = useCancelBooking();

  if (isLoading) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <LoadingState />
      </section>
    );
  }

  if (isError) {
    return (
      <section className="rounded-lg border border-red-200 bg-white p-6 shadow-sm">
        <ErrorState onRetry={() => refetch()} />
      </section>
    );
  }

  return (
    <section className="space-y-4">
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold text-slate-950">Đăng ký sử dụng PTN của tôi</h2>
        <p className="mt-2 text-sm text-slate-600">
          Theo dõi trạng thái phê duyệt và tạo mã QR check-in trong 10 phút đầu của ca sử dụng.
        </p>
      </div>

      {!bookings.length ? (
        <EmptyState>
          Bạn chưa có đăng ký sử dụng PTN nào.
        </EmptyState>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {bookings.map((booking) => {
            const canCancel = isCancellableBooking(booking.status, booking.startTime);
            return (
              <article key={booking.id} className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <h3 className="text-base font-semibold text-slate-950">{booking.labName ?? 'PTN'}</h3>
                    <p className="mt-1 text-sm text-slate-600">
                      {formatDateTime(booking.startTime)} - {formatDateTime(booking.endTime)}
                    </p>
                  </div>
                  <span className="w-fit rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">
                    {getBookingStatusLabel(booking.status)}
                  </span>
                </div>

                {booking.purpose ? (
                  <p className="mt-4 text-sm text-slate-600">Mục đích: {booking.purpose}</p>
                ) : null}

                <div className="mt-5 grid gap-2 sm:grid-cols-2">
                  <CheckinButton booking={booking} />
                  {canCancel ? (
                    <button
                      className="w-full rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700 disabled:opacity-60"
                      disabled={cancelBooking.isPending}
                      type="button"
                      onClick={() => {
                        if (window.confirm('Bạn có chắc muốn hủy đăng ký sử dụng khung giờ này không?')) {
                          cancelBooking.mutate(booking.id);
                        }
                      }}
                    >
                      Hủy đăng ký
                    </button>
                  ) : null}
                </div>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}
