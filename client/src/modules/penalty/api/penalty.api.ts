import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';
import type { Complaint, CreateComplaintPayload, CreatePenaltyPayload, Penalty } from '../types';

export async function getMyPenalties(): Promise<Penalty[]> {
  const response = await apiClient.get<Response<Penalty[]>>('/api/users/me/penalties');
  return response.data.data;
}

export async function submitComplaint(payload: CreateComplaintPayload): Promise<Complaint> {
  const response = await apiClient.post<Response<Complaint>>('/api/complaints', payload);
  return response.data.data;
}

export async function createPenalty(payload: CreatePenaltyPayload): Promise<Penalty> {
  const response = await apiClient.post<Response<Penalty>>('/api/penalties', payload);
  return response.data.data;
}

export async function getSlotPenalties(slotId: number): Promise<Penalty[]> {
  const response = await apiClient.get<Response<Penalty[]>>(`/api/slots/${slotId}/penalties`);
  return response.data.data;
}
