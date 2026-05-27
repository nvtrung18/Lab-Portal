import axios from 'axios';

import { toast, TOAST_MESSAGES } from '../components/toast';
import { queryClient } from './queryClient';

export const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
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

apiClient.interceptors.request.use((config) => {
  const token = getAuthToken();

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

function getApiErrorMessage(error: unknown) {
  if (!axios.isAxiosError(error)) {
    return null;
  }

  const data = error.response?.data as { message?: string; errors?: string[] } | undefined;
  return data?.message ?? data?.errors?.[0] ?? null;
}

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;

    if (status === 401 && (getAuthToken() || getStoredUser())) {
      clearAuthTokens();
      queryClient.clear();
      toast.warning('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');

      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    } else if (status === 403) {
      toast.error(TOAST_MESSAGES.permission);
    } else if (status === 400) {
      toast.error(getApiErrorMessage(error) ?? 'Dữ liệu yêu cầu không hợp lệ.');
    } else if (!error.response) {
      toast.error(TOAST_MESSAGES.network);
    } else if (status !== undefined && status >= 500) {
      toast.error(TOAST_MESSAGES.error);
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
