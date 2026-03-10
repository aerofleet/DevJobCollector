import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

const resumeClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const saveResume = async (resumeData) => {
  const response = await resumeClient.post('/resume', resumeData);
  return response.data;
};

export const getResume = async (userId) => {
  const response = await resumeClient.get(`/resume/${userId}`);
  return response.data;
};
