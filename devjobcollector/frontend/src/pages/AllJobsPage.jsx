import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchJobs, searchJobs } from '../api/jobApi';
import JobCard from '../components/job/JobCard';
import LoadingSpinner from '../components/common/LoadingSpinner';
import useInfiniteScroll from '../hooks/useInfiniteScroll';
import '../styles/AllJobsPage.css';

const PAGE_SIZE = 12;

const AllJobsPage = ({ searchParams = { keyword: '' } }) => {
  const [jobs, setJobs] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [errorMessage, setErrorMessage] = useState('');
  const keyword = searchParams.keyword?.trim() || '';

  const fetchPage = useCallback((pageToLoad = 0) => {
    if (keyword) {
      return searchJobs(keyword, pageToLoad, PAGE_SIZE);
    }
    return fetchJobs(pageToLoad, PAGE_SIZE);
  }, [keyword]);

  useEffect(() => {
    const loadInitialData = async () => {
      try {
        setErrorMessage('');
        const data = await fetchPage(0);
        setJobs(data.content ?? []);
        setTotalPages(data.totalPages ?? data.page?.totalPages ?? 0);
        setTotalElements(data.totalElements ?? data.page?.totalElements ?? 0);
        setPage(1);
      } catch (error) {
        setJobs([]);
        setErrorMessage('공고를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.');
        console.error('공고 초기 로드 실패:', error);
      }
    };

    loadInitialData();
  }, [fetchPage]);

  const loadMoreData = useCallback(async () => {
    if (page >= totalPages) return false;

    try {
      const data = await fetchPage(page);
      const nextPage = page + 1;
      const maxPages = data.totalPages ?? data.page?.totalPages ?? totalPages;
      setJobs((previous) => [...previous, ...(data.content ?? [])]);
      setTotalPages(maxPages);
      setTotalElements((previous) => data.totalElements ?? data.page?.totalElements ?? previous);
      setPage(nextPage);
      return nextPage < maxPages;
    } catch (error) {
      setErrorMessage('추가 공고를 불러오지 못했습니다. 네트워크 상태를 확인해주세요.');
      console.error('공고 추가 로드 실패:', error);
      return false;
    }
  }, [fetchPage, page, totalPages]);

  const { loading } = useInfiniteScroll(loadMoreData);

  return (
    <main className="all-jobs-page">
      <div className="all-jobs-breadcrumb"><Link to="/">홈</Link><span>/</span>개발자 채용</div>
      <header className="all-jobs-header">
        <div>
          <span>DEVELOPER JOBS</span>
          <h1>{keyword ? `“${keyword}” 검색 결과` : '개발자 채용'}</h1>
        </div>
        <p>총 <strong>{totalElements}</strong>개의 공고</p>
      </header>

      <div className="all-jobs-grid">
        {jobs.map((job) => <JobCard key={job.id} job={job} />)}
      </div>

      {loading && <LoadingSpinner />}
      {errorMessage && <div className="all-jobs-message error">{errorMessage}</div>}
      {!loading && !errorMessage && jobs.length === 0 && (
        <div className="all-jobs-message">조건에 맞는 공고가 없습니다.</div>
      )}
      {!loading && !errorMessage && jobs.length > 0 && page >= totalPages && (
        <div className="all-jobs-message">모든 개발자 공고를 확인했어요.</div>
      )}
    </main>
  );
};

export default AllJobsPage;
