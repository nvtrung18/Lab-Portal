import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
export const AUTH_TOKEN_KEY = 'accessToken';
export const REFRESH_TOKEN_KEY = 'refreshToken';

export function getAuthToken() {
  const tokenFromStorage = localStorage.getItem(AUTH_TOKEN_KEY);
  const tokenFromCookie = document.cookie
    .split('; ')
    .find((row) => row.startsWith(`${AUTH_TOKEN_KEY}=`))
    ?.split('=')[1];

  return tokenFromStorage ?? tokenFromCookie ?? null;
}

export function clearAuthTokens() {
  localStorage.removeItem(AUTH_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

function redirectToLogin() {
  clearAuthTokens();

  if (window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
}

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getAuthToken();

  if (token) {
    config.headers.Authorization = `Bearer ${decodeURIComponent(token)}`;
  }

  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      clearAuthTokens();

      if (window.location.pathname !== '/login') {
        redirectToLogin();
      }
    }

    return Promise.reject(error);
  },
);

export default apiClient;
