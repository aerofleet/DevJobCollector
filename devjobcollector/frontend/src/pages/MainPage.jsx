import React, { useState, useCallback, useEffect } from 'react';
import { fetchJobs } from '../api/jobApi';
import JobCard from '../components/job/JobCard';
import LoadingSpinner from '../components/common/LoadingSpinner';
import useInfiniteScroll from '../hooks/useInfiniteScroll';
import '../styles/MainPage.css';

const MainPage = () => {
  const [jobs, setJobs] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  // 초기 데이터 로드
  useEffect(() => {  
  const loadInitialData = async () => {
    try {
      const data = await fetchJobs(0, 10);
      setJobs(data.content);
      setTotalPages(data.totalPages);
      setPage(1);
    } catch (error) {
      console.error('데이터 로드 실패:', error);
    }
  };
  
  loadInitialData();
  }, []);

  // 추가 데이터 로드
  const loadMoreData = useCallback(async () => {
    if (page >= totalPages) return false;

    try {
      const data = await fetchJobs(page, 10);
      setJobs(prev => [...prev, ...data.content]);
      setPage(prev => prev + 1);
      return page + 1 < totalPages;
    } catch (error) {
      console.error('추가 데이터 로드 실패:', error);
      return false;
    }
  }, [page, totalPages]);

  const { loading } = useInfiniteScroll(loadMoreData);

  return (
    <div className="main-page">
      <header className="page-header">
        <h1>개발자 채용공고</h1>
        <p>총 {jobs.length}개의 공고</p>
      </header>

      <div className="job-list">
        {jobs.map(job => (
          <JobCard key={job.id} job={job} />
        ))}
      </div>

      {loading && <LoadingSpinner />}
      
      {!loading && page >= totalPages && (
        <div className="end-message">
          모든 공고를 불러왔습니다
        </div>
      )}
    </div>
  );
};

export default MainPage;