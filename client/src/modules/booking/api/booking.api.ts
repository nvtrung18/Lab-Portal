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

export interface CheckInResponse {
  booking: BookingResponse;
}

export interface CheckinQrResponse {
  requestId: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'USED';
  token?: string | null;
  expiresAt: string;
  message: string;
}

export interface CheckinQrRequestResponse {
  requestId: string;
  bookingId: number;
  studentId: number;
  studentName?: string | null;
  fallbackReason: FaceFallbackReason;
  reason: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  expiresAt: string;
}

export interface CheckinQrHistoryResponse {
  requestId: string;
  bookingId: number;
  fallbackReason: FaceFallbackReason;
  reason: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'USED';
  token?: string | null;
  expiresAt: string;
}

export type FaceFallbackReason = 'FACE_DISABLED' | 'FACE_SERVICE_UNAVAILABLE' | 'FACE_PROFILE_UNAVAILABLE' | 'OTHER';

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
    `/api/slots/${slotId}/bookings`,
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

export async function createCheckinQr(bookingId: number, fallbackReason: FaceFallbackReason, customReason?: string): Promise<CheckinQrResponse> {
  const response = await apiClient.post<Response<CheckinQrResponse>>('/api/checkin/qr-requests', { bookingId, fallbackReason, customReason });
  return response.data.data;
}

export async function getMyCheckinQrRequest(bookingId: number): Promise<CheckinQrResponse> {
  const response = await apiClient.get<Response<CheckinQrResponse>>('/api/checkin/qr-requests/me', { params: { bookingId } });
  return response.data.data;
}

export async function getMyCheckinQrHistory(): Promise<CheckinQrHistoryResponse[]> {
  const response = await apiClient.get<Response<CheckinQrHistoryResponse[]>>('/api/checkin/qr-requests/me/history');
  return response.data.data;
}

export async function getPendingCheckinQrRequests(): Promise<CheckinQrRequestResponse[]> {
  const response = await apiClient.get<Response<CheckinQrRequestResponse[]>>('/api/checkin/qr-requests/pending');
  return response.data.data;
}

export async function reviewCheckinQrRequest(requestId: string, approved: boolean): Promise<CheckinQrRequestResponse> {
  const response = await apiClient.patch<Response<CheckinQrRequestResponse>>(`/api/checkin/qr-requests/${requestId}/review`, { approved });
  return response.data.data;
}

export async function manualCheckin(bookingId: number, reason: string): Promise<CheckInResponse> {
  const response = await apiClient.post<Response<CheckInResponse>>('/api/checkin/manual', { bookingId, reason });
  return response.data.data;
}

export async function confirmCheckinByToken(token: string): Promise<CheckInResponse> {
  const response = await apiClient.post<Response<CheckInResponse>>('/api/checkin/confirm', { token });
  return response.data.data;
}
