import authenticatedApi from './authenticatedApi';

export const fetchMyCompanies = async () => {
  const response = await authenticatedApi.get('/companies/me');
  return response.data;
};

export const createCompany = async (company) => {
  const response = await authenticatedApi.post('/companies', company);
  return response.data;
};

export const requestCompanyVerification = async (companyId, evidenceObjectKey) => {
  const response = await authenticatedApi.post(`/companies/${companyId}/verification-requests`, {
    method: 'BUSINESS_REGISTRATION_DOCUMENT',
    evidenceObjectKey,
  });
  return response.data;
};
