import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';
import type { AiActionLog, AiUsageLog, FaceCheckinLog, OperationalFilters, OperationalLogPage } from '../types';

function cleanParams(filters: OperationalFilters, page: number, size: number) {
  return Object.fromEntries(Object.entries({ ...filters, page, size }).filter(([, value]) => value !== undefined && value !== ''));
}

export async function getAiUsageLogs(filters: OperationalFilters, page: number, size: number) {
  const response = await apiClient.get<Response<OperationalLogPage<AiUsageLog>>>('/api/admin/operational-logs/ai-usage', { params: cleanParams(filters, page, size) });
  return response.data.data;
}

export async function getAiActionLogs(filters: OperationalFilters, page: number, size: number) {
  const response = await apiClient.get<Response<OperationalLogPage<AiActionLog>>>('/api/admin/operational-logs/ai-actions', { params: cleanParams(filters, page, size) });
  return response.data.data;
}

export async function getFaceCheckinLogs(filters: OperationalFilters, page: number, size: number) {
  const response = await apiClient.get<Response<OperationalLogPage<FaceCheckinLog>>>('/api/admin/operational-logs/face-checkins', { params: cleanParams(filters, page, size) });
  return response.data.data;
}
