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
  managedLabId?: number | null;
  managedLabName?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface AdminDashboardStats {
  users: {
    total: number;
    active: number;
    banned: number;
    pendingVerification: number;
    students: number;
    managers: number;
    unassignedManagers: number;
  };
  labs: {
    total: number;
    active: number;
    inactive: number;
    withoutManager: number;
  };
  operations: {
    pendingApplications: number;
    todaySlots: number;
    todayBookings: number;
    pendingComplaints: number;
    pendingCleaningTasks: number;
  };
  research: {
    activeProjects: number;
    activeGroups: number;
    pendingReports: number;
    submittedProducts: number;
    averageEvaluationScore: number | null;
  };
}

export interface SystemConfig {
  account: {
    requireEmailVerification: boolean;
    defaultRegisterRole: 'STUDENT';
    maxLoginAttempts: number;
  };
  lab: {
    oneManagerOneLab: boolean;
    hideInactiveLabsFromStudent: boolean;
    disableApplyForInactiveLab: boolean;
    disableBookingForInactiveLab: boolean;
  };
  booking: {
    checkinWindowMinutes: number;
    cancelBeforeMinutes: number;
    hidePastSlots: boolean;
    hideCancelledSlots: boolean;
  };
  upload: {
    reportMaxSizeMb: number;
    productMaxSizeMb: number;
    reportAllowedTypes: string[];
    productAllowedTypes: string[];
  };
  research: {
    evaluationMaxScore: number;
    requireApprovedReportBeforeTaskDone: boolean;
    requireLeaderReviewBeforeManagerReview: boolean;
    allowMemberPersonalProductUpload: boolean;
  };
}

export async function getAdminDashboardStats(): Promise<AdminDashboardStats> {
  const response = await apiClient.get<Response<AdminDashboardStats>>('/api/admin/dashboard/stats');
  return response.data.data;
}

export async function getSystemConfig(): Promise<SystemConfig> {
  const response = await apiClient.get<Response<SystemConfig>>('/api/admin/system-config');
  return response.data.data;
}

export async function updateSystemConfig(config: SystemConfig): Promise<SystemConfig> {
  const response = await apiClient.put<Response<SystemConfig>>('/api/admin/system-config', config);
  return response.data.data;
}

export async function getAdminUsers(): Promise<AdminUser[]> {
  const response = await apiClient.get<Response<AdminUser[]>>('/api/admin/users');
  return response.data.data;
}

export async function updateUserRoles(userId: number, roles: string[], managedLabId?: number): Promise<AdminUser> {
  const response = await apiClient.put<Response<AdminUser>>(
    `/api/admin/users/${userId}/roles`,
    { roles, managedLabId },
  );
  return response.data.data;
}

export interface AssignableLab {
  id: number;
  name: string;
  department: string | null;
  status: string;
  managerId: number | null;
  managerName: string | null;
}

export async function getAssignableLabs(keyword?: string, includeInactive?: boolean): Promise<AssignableLab[]> {
  const response = await apiClient.get<Response<AssignableLab[]>>('/api/admin/labs/assignable', {
    params: { keyword, includeInactive },
  });
  return response.data.data;
}

export async function patchUserRole(userId: number, role: string, labId?: number): Promise<any> {
  const response = await apiClient.patch<any>(
    `/api/admin/users/${userId}/role`,
    { role, labId },
  );
  return response.data;
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

export interface AssignableManager {
  id: number;
  fullName: string | null;
  email: string;
  role: string;
  managedLabId: number | null;
}

export async function getAssignableManagers(): Promise<AssignableManager[]> {
  const response = await apiClient.get<Response<AssignableManager[]>>('/api/admin/users/assignable-managers');
  return response.data.data;
}

export async function assignLabManager(labId: number, managerId: number): Promise<LabResponse> {
  const response = await apiClient.patch<Response<LabResponse>>(
    `/api/admin/labs/${labId}/manager`,
    { managerUserId: managerId },
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

export interface AuditLogFilters {
  actorId?: number;
  action?: string;
  module?: string;
  fromDate?: string;
  toDate?: string;
  keyword?: string;
}

export interface AuditLogResponseItem {
  id: number;
  actorId: number;
  actorName: string;
  actorRole: string;
  action: string;
  module: string;
  targetType: string;
  targetId: number | null;
  description: string;
  metadataJson: string | null;
  createdAt: string;
}

export interface AuditLogPageResponse {
  items: AuditLogResponseItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export async function getAdminAuditLogs(
  page: number,
  size: number,
  filters: AuditLogFilters
): Promise<AuditLogPageResponse> {
  const params: Record<string, any> = {
    page,
    size,
    ...filters,
  };

  // Remove empty or undefined query parameters
  Object.keys(params).forEach((key) => {
    if (params[key] === undefined || params[key] === null || params[key] === '') {
      delete params[key];
    }
  });

  const response = await apiClient.get<Response<AuditLogPageResponse>>('/api/admin/audit-logs', {
    params,
  });
  return response.data.data;
}

