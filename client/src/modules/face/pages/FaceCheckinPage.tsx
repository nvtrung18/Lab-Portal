import axios from 'axios';
import { Camera, CheckCircle2 } from 'lucide-react';
import { ChangeEvent, FormEvent, useState } from 'react';

import { useMyBookings } from '../../booking/hooks';
import { Button, EmptyState, ErrorState } from '../../../shared/components';
import type { Response } from '../../../shared/types';
import { faceCheckin, readFaceImage } from '../api';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '../../../shared/api';

function message(error: unknown) {
  if (axios.isAxiosError(error)) return (error.response?.data as Partial<Response<unknown>> | undefined)?.message ?? 'Không thể check-in bằng khuôn mặt.';
  return error instanceof Error ? error.message : 'Không thể check-in bằng khuôn mặt.';
}

export function FaceCheckinPage() {
  const bookings = useMyBookings();
  const queryClient = useQueryClient();
  const [bookingId, setBookingId] = useState('');
  const [image, setImage] = useState<Awaited<ReturnType<typeof readFaceImage>> | null>(null);
  const [previewUrl, setPreviewUrl] = useState('');
  const [localError, setLocalError] = useState('');
  const checkin = useMutation({
    mutationFn: () => faceCheckin(Number(bookingId), image!),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.bookings.mine }),
  });
  const approvedBookings = bookings.data?.filter((booking) => booking.status === 'APPROVED') ?? [];

  const handleImage = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    try { setImage(await readFaceImage(file)); setPreviewUrl(URL.createObjectURL(file)); setLocalError(''); }
    catch (error) { setLocalError(message(error)); }
  };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (Number(bookingId) <= 0 || !image) { setLocalError('Vui lòng chọn booking đã duyệt và chụp ảnh khuôn mặt.'); return; }
    setLocalError(''); checkin.mutate();
  };

  return (
    <section className="mx-auto max-w-3xl rounded-lg border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
      <header><p className="text-sm font-semibold uppercase tracking-wide text-slate-500">Check-in chính</p><h1 className="mt-1 text-2xl font-semibold text-slate-950">Check-in bằng khuôn mặt</h1><p className="mt-2 text-sm leading-6 text-slate-600">Chọn booking đã được duyệt và chụp ảnh trực tiếp. Hệ thống vẫn kiểm tra chủ booking và khung giờ hợp lệ.</p></header>
      {bookings.isError ? <ErrorState className="mt-5" onRetry={() => void bookings.refetch()}>Không thể tải booking.</ErrorState> : bookings.isLoading ? <div className="mt-5 h-24 animate-pulse rounded bg-slate-100" /> : approvedBookings.length === 0 ? <EmptyState className="mt-5">Không có booking đã duyệt để check-in.</EmptyState> : (
        <form className="mt-6 space-y-5" onSubmit={submit}>
          <label className="block text-sm font-semibold text-slate-700" htmlFor="face-booking">Booking đã duyệt<select id="face-booking" className="mt-2 min-h-12 w-full rounded-md border border-slate-300 bg-white px-3 text-base" value={bookingId} onChange={(event) => setBookingId(event.target.value)}><option value="">Chọn booking</option>{approvedBookings.map((booking) => <option key={booking.id} value={booking.id}>{booking.labName ?? `PTN #${booking.labId}`} · {new Date(booking.startTime).toLocaleString('vi-VN')}</option>)}</select></label>
          {previewUrl ? <img alt="Ảnh check-in được chọn" className="aspect-video w-full rounded-md border border-slate-200 object-cover" src={previewUrl} /> : null}
          <label className="block text-sm font-semibold text-slate-700" htmlFor="face-checkin-image"><span className="flex items-center gap-2"><Camera aria-hidden="true" className="h-4 w-4" /> Ảnh khuôn mặt trực tiếp</span><input id="face-checkin-image" accept="image/jpeg,image/png" capture="user" className="mt-2 block w-full text-sm file:mr-3 file:min-h-11 file:rounded-md file:border-0 file:bg-slate-900 file:px-4 file:text-sm file:font-semibold file:text-white" type="file" onChange={(event) => void handleImage(event)} /></label>
          {localError || checkin.isError ? <p className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700" role="alert">{localError || message(checkin.error)}</p> : null}
          {checkin.data ? <div className={['rounded-md border p-4 text-sm', checkin.data.checkedIn ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-amber-200 bg-amber-50 text-amber-800'].join(' ')} role="status"><p className="flex items-center gap-2 font-semibold"><CheckCircle2 aria-hidden="true" className="h-5 w-5" /> {checkin.data.checkedIn ? 'Check-in thành công' : 'Chưa thể check-in'}</p><p className="mt-1">Kết quả: {checkin.data.result}</p>{checkin.data.confidenceScore !== null ? <p>Độ tin cậy: {(checkin.data.confidenceScore * 100).toFixed(1)}%</p> : null}{checkin.data.failureReason ? <p>Lý do: {checkin.data.failureReason}</p> : null}</div> : null}
          <Button className="w-full sm:w-auto" loading={checkin.isPending} loadingText="Đang xác minh..." type="submit">Xác minh và check-in</Button>
        </form>
      )}
    </section>
  );
}
