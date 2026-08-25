import authenticatedApi from './authenticatedApi';

export const saveResume = async (resumeData) => {
  const response = await authenticatedApi.post('/resume', resumeData);
  return response.data;
};

export const getResume = async (userId) => {
  const response = await authenticatedApi.get(`/resume/${userId}`);
  return response.data;
};
