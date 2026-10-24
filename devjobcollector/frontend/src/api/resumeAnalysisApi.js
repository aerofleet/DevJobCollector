import authenticatedApi from './authenticatedApi';

const base = '/ai/resume-analyses';
export const getAnalysisCapabilities = async () => (await authenticatedApi.get(`${base}/capabilities`)).data;
export const listAnalyses = async () => (await authenticatedApi.get(base)).data;
export const getAnalysis = async (id) => (await authenticatedApi.get(`${base}/${id}`)).data;
export const getCurrentRecommendations = async (id) => (await authenticatedApi.get(`${base}/${id}/recommendations`)).data;
export const getHomeRecommendations = async () => (await authenticatedApi.get(`${base}/home-recommendations`)).data;
export const saveCurrentRecommendationFeedback = async (id, jobId, type) => authenticatedApi.put(`${base}/${id}/recommendations/feedback`, { jobId, type });
export const createAnalysis = async (body, key) => (await authenticatedApi.post(base, body, {
  headers: { 'Idempotency-Key': key },
  timeout: 80000,
})).data;
export const deleteAnalysis = async (id) => authenticatedApi.delete(`${base}/${id}`);
export const saveAnalysisFeedback = async (id, jobId, type) => authenticatedApi.put(`${base}/${id}/feedback`, { jobId, type });
export const getExplanation = async (id) => (await authenticatedApi.get(`${base}/${id}/explanation`)).data;
export const requestExplanation = async (id, consentVersion) => (await authenticatedApi.post(`${base}/${id}/explanation`, { consentVersion }, { timeout: 35000 })).data;

export const saveFindingFeedback = async (id, findingId, type) => authenticatedApi.put(`${base}/${id}/findings/${findingId}/feedback`, { type });

export const previewContentReview = async (id) => (await authenticatedApi.post(`${base}/${id}/content-review/preview`, {}, { timeout: 80000 })).data;
export const getContentReview = async (id) => (await authenticatedApi.get(`${base}/${id}/content-review`)).data;
export const confirmContentReview = async (id, findingId, option) => (await authenticatedApi.put(`${base}/${id}/content-review/confirm`, { findingId, option })).data;
export const requestContentReview = async (id, consentVersion, previewHash) => (await authenticatedApi.post(`${base}/${id}/content-review`, { consentVersion, previewHash }, { timeout: 130000 })).data;
