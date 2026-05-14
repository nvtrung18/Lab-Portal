import { apiClient } from '../../../shared/api';
import type { Response } from '../../../shared/types';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token?: string;
  accessToken?: string;
  refreshToken?: string;
  tokenType?: string;
  expiresIn?: number;
  userId?: number;
  username?: string;
  email?: string;
  roles?: string[];
}

export async function loginAPI(data: LoginRequest): Promise<LoginResponse> {
  const response = await apiClient.post<Response<LoginResponse>>(
    '/api/auth/login',
    data,
  );

  return response.data.data;
}
