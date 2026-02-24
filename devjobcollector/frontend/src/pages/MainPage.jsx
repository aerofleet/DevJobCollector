import React, { useState, useCallback, useEffect } from 'react';
import { fetchJobs, searchJobs } from '../api/jobApi';
import JobCard from '../components/job/JobCard';
import LoadingSpinner from '../components/common/LoadingSpinner';
import useInfiniteScroll from '../hooks/useInfiniteScroll';
import '../styles/MainPage.css';

const MainPage = ({ searchParams = { keyword: '' } }) => {
  const [jobs, setJobs] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const keyword = searchParams.keyword?.trim() || '';

  // 공통 데이터 요청 함수
  const fetchPage = useCallback(async (pageToLoad = 0) => {
    if (keyword) {
      return searchJobs(keyword, pageToLoad, 10);
    }
    return fetchJobs(pageToLoad, 10);
  }, [keyword]);

  // 초기/검색 데이터 로드
  useEffect(() => {  
    const loadInitialData = async () => {
      try {
        const data = await fetchPage(0);
        setJobs(data.content);
        setTotalPages(data.totalPages ?? data.page?.totalPages ?? 0);
        setTotalElements(data.totalElements ?? data.page?.totalElements ?? 0);
        setPage(1);
      } catch (error) {
        console.error('데이터 로드 실패:', error);
      }
    };

    loadInitialData();
  }, [fetchPage]);

  // 추가 데이터 로드
  const loadMoreData = useCallback(async () => {
    if (page >= totalPages) return false;

    try {
      const data = await fetchPage(page);
      setJobs(prev => [...prev, ...data.content]);
      setTotalPages(data.totalPages ?? data.page?.totalPages ?? totalPages);
      setTotalElements(prev => data.totalElements ?? data.page?.totalElements ?? prev);
      const nextPage = page + 1;
      setPage(nextPage);
      const maxPages = data.totalPages ?? data.page?.totalPages ?? totalPages;
      return nextPage < maxPages;
    } catch (error) {
      console.error('추가 데이터 로드 실패:', error);
      return false;
    }
  }, [page, totalPages, fetchPage]);

  const { loading } = useInfiniteScroll(loadMoreData);

  return (
    <div className="main-page">
      <header className="page-header">
        <h1>개발자 채용공고</h1>
        <p>
          {keyword
            ? `"${keyword}" 검색 결과: ${totalElements}개`
            : `총 ${totalElements}개의 공고`}
        </p>
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
