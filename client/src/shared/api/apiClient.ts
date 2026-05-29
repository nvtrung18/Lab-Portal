import axios from 'axios';
import type { AxiosError, InternalAxiosRequestConfig } from 'axios';

import { PENDING_TOAST_KEY, toast, TOAST_MESSAGES } from '../components/toast';
import { queryClient } from './queryClient';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';
export const AUTH_TOKEN_KEY = 'access_token';
export const REFRESH_TOKEN_KEY = 'refreshToken';
export const USER_ROLE_KEY = 'user_role';
export const USER_DATA_KEY = 'user_data';

export interface StoredUser {
  id: number;
  fullName: string | null;
  email: string;
  roles: string[];
  memberships?: UserMembership[];
  researchGroupMemberships?: UserResearchGroupMembership[];
  groupMemberships?: UserResearchGroupMembership[];
  researchGroups?: UserResearchGroupMembership[];
  managedLab?: ManagedLab | null;
  managedLabId?: number | null;
}

export interface UserMembership {
  labId: number;
  labName: string;
  role?: string;
  status: string;
  joinedAt?: string;
}

export interface ManagedLab {
  id: number;
  name: string;
}

export interface UserResearchGroupMembership {
  labId?: number;
  labName?: string;
  status?: string;
  group?: {
    labId?: number;
    labName?: string;
    lab?: {
      id?: number;
      name?: string;
      labName?: string;
    };
  };
  researchGroup?: {
    labId?: number;
    labName?: string;
    lab?: {
      id?: number;
      name?: string;
      labName?: string;
    };
  };
}

export function getAuthToken() {
  return localStorage.getItem(AUTH_TOKEN_KEY);
}

export function setAuthTokens(accessToken: string, refreshToken?: string) {
  localStorage.setItem(AUTH_TOKEN_KEY, accessToken);

  if (refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  }
}

export function setAuthSession(accessToken: string, role: string, userData?: unknown) {
  localStorage.setItem(AUTH_TOKEN_KEY, accessToken);
  localStorage.setItem(USER_ROLE_KEY, role);

  if (userData) {
    localStorage.setItem(USER_DATA_KEY, JSON.stringify(userData));
  }
}

export function setStoredUser(user: StoredUser) {
  localStorage.setItem(USER_DATA_KEY, JSON.stringify(user));
  localStorage.setItem(USER_ROLE_KEY, user.roles[0] ?? '');
}

export function clearAuthTokens() {
  localStorage.removeItem(AUTH_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_ROLE_KEY);
  localStorage.removeItem(USER_DATA_KEY);
}

export function getStoredRole() {
  return localStorage.getItem(USER_ROLE_KEY);
}

export function getStoredUser(): StoredUser | null {
  const rawUser = localStorage.getItem(USER_DATA_KEY);

  if (!rawUser) {
    return null;
  }

  try {
    return JSON.parse(rawUser) as StoredUser;
  } catch {
    return null;
  }
}

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
});

const PUBLIC_AUTH_PATHS = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/register/send-code',
  '/api/auth/register/verify-code',
  '/api/auth/forgot-password',
  '/api/auth/forgot-password/send-code',
  '/api/auth/forgot-password/verify-code',
  '/api/auth/reset-password',
];

const TOAST_DEDUPE_MS = 2500;
const toastHistory = new Map<string, number>();
let isHandlingUnauthorized = false;

function getRequestPath(config: InternalAxiosRequestConfig) {
  const rawUrl = config.url ?? '';

  try {
    return new URL(rawUrl, API_BASE_URL).pathname;
  } catch {
    return rawUrl.split('?')[0] ?? rawUrl;
  }
}

function getFullRequestUrl(config?: InternalAxiosRequestConfig) {
  const rawUrl = config?.url ?? '';

  try {
    return new URL(rawUrl, API_BASE_URL || window.location.origin).toString();
  } catch {
    return rawUrl;
  }
}

function hasAuthorizationHeader(config?: InternalAxiosRequestConfig) {
  const headers = config?.headers;

  if (!headers) {
    return false;
  }

  const authorization =
    typeof headers.get === 'function'
      ? headers.get('Authorization')
      : (headers as Record<string, unknown>).Authorization ?? (headers as Record<string, unknown>).authorization;

  return typeof authorization === 'string' && authorization.startsWith('Bearer ');
}

function shouldLogResearchGroupFailure(config?: InternalAxiosRequestConfig) {
  if (!config) {
    return false;
  }

  const path = getRequestPath(config);
  return path.includes('/research-groups/');
}

function logResearchGroupRequestFailure(error: AxiosError) {
  if (!shouldLogResearchGroupFailure(error.config)) {
    return;
  }

  const method = getRequestMethod(error.config);
  const status = error.response?.status ?? 'NETWORK_ERROR';
  const url = getFullRequestUrl(error.config);
  const role = getStoredRole() ?? 'unknown';

  console.error('[ResearchGroup API failure]', {
    method,
    url,
    status,
    role,
    hasBearerToken: hasAuthorizationHeader(error.config),
    responseBody: error.response?.data ?? null,
  });
}

function isPublicAuthEndpoint(config?: InternalAxiosRequestConfig) {
  if (!config) {
    return false;
  }

  const path = getRequestPath(config);
  return PUBLIC_AUTH_PATHS.some((publicPath) => path === publicPath || path.startsWith(`${publicPath}/`));
}

function getRequestMethod(config?: InternalAxiosRequestConfig) {
  return config?.method?.toUpperCase() ?? 'GET';
}

function isMutationRequest(config?: InternalAxiosRequestConfig) {
  return !['GET', 'HEAD', 'OPTIONS'].includes(getRequestMethod(config));
}

function showToastOnce(
  variant: 'success' | 'error' | 'warning' | 'info',
  message: string,
  key = `${variant}:${message}`,
) {
  const now = Date.now();
  const lastShownAt = toastHistory.get(key) ?? 0;

  if (now - lastShownAt < TOAST_DEDUPE_MS) {
    return;
  }

  toastHistory.set(key, now);
  toast[variant](message);
}

apiClient.interceptors.request.use((config) => {
  const token = getAuthToken();

  if (token && !isPublicAuthEndpoint(config)) {
    config.headers.Authorization = `Bearer ${token}`;
  } else {
    delete config.headers.Authorization;
  }

  return config;
});

interface ApiErrorBody {
  message?: string;
  error?: string | { message?: string };
  errors?: string[] | Record<string, string | string[]>;
}

function getApiErrorMessage(error: AxiosError | unknown) {
  if (!axios.isAxiosError(error)) {
    return null;
  }

  const data = error.response?.data as ApiErrorBody | undefined;

  if (!data) {
    return null;
  }

  if (typeof data.message === 'string' && data.message.trim()) {
    return data.message;
  }

  if (typeof data.error === 'string' && data.error.trim()) {
    return data.error;
  }

  if (typeof data.error === 'object' && typeof data.error.message === 'string' && data.error.message.trim()) {
    return data.error.message;
  }

  if (Array.isArray(data.errors)) {
    return data.errors.find(Boolean) ?? null;
  }

  if (data.errors && typeof data.errors === 'object') {
    for (const value of Object.values(data.errors)) {
      if (typeof value === 'string' && value.trim()) {
        return value;
      }

      if (Array.isArray(value)) {
        const firstError = value.find(Boolean);
        if (firstError) {
          return firstError;
        }
      }
    }
  }

  return null;
}

function redirectToLogin() {
  if (window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
}

function persistToast(variant: 'success' | 'error' | 'warning' | 'info', message: string) {
  try {
    sessionStorage.setItem(PENDING_TOAST_KEY, JSON.stringify({ variant, message }));
  } catch {
    // Ignore storage failures; the redirect itself is still the required auth recovery.
  }
}

function handleUnauthorized() {
  const hadSession = Boolean(getAuthToken() || getStoredUser());

  clearAuthTokens();
  queryClient.clear();

  if (hadSession && !isHandlingUnauthorized) {
    isHandlingUnauthorized = true;
    persistToast('warning', TOAST_MESSAGES.sessionExpired);
    showToastOnce('warning', TOAST_MESSAGES.sessionExpired, 'auth:401');
  }

  redirectToLogin();
}

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (!axios.isAxiosError(error)) {
      showToastOnce('error', TOAST_MESSAGES.error);
      return Promise.reject(error);
    }

    const status = error.response?.status;
    const method = getRequestMethod(error.config);
    const isMutation = isMutationRequest(error.config);

    logResearchGroupRequestFailure(error);

    if (!error.response) {
      showToastOnce('error', TOAST_MESSAGES.network, `network:${method}:${error.config?.url ?? ''}`);
      return Promise.reject(error);
    }

    if (status === 400) {
      showToastOnce('error', getApiErrorMessage(error) ?? 'Dữ liệu yêu cầu không hợp lệ.');
    } else if (status === 401 && !isPublicAuthEndpoint(error.config)) {
      handleUnauthorized();
    } else if (status === 401) {
      showToastOnce('error', getApiErrorMessage(error) ?? 'Thông tin đăng nhập không hợp lệ.', 'auth:public:401');
    } else if (status === 403) {
      if (isMutation) {
        showToastOnce('error', TOAST_MESSAGES.permission, 'permission:mutation');
      }
    } else if (status === 404) {
      if (isMutation) {
        showToastOnce('error', 'Không tìm thấy dữ liệu.', 'not-found:mutation');
      }
    } else if (status !== undefined && status >= 500) {
      showToastOnce('error', 'Hệ thống đang gặp sự cố. Vui lòng thử lại sau.', `server:${status}`);
    }

    return Promise.reject(error);
  },
);

export default apiClient;

export function resolveApiAssetUrl(url?: string | null) {
  if (!url) {
    return '';
  }

  if (/^https?:\/\//i.test(url)) {
    return url;
  }

  return `${API_BASE_URL.replace(/\/$/, '')}/${url.replace(/^\//, '')}`;
}
