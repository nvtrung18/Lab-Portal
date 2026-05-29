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
  location: string | null;
  capacity: number | null;
  department: string | null;
  status: 'AVAILABLE' | 'ACTIVE' | 'MAINTENANCE' | 'INACTIVE' | 'ARCHIVED' | 'CLOSED';
  manager: LabManager | null;
  applicationStatus?: 'PENDING' | 'APPROVED' | 'REJECTED' | null;
  membershipStatus?: 'ACTIVE' | 'INACTIVE' | 'REMOVED' | null;
  createdAt: string;
  updatedAt: string;
}

export interface LabMemberResponse {
  id: number;
  userId: number;
  fullName: string | null;
  email: string;
  labId: number;
  labName: string;
  role: string;
  status: string;
  joinedAt?: string;
}

export async function getLabs(): Promise<LabResponse[]> {
  const response = await apiClient.get<Response<LabResponse[]>>('/api/labs');
  return response.data.data;
}

export async function getLabById(labId: number): Promise<LabResponse> {
  const response = await apiClient.get<Response<LabResponse>>(`/api/labs/${labId}`);
  return response.data.data;
}

export async function getLabMembers(labId: number): Promise<LabMemberResponse[]> {
  const response = await apiClient.get<Response<LabMemberResponse[]>>(
    `/api/labs/${labId}/members`,
  );
  return response.data.data;
}

export async function removeLabMember(
  labId: number,
  userId: number,
): Promise<LabMemberResponse> {
  const response = await apiClient.patch<Response<LabMemberResponse>>(
    `/api/labs/${labId}/members/${userId}/remove`,
  );
  return response.data.data;
}

export async function applyForLab(
  labId: number,
  payload: { cvUrl?: string; cvFile?: File | null },
): Promise<ApplicationResponse> {
  const formData = new FormData();
  const cvUrl = payload.cvUrl?.trim();

  if (cvUrl) {
    formData.append('cvUrl', cvUrl);
  }

  if (payload.cvFile) {
    formData.append('cvFile', payload.cvFile);
  }

  const response = await apiClient.post<Response<ApplicationResponse>>(
    `/api/labs/${labId}/apply`,
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    },
  );

  return response.data.data;
}

export interface LabStudentAttendance {
  studentId: number;
  studentName: string;
  attendanceCount: number;
  expectedAttendanceCount: number;
  attendanceRate: number;
}

export interface LabDashboardStats {
  labId: number;
  memberCount: number;
  todaySlots: number;
  todayBookings: number;
  attendanceRate: number;
  attendanceByStudent: LabStudentAttendance[];
  pendingCleaningTasks: number;
  pendingComplaints: number;
  activeResearchProjects: number;
}

export async function getLabDashboardStats(labId: number): Promise<LabDashboardStats> {
  const response = await apiClient.get<Response<LabDashboardStats>>(
    `/api/labs/${labId}/dashboard/stats`,
  );
  return response.data.data;
}

