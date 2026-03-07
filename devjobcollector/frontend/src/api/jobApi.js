// src/api/jobApi.js
import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Priority': 'u=0, i', // HTTP/2 우선순위 헤더
    'Content-Type': 'application/json',
    'Connection': 'keep-alive',
  },
});

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
