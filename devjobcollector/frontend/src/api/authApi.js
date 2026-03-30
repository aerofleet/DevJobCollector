import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

const authClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const loginWithPassword = async ({ identifier, password }) => {
  const response = await authClient.post('/auth/login', { identifier, password });
  return response.data;
};
