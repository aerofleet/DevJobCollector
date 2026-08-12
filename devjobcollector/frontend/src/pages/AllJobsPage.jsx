import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Search, SlidersHorizontal, X } from 'lucide-react';
import { searchJobs } from '../api/jobApi';
import JobCard from '../components/job/JobCard';
import LoadingSpinner from '../components/common/LoadingSpinner';
import useInfiniteScroll from '../hooks/useInfiniteScroll';
import '../styles/AllJobsPage.css';

const PAGE_SIZE = 12;

const JOB_ROLES = [
  { value: '', label: '전체 직무' },
  { value: 'backend', label: '백엔드' },
  { value: 'frontend', label: '프론트엔드' },
  { value: 'fullstack', label: '풀스택' },
  { value: 'mobile', label: '모바일' },
  { value: 'data-ai', label: '데이터·AI' },
  { value: 'devops-security', label: 'DevOps·보안' },
];

const TECH_STACKS = ['', 'Java', 'Spring', 'JavaScript', 'TypeScript', 'React', 'Python', 'Node.js', 'AWS', 'Docker'];
const EXPERIENCES = [
  { value: '', label: '경력 전체' },
  { value: '신입', label: '신입' },
  { value: '경력', label: '경력' },
  { value: '경력무관', label: '경력 무관' },
];
const LOCATIONS = ['', '서울', '경기', '인천', '대전', '세종', '부산', '대구', '광주', 'Remote'];
const SORT_OPTIONS = [
  { value: 'latest', label: '최신 등록순', sortBy: 'createdAt', direction: 'DESC' },
  { value: 'deadline', label: '마감 임박순', sortBy: 'endDate', direction: 'ASC' },
];

const getFilters = (searchParams) => {
  const sort = SORT_OPTIONS.find((option) => option.value === searchParams.get('sort')) ?? SORT_OPTIONS[0];
  return {
    keyword: searchParams.get('keyword') ?? '',
    jobCategory: searchParams.get('jobCategory') ?? '',
    techStack: searchParams.get('techStack') ?? '',
    experience: searchParams.get('experience') ?? '',
    location: searchParams.get('location') ?? '',
    sortBy: sort.sortBy,
    direction: sort.direction,
  };
};

const JobGridSkeleton = () => (
  <div className="all-jobs-grid" aria-label="공고 불러오는 중">
    {Array.from({ length: 8 }, (_, index) => <div className="job-card-skeleton" key={index} />)}
  </div>
);

const JobsResults = ({ filters }) => {
  const [jobs, setJobs] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [initialLoading, setInitialLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');

  const fetchPage = useCallback(
    (pageToLoad = 0) => searchJobs(filters, pageToLoad, PAGE_SIZE),
    [filters]
  );

  useEffect(() => {
    const loadInitialData = async () => {
      try {
        const data = await fetchPage(0);
        setJobs(data.content ?? []);
        setTotalPages(data.totalPages ?? data.page?.totalPages ?? 0);
        setTotalElements(data.totalElements ?? data.page?.totalElements ?? 0);
        setPage(1);
      } catch (error) {
        setErrorMessage('공고를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.');
        console.error('공고 초기 로드 실패:', error);
      } finally {
        setInitialLoading(false);
      }
    };

    loadInitialData();
  }, [fetchPage]);

  const loadMoreData = useCallback(async () => {
    if (initialLoading || page >= totalPages) return false;

    try {
      const data = await fetchPage(page);
      const nextPage = page + 1;
      const maxPages = data.totalPages ?? data.page?.totalPages ?? totalPages;
      setJobs((previous) => [...previous, ...(data.content ?? [])]);
      setTotalPages(maxPages);
      setPage(nextPage);
      return nextPage < maxPages;
    } catch (error) {
      setErrorMessage('추가 공고를 불러오지 못했습니다. 네트워크 상태를 확인해주세요.');
      console.error('공고 추가 로드 실패:', error);
      return false;
    }
  }, [fetchPage, initialLoading, page, totalPages]);

  const { loading } = useInfiniteScroll(loadMoreData, 280);

  return (
    <>
      <div className="results-toolbar">
        <p>조건에 맞는 공고 <strong>{totalElements.toLocaleString()}</strong>개</p>
        <span>스크롤하면 공고를 계속 불러옵니다</span>
      </div>

      {initialLoading ? <JobGridSkeleton /> : (
        <div className="all-jobs-grid">
          {jobs.map((job) => <JobCard key={job.id} job={job} />)}
        </div>
      )}

      {loading && <LoadingSpinner />}
      {errorMessage && <div className="all-jobs-message error">{errorMessage}</div>}
      {!initialLoading && !errorMessage && jobs.length === 0 && (
        <div className="all-jobs-message empty">
          <strong>조건에 맞는 공고가 없어요.</strong>
          <span>필터를 줄이거나 다른 검색어를 입력해보세요.</span>
        </div>
      )}
      {!initialLoading && !loading && !errorMessage && jobs.length > 0 && page >= totalPages && (
        <div className="all-jobs-message">모든 공고를 확인했어요.</div>
      )}
    </>
  );
};

const AllJobsPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const [isMobileFilterOpen, setIsMobileFilterOpen] = useState(false);
  const resultKey = searchParams.toString();
  const filters = useMemo(() => getFilters(new URLSearchParams(resultKey)), [resultKey]);

  const updateParams = (updates) => {
    const nextParams = new URLSearchParams(searchParams);
    Object.entries(updates).forEach(([key, value]) => {
      if (value) nextParams.set(key, value);
      else nextParams.delete(key);
    });
    setSearchParams(nextParams);
  };

  const submitKeyword = (event) => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    updateParams({ keyword: formData.get('keyword')?.toString().trim() ?? '' });
  };

  const clearFilters = () => setSearchParams({});
  const activeFilters = [
    filters.keyword && { key: 'keyword', label: `검색: ${filters.keyword}` },
    filters.jobCategory && { key: 'jobCategory', label: JOB_ROLES.find((role) => role.value === filters.jobCategory)?.label },
    filters.techStack && { key: 'techStack', label: filters.techStack },
    filters.experience && { key: 'experience', label: filters.experience },
    filters.location && { key: 'location', label: filters.location },
  ].filter(Boolean);

  return (
    <main className="all-jobs-page">
      <div className="all-jobs-breadcrumb"><Link to="/">홈</Link><span>/</span>개발자 채용</div>

      <header className="all-jobs-header">
        <div>
          <span>DEVELOPER JOBS</span>
          <h1>개발 직무 탐색</h1>
          <p>직무와 기술, 커리어 조건을 조합해 나에게 맞는 공고를 찾아보세요.</p>
        </div>
      </header>

      <section className="job-explorer" aria-label="채용공고 검색 및 필터">
        <form className="jobs-search-form" onSubmit={submitKeyword} key={filters.keyword}>
          <Search size={20} />
          <input name="keyword" defaultValue={filters.keyword} placeholder="포지션, 회사명, 기술 스택 검색" aria-label="채용공고 검색" />
          {filters.keyword && <button type="button" className="search-clear" onClick={() => updateParams({ keyword: '' })} aria-label="검색어 지우기"><X size={16} /></button>}
          <button type="submit" className="search-submit">검색</button>
        </form>

        <button
          type="button"
          className="mobile-filter-toggle"
          onClick={() => setIsMobileFilterOpen((open) => !open)}
          aria-expanded={isMobileFilterOpen}
        >
          <SlidersHorizontal size={17} /> 필터 {activeFilters.length > 0 && <b>{activeFilters.length}</b>}
        </button>

        <div className={`filter-panel ${isMobileFilterOpen ? 'open' : ''}`}>
          <div className="role-filter" aria-label="개발 직무">
            {JOB_ROLES.map((role) => (
              <button
                type="button"
                className={filters.jobCategory === role.value ? 'active' : ''}
                key={role.value || 'all'}
                onClick={() => updateParams({ jobCategory: role.value })}
              >
                {role.label}
              </button>
            ))}
          </div>

          <div className="detail-filters">
            <label>
              <span>기술 스택</span>
              <select value={filters.techStack} onChange={(event) => updateParams({ techStack: event.target.value })}>
                {TECH_STACKS.map((stack) => <option value={stack} key={stack || 'all'}>{stack || '전체 기술'}</option>)}
              </select>
            </label>
            <label>
              <span>경력</span>
              <select value={filters.experience} onChange={(event) => updateParams({ experience: event.target.value })}>
                {EXPERIENCES.map((option) => <option value={option.value} key={option.value || 'all'}>{option.label}</option>)}
              </select>
            </label>
            <label>
              <span>지역</span>
              <select value={filters.location} onChange={(event) => updateParams({ location: event.target.value })}>
                {LOCATIONS.map((location) => <option value={location} key={location || 'all'}>{location || '지역 전체'}</option>)}
              </select>
            </label>
            <label className="sort-filter">
              <span>정렬</span>
              <select value={searchParams.get('sort') ?? 'latest'} onChange={(event) => updateParams({ sort: event.target.value === 'latest' ? '' : event.target.value })}>
                {SORT_OPTIONS.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
              </select>
            </label>
          </div>
        </div>

        {activeFilters.length > 0 && (
          <div className="active-filter-row">
            <span>선택 조건</span>
            {activeFilters.map((filter) => (
              <button type="button" key={filter.key} onClick={() => updateParams({ [filter.key]: '' })}>
                {filter.label} <X size={13} />
              </button>
            ))}
            <button type="button" className="clear-all" onClick={clearFilters}>전체 초기화</button>
          </div>
        )}
      </section>

      <JobsResults key={resultKey} filters={filters} />
    </main>
  );
};

export default AllJobsPage;
