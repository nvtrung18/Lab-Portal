import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';

export interface UserProfileResponse {
  id: number;
  email: string;
  username?: string;
  fullName: string;
  phone: string | null;
  avatarUrl?: string | null;
  status?: string;
  roles: string[];
  memberships?: UserMembershipResponse[];
  managedLab?: {
    id?: number;
    name?: string;
    labName?: string;
  } | null;
  managedLabId?: number | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface UserMembershipResponse {
  id?: number;
  labId?: number;
  labName?: string;
  lab?: {
    id?: number;
    name?: string;
    labName?: string;
  };
  role?: string;
  status: string;
  joinedAt?: string;
  createdAt?: string;
}

export interface UpdateProfileRequest {
  fullName: string;
  phone?: string | null;
  avatarUrl?: string | null;
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
