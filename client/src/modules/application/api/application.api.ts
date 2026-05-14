import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';

export type ApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ApplicationResponse {
  id: number;
  userId: number;
  applicantName: string | null;
  applicantEmail: string;
  labId: number;
  labName: string;
  cvUrl: string;
  status: ApplicationStatus;
  createdAt: string;
  updatedAt: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export async function getApplications(): Promise<ApplicationResponse[]> {
  const response =
    await apiClient.get<Response<PageResponse<ApplicationResponse>>>(
      '/api/applications',
    );

  return response.data.data.content;
}

export async function reviewApplication(
  appId: number,
  status: Extract<ApplicationStatus, 'APPROVED' | 'REJECTED'>,
): Promise<ApplicationResponse> {
  const response = await apiClient.put<Response<ApplicationResponse>>(
    `/api/applications/${appId}/review`,
    { status },
  );

  return response.data.data;
}
