import axios from 'axios';
import { trackGrowth } from '../utils/growthAnalytics';

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
  trackGrowth('LOGIN');
  return response.data;
};

export const signupPersonal = async (payload) => {
  const response = await authClient.post('/auth/signup/personal', payload);
  return response.data;
};

export const verifyPersonalEmail = async ({ email, code }) => {
  const response = await authClient.post('/auth/signup/personal/verify-email', { email, code });
  trackGrowth('SIGNUP');
  return response.data;
};

export const resendPersonalVerification = async ({ email, turnstileToken }) => {
  const response = await authClient.post('/auth/signup/personal/resend', { email, turnstileToken });
  return response.data;
};
