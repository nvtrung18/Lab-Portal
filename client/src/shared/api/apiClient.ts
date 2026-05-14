import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
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
}

export interface UserMembership {
  labId: number;
  labName: string;
  status: string;
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
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

apiClient.interceptors.request.use((config) => {
  const token = getAuthToken();

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  console.log('[API Request]', config.method?.toUpperCase(), config.url, {
    hasToken: Boolean(token),
  });
  return config;
});

apiClient.interceptors.response.use(
  (response) => {
    console.log('[API Response]', response.status, response.config.url);
    return response;
  },
  (error) => {
    console.error('[API Error]', error);

    if (error.response?.status === 401) {
      clearAuthTokens();

      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }

    return Promise.reject(error);
  },
);

export default apiClient;
