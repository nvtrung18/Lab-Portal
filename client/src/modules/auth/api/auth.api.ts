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

export type RegisterResponse = LoginResponse;

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

function buildUsernameFromEmail(email: string) {
  const [localPart] = email.trim().toLowerCase().split('@');
  const normalized = localPart.replace(/[^a-z0-9._-]/g, '') || 'student';
  const suffix = Date.now().toString().slice(-6);
  return `${normalized.slice(0, 40)}${suffix}`;
}

export async function registerAPI(data: RegisterRequest): Promise<RegisterResponse> {
  const response = await apiClient.post<Response<RegisterResponse>>(
    '/api/auth/register',
    {
      fullName: data.fullName,
      email: data.email,
      username: buildUsernameFromEmail(data.email),
      password: data.password,
    },
  );

  return response.data.data;
}
