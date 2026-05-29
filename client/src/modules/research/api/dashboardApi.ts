import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';
import { adaptProjectDashboardStats } from '../adapters/dashboardAdapter';
import type { DashboardStats, RawProjectDashboardStats } from '../types/dashboard';

export async function getProjectDashboardStats(projectId: number): Promise<DashboardStats> {
  const response = await apiClient.get<Response<RawProjectDashboardStats>>(`/api/projects/${projectId}/stats`, {
    params: { type: 'overview' },
  });

  return adaptProjectDashboardStats(response.data.data);
}
