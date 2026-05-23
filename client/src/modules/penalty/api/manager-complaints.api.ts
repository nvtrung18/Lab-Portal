import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';
import type { Complaint } from '../types';

export interface ReviewComplaintPayload {
  complaintId: number;
  decision: 'APPROVE' | 'REJECT';
  note?: string;
}

export async function getManagerComplaints(labId: number): Promise<Complaint[]> {
  const response = await apiClient.get<Response<Complaint[]>>(`/api/labs/${labId}/complaints`);
  return response.data.data;
}

export async function reviewComplaint(payload: ReviewComplaintPayload): Promise<Complaint> {
  const response = await apiClient.patch<Response<Complaint>>(
    `/api/complaints/${payload.complaintId}/review`,
    {
      decision: payload.decision,
      note: payload.note,
    },
  );
  return response.data.data;
}
