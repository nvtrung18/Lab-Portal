import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';

export interface BookingResponse {
  id: number;
  userId: number;
  studentName?: string | null;
  studentEmail?: string | null;
  labId: number;
  labName?: string | null;
  slotId: number;
  startTime: string;
  endTime: string;
  status: string;
  purpose?: string | null;
  participantsCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface ReviewBookingPayload {
  bookingId: number;
  decision: 'APPROVE' | 'REJECT';
  note?: string;
}

export async function createBooking(slotId: number): Promise<BookingResponse> {
  const response = await apiClient.post<Response<BookingResponse>>('/api/bookings', { slotId });
  return response.data.data;
}

export async function cancelBooking(bookingId: number): Promise<BookingResponse> {
  const response = await apiClient.patch<Response<BookingResponse>>(
    `/api/bookings/${bookingId}/cancel`,
    { reason: 'Sinh viên hủy đăng ký' },
  );
  return response.data.data;
}

export async function getMyBookings(): Promise<BookingResponse[]> {
  const response = await apiClient.get<Response<BookingResponse[]>>('/api/bookings/me');
  return response.data.data;
}

export async function getSlotRegistrations(slotId: number): Promise<BookingResponse[]> {
  const response = await apiClient.get<Response<BookingResponse[]>>(
    `/api/slots/${slotId}/registrations`,
  );
  return response.data.data;
}

export async function reviewBooking(payload: ReviewBookingPayload): Promise<BookingResponse> {
  const response = await apiClient.patch<Response<BookingResponse>>(
    `/api/bookings/${payload.bookingId}/review`,
    {
      decision: payload.decision,
      note: payload.note,
    },
  );
  return response.data.data;
}
