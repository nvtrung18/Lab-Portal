import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';
import type { LabResponse } from '../../lab/api';

export interface AdminUser {
  id: number;
  email: string;
  username?: string;
  fullName: string | null;
  phone?: string | null;
  status: string;
  roles: string[];
  createdAt?: string;
  updatedAt?: string;
}

export async function getAdminUsers(): Promise<AdminUser[]> {
  const response = await apiClient.get<Response<AdminUser[]>>('/api/admin/users');
  return response.data.data;
}

export async function updateUserRoles(userId: number, roles: string[]): Promise<AdminUser> {
  const response = await apiClient.put<Response<AdminUser>>(
    `/api/admin/users/${userId}/roles`,
    { roles },
  );
  return response.data.data;
}

export async function banUser(userId: number): Promise<AdminUser> {
  const response = await apiClient.put<Response<AdminUser>>(`/api/admin/users/${userId}/ban`);
  return response.data.data;
}

export async function unbanUser(userId: number): Promise<AdminUser> {
  const response = await apiClient.put<Response<AdminUser>>(`/api/admin/users/${userId}/unban`);
  return response.data.data;
}

export async function getAdminLabs(): Promise<LabResponse[]> {
  const response = await apiClient.get<Response<LabResponse[]>>('/api/labs');
  return response.data.data;
}

export interface CreateLabRequest {
  labName: string;
  department?: string | null;
  description?: string | null;
  capacity: number;
  location: string;
}

export async function createLab(data: CreateLabRequest): Promise<LabResponse> {
  const response = await apiClient.post<Response<LabResponse>>('/api/labs', data);
  return response.data.data;
}

export async function assignLabManager(labId: number, managerId: number): Promise<LabResponse> {
  const response = await apiClient.put<Response<LabResponse>>(
    `/api/labs/${labId}/manager`,
    null,
    { params: { managerId } },
  );
  return response.data.data;
}

export async function updateLabStatus(
  labId: number,
  status: 'AVAILABLE' | 'INACTIVE',
): Promise<LabResponse> {
  const response = await apiClient.patch<Response<LabResponse>>(
    `/api/labs/${labId}/status`,
    { status },
  );
  return response.data.data;
}
