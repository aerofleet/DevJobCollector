import authenticatedApi from './authenticatedApi';

export const fetchMyCompanies = async () => {
  const response = await authenticatedApi.get('/companies/me');
  return response.data;
};

export const createCompany = async (company) => {
  const response = await authenticatedApi.post('/companies', company);
  return response.data;
};

export const requestCompanyVerification = async (companyId, file) => {
  const form = new FormData();
  form.append('file', file);
  const response = await authenticatedApi.post(`/companies/${companyId}/verification-requests/document`, form, {
    headers: { 'Content-Type': undefined },
  });
  return response.data;
};
