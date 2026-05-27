import { apiClient } from '../../../shared/api';
import { ADMIN, LAB_MANAGER, STUDENT, type Role } from '../../../shared/constants/roles';
import type { Response } from '../../../shared/types';
import { jwtDecode } from 'jwt-decode';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
  verificationToken: string;
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
  role?: string;
  roles?: string[];
}

export interface AuthEmailResponse {
  email: string;
  message: string;
}

export type RegisterResponse = AuthEmailResponse;

export interface RegisterVerifyCodeResponse {
  email: string;
  verificationToken: string;
  message: string;
}

export interface PasswordResetVerifyResponse {
  resetToken: string;
  message: string;
}

export interface VerifyRegisterRequest {
  email: string;
  code: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  email: string;
  resetToken: string;
  newPassword: string;
}

export interface ResendCodeRequest {
  email: string;
}

interface JwtPayload {
  role?: string;
  roles?: string | string[];
  authorities?: string[];
}

export interface LoginResult {
  token: string;
  refreshToken?: string;
  role: Role;
  raw: LoginResponse;
  roleSource: 'body' | 'jwt';
}

function normalizeRole(role: string): Role {
  const normalized = role.replace(/^ROLE_/, '').toUpperCase();

  if (normalized === ADMIN) {
    return ADMIN;
  }

  if (normalized === LAB_MANAGER || normalized === 'MANAGER') {
    return LAB_MANAGER;
  }

  return STUDENT;
}

function extractRoleFromBody(data: LoginResponse): string | null {
  if (data.role) {
    return data.role;
  }

  if (data.roles?.length) {
    return data.roles[0];
  }

  return null;
}

function extractRoleFromToken(token: string): string {
  const payload = jwtDecode<JwtPayload>(token);
  const tokenRoles = payload.role ?? payload.roles ?? payload.authorities;

  if (Array.isArray(tokenRoles)) {
    return tokenRoles[0] ?? 'USER';
  }

  if (typeof tokenRoles === 'string') {
    return tokenRoles.split(',')[0] ?? 'USER';
  }

  return 'USER';
}

export async function loginAPI(data: LoginRequest): Promise<LoginResult> {
  const response = await apiClient.post<Response<LoginResponse>>(
    '/api/auth/login',
    data,
  );

  const auth = response.data.data;
  const token = auth.accessToken ?? auth.token;

  if (!token) {
    throw new Error('Login response does not contain token');
  }

  const bodyRole = extractRoleFromBody(auth);

  if (bodyRole) {
    const role = normalizeRole(bodyRole);
    console.log('[Auth] Role extracted from API body:', bodyRole, '=>', role);
    return {
      token,
      refreshToken: auth.refreshToken,
      role,
      raw: auth,
      roleSource: 'body',
    };
  }

  const tokenRole = extractRoleFromToken(token);
  const role = normalizeRole(tokenRole);
  console.log('[Auth] Role extracted from JWT:', tokenRole, '=>', role);

  return {
    token,
    refreshToken: auth.refreshToken,
    role,
    raw: auth,
    roleSource: 'jwt',
  };
}

export async function sendRegisterCodeAPI(data: ResendCodeRequest): Promise<AuthEmailResponse> {
  const response = await apiClient.post<Response<AuthEmailResponse>>(
    '/api/auth/register/send-code',
    data,
  );
  return response.data.data;
}

export async function verifyRegisterCodeAPI(data: VerifyRegisterRequest): Promise<RegisterVerifyCodeResponse> {
  const response = await apiClient.post<Response<RegisterVerifyCodeResponse>>(
    '/api/auth/register/verify-code',
    data,
  );
  return response.data.data;
}

export async function registerAPI(data: RegisterRequest): Promise<AuthEmailResponse> {
  const response = await apiClient.post<Response<AuthEmailResponse>>(
    '/api/auth/register',
    data,
  );
  return response.data.data;
}

export async function sendForgotPasswordCodeAPI(data: ForgotPasswordRequest): Promise<AuthEmailResponse> {
  const response = await apiClient.post<Response<AuthEmailResponse>>('/api/auth/forgot-password/send-code', data);
  return response.data.data;
}

export async function verifyForgotPasswordCodeAPI(data: VerifyRegisterRequest): Promise<PasswordResetVerifyResponse> {
  const response = await apiClient.post<Response<PasswordResetVerifyResponse>>(
    '/api/auth/forgot-password/verify-code',
    data,
  );
  return response.data.data;
}

export async function resetPasswordAPI(data: ResetPasswordRequest): Promise<void> {
  await apiClient.post<Response<void>>('/api/auth/reset-password', data);
}
