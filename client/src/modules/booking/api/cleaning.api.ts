import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';

export interface CleaningTask {
  id?: number | null;
  slotId: number;
  labId: number;
  labName?: string | null;
  staffId?: number | null;
  staffName?: string | null;
  staffEmail?: string | null;
  assignedToId?: number | null;
  assignedToName?: string | null;
  startTime: string;
  endTime: string;
  slotStatus?: string | null;
  participantCount?: number | null;
  status: string;
  assignedAt?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt?: string | null;
}

export interface EligibleCleaner {
  userId: number;
  fullName?: string | null;
  email: string;
  bookingId: number;
  bookingStatus: string;
  checkedIn: boolean;
}

export interface AssignCleaningPayload {
  slotId: number;
  assigneeIds: number[];
}

export async function getLabCleaningTasks(labId: number): Promise<CleaningTask[]> {
  const response = await apiClient.get<Response<CleaningTask[]>>(
    `/api/labs/${labId}/cleaning-tasks`,
  );
  return response.data.data;
}

export async function getEligibleCleaners(slotId: number): Promise<EligibleCleaner[]> {
  const response = await apiClient.get<Response<EligibleCleaner[]>>(
    `/api/slots/${slotId}/eligible-cleaners`,
  );
  return response.data.data;
}

export async function assignCleaningTasks(payload: AssignCleaningPayload): Promise<CleaningTask[]> {
  const response = await apiClient.post<Response<CleaningTask[]>>('/api/cleaning-tasks', payload);
  return response.data.data;
}

export async function getMyCleaningTasks(): Promise<CleaningTask[]> {
  const response = await apiClient.get<Response<CleaningTask[]>>('/api/cleaning/pending');
  return response.data.data;
}

export async function completeCleaningTask(taskId: number): Promise<CleaningTask> {
  const response = await apiClient.post<Response<CleaningTask>>('/api/cleaning/confirm', {
    taskId,
    cleaningId: taskId,
  });
  return response.data.data;
}

export async function cancelCleaningTask(taskId: number): Promise<CleaningTask> {
  const response = await apiClient.patch<Response<CleaningTask>>(
    `/api/cleaning-tasks/${taskId}/cancel`,
  );
  return response.data.data;
}
