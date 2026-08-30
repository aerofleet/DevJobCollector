import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

const authenticatedApi = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

authenticatedApi.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

authenticatedApi.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const nextPath = `${window.location.pathname}${window.location.search}`;
      const authFailureReason = error.response?.data?.error || 'ACCESS_TOKEN_INVALID';
      sessionStorage.setItem('authFailureReason', authFailureReason);
      localStorage.removeItem('accessToken');
      if (!window.location.pathname.startsWith('/login')) {
        sessionStorage.setItem('postLoginNextPath', nextPath);
        window.location.replace(`/login?next=${encodeURIComponent(nextPath)}`);
      }
    }
    return Promise.reject(error);
  },
);

export default authenticatedApi;
