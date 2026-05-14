import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';
import type { ApplicationResponse } from '../../application/api';

export interface LabManager {
  id: number;
  email: string;
  username: string;
  fullName: string;
}

export interface LabResponse {
  id: number;
  labName: string;
  description: string | null;
  location: string;
  capacity: number;
  department: string | null;
  status: 'AVAILABLE' | 'MAINTENANCE' | 'CLOSED';
  manager: LabManager | null;
  createdAt: string;
  updatedAt: string;
}

export async function getLabs(): Promise<LabResponse[]> {
  const response = await apiClient.get<Response<LabResponse[]>>('/api/labs');
  return response.data.data;
}

export async function applyForLab(
  labId: number,
  cvUrl: string,
): Promise<ApplicationResponse> {
  const response = await apiClient.post<Response<ApplicationResponse>>(
    `/api/labs/${labId}/apply`,
    { cvUrl },
  );

  return response.data.data;
}
