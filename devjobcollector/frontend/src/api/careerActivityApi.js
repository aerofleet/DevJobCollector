import authenticatedApi from './authenticatedApi';

export const fetchBookmarks = async () => {
  const response = await authenticatedApi.get('/members/me/bookmarks');
  return response.data;
};

export const createBookmark = async (jobPostId) => {
  const response = await authenticatedApi.post(`/members/me/bookmarks/${jobPostId}`);
  return response.data;
};

export const deleteBookmark = async (jobPostId) => {
  await authenticatedApi.delete(`/members/me/bookmarks/${jobPostId}`);
};

export const fetchRecentJobs = async () => {
  const response = await authenticatedApi.get('/members/me/recent-jobs');
  return response.data;
};

export const recordRecentJob = async (jobPostId) => {
  const response = await authenticatedApi.post(`/members/me/recent-jobs/${jobPostId}`);
  return response.data;
};

export const fetchApplications = async () => {
  const response = await authenticatedApi.get('/members/me/applications');
  return response.data;
};

export const createApplication = async (jobPostId) => {
  const response = await authenticatedApi.post(`/members/me/applications/${jobPostId}`);
  return response.data;
};

export const updateApplicationStatus = async (applicationId, status) => {
  const response = await authenticatedApi.patch(
    `/members/me/applications/${applicationId}/status`,
    { status },
  );
  return response.data;
};
