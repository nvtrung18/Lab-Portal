import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';

export interface UserProfileResponse {
  id: number;
  email: string;
  username: string;
  fullName: string;
  phone: string | null;
  status: string;
  roles: string[];
  createdAt: string;
  updatedAt: string;
}

export interface UpdateProfileRequest {
  fullName: string;
  phone?: string | null;
}

export async function getProfile(): Promise<UserProfileResponse> {
  const response = await apiClient.get<Response<UserProfileResponse>>(
    '/api/users/me',
  );

  return response.data.data;
}

export async function updateProfile(
  data: UpdateProfileRequest,
): Promise<UserProfileResponse> {
  const response = await apiClient.put<Response<UserProfileResponse>>(
    '/api/users/me',
    data,
  );

  return response.data.data;
}
