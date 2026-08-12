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

// 랜딩 페이지용 무작위 공고 조회
// 전체 건수를 확인한 뒤 임의 구간을 가져와 섞으므로 DB 랜덤 정렬 부하를 만들지 않는다.
const DEVELOPER_SEARCH_KEYWORDS = [
  '개발', 'Engineer', 'Software', 'Backend', 'Frontend', 'DevOps', '데이터',
];

const DEVELOPER_TITLE_PATTERN = /개발|엔지니어|소프트웨어|백엔드|프론트엔드|풀스택|서버|데이터|머신러닝|인공지능|\b(?:software|engineer|developer|backend|frontend|full[ -]?stack|devops|sre|qa|data|machine learning|ai)\b/i;

const isDeveloperJob = (job) => {
  const techStackText = (job.techStacks ?? [])
    .map((techStack) => techStack.name ?? techStack.stackName ?? '')
    .join(' ');
  return DEVELOPER_TITLE_PATTERN.test(`${job.title ?? ''} ${techStackText}`);
};

export const fetchRandomDeveloperJobs = async (count = 8) => {
  const searchResults = await Promise.allSettled(
    DEVELOPER_SEARCH_KEYWORDS.map((keyword) => searchJobs(keyword, 0, 20))
  );
  const uniqueJobs = new Map();

  searchResults.forEach((result) => {
    if (result.status !== 'fulfilled') return;

    (result.value.content ?? [])
      .filter(isDeveloperJob)
      .forEach((job) => uniqueJobs.set(job.id, job));
  });

  const shuffled = [...uniqueJobs.values()];

  for (let index = shuffled.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(Math.random() * (index + 1));
    [shuffled[index], shuffled[swapIndex]] = [shuffled[swapIndex], shuffled[index]];
  }

  return {
    content: shuffled.slice(0, count),
    candidateCount: shuffled.length,
  };
};

// 채용공고 검색
export const searchJobs = async (filters = {}, page = 0, size = 10) => {
  const normalizedFilters = typeof filters === 'string' ? { keyword: filters } : filters;
  const response = await apiClient.get('/jobs/search', {
    params: {
      keyword: normalizedFilters.keyword?.trim() || '',
      location: normalizedFilters.location || undefined,
      experience: normalizedFilters.experience || undefined,
      jobCategory: normalizedFilters.jobCategory || undefined,
      techStack: normalizedFilters.techStack || undefined,
      sortBy: normalizedFilters.sortBy || 'createdAt',
      direction: normalizedFilters.direction || 'DESC',
      page,
      size,
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
