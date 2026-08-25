import authenticatedApi from './authenticatedApi';

export const getCurrentMember = async () => {
  const response = await authenticatedApi.get('/members/me');
  return response.data;
};
