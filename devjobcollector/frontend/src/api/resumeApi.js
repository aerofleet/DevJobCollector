import authenticatedApi from './authenticatedApi';

export const updateResumeVisibility = async (resumeId, visibility) => {
  const response = await authenticatedApi.patch(`/members/me/resumes/${resumeId}/visibility`, { visibility });
  return response.data;
};

export const listResumes = async () => {
  const response = await authenticatedApi.get('/members/me/resumes');
  return response.data;
};

export const createResume = async (resumeData) => {
  const response = await authenticatedApi.post('/members/me/resumes', resumeData);
  return response.data;
};

export const getResume = async (resumeId) => {
  const response = await authenticatedApi.get(`/members/me/resumes/${resumeId}`);
  return response.data;
};

export const updateResume = async (resumeId, resumeData) => {
  const response = await authenticatedApi.put(`/members/me/resumes/${resumeId}`, resumeData);
  return response.data;
};

export const deleteResume = async (resumeId) => {
  await authenticatedApi.delete(`/members/me/resumes/${resumeId}`);
};
