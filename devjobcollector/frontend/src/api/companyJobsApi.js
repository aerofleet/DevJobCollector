import authenticatedApi from './authenticatedApi';

const base = (companyId) => `/companies/${companyId}/jobs`;
export const companyJobsApi = {
  list: async (companyId, page = 0) => (await authenticatedApi.get(base(companyId), { params: { page } })).data,
  create: async (companyId, content) => (await authenticatedApi.post(base(companyId), content)).data,
  edit: async (companyId, id, expectedVersion, content) => (await authenticatedApi.put(`${base(companyId)}/${id}`, { expectedVersion, content })).data,
  action: async (companyId, job, action) => (await authenticatedApi.post(`${base(companyId)}/${job.id}/${action}`, { expectedVersion: job.version })).data,
};
