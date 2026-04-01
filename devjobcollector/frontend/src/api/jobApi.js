// src/api/jobApi.js
import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';
const RETRYABLE_METHODS = new Set(['get', 'head', 'options']);
const RETRYABLE_STATUSES = new Set([408, 429, 500, 502, 503, 504]);

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const shouldRetry = (error) => {
  const method = error.config?.method?.toLowerCase();
  if (!RETRYABLE_METHODS.has(method)) return false;

  if (error.code === 'ECONNABORTED') return true;
  if (error.message?.includes('Network Error')) return true;

  const status = error.response?.status;
  return status ? RETRYABLE_STATUSES.has(status) : false;
};

// 요청 인터셉터
apiClient.interceptors.request.use(
  (config) => {
    config.metadata = {
      retryCount: config.metadata?.retryCount ?? 0,
    };
    return config;
  },
  (error) => Promise.reject(error)
);

// 응답 인터셉터 (에러 처리)
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config;
    const retryCount = config?.metadata?.retryCount ?? 0;

    if (config && retryCount < 2 && shouldRetry(error)) {
      config.metadata.retryCount = retryCount + 1;
      const backoff = 600 * (retryCount + 1);
      await sleep(backoff);
      return apiClient.request(config);
    }

    if (error.response) {
      console.error('API Error:', error.response.status, error.response.data);
    } else if (error.request) {
      console.error('Network Error:', error.message);
    }
    return Promise.reject(error);
  }
);

// 채용공고 목록 조회 (페이징)
export const fetchJobs = async (page = 0, size = 10) => {
  const response = await apiClient.get('/jobs', {
    params: { page, size },
  });
  return response.data;
};

// 채용공고 검색
export const searchJobs = async (keyword = '', page = 0, size = 10) => {
  const response = await apiClient.get('/jobs/search', {
    // 백엔드 @RequestParam 이름과 일치하도록 명시
    params: { 
      keyword: keyword?.trim() || '', 
      page, 
      size,
      // location, experience 필요 시 추가 전달 가능
    },
  });
  return response.data;
};

// 채용공고 상세 조회
export const fetchJobDetail = async (jobId) => {
  const response = await apiClient.get(`/jobs/${jobId}`);
  return response.data;
};

export default apiClient;
