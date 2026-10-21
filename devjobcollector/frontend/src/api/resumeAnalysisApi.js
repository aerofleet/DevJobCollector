import authenticatedApi from './authenticatedApi';

const base = '/ai/resume-analyses';
export const getAnalysisCapabilities = async () => (await authenticatedApi.get(`${base}/capabilities`)).data;
export const listAnalyses = async () => (await authenticatedApi.get(base)).data;
export const getAnalysis = async (id) => (await authenticatedApi.get(`${base}/${id}`)).data;
export const createAnalysis = async (body, key) => (await authenticatedApi.post(base, body, {
  headers: { 'Idempotency-Key': key },
  timeout: 80000,
})).data;
export const deleteAnalysis = async (id) => authenticatedApi.delete(`${base}/${id}`);
export const saveAnalysisFeedback = async (id, jobId, type) => authenticatedApi.put(`${base}/${id}/feedback`, { jobId, type });
export const getExplanation = async (id) => (await authenticatedApi.get(`${base}/${id}/explanation`)).data;
export const requestExplanation = async (id, consentVersion) => (await authenticatedApi.post(`${base}/${id}/explanation`, { consentVersion }, { timeout: 35000 })).data;

export const saveFindingFeedback = async (id, findingId, type) => authenticatedApi.put(`${base}/${id}/findings/${findingId}/feedback`, { type });
