import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, BriefcaseBusiness, Code2, Search, Sparkles } from 'lucide-react';
import { fetchRandomDeveloperJobs } from '../api/jobApi';
import JobCard from '../components/job/JobCard';
import LoadingSpinner from '../components/common/LoadingSpinner';
import HomeRecommendations from '../components/member/HomeRecommendations';
import { useMemberSession } from '../contexts/memberSession';
import '../styles/MainPage.css';

const QUICK_SEARCHES = ['Java', 'Spring', 'React', 'Python', '신입'];

const DISCOVERY_THEMES = [
  {
    eyebrow: 'DEVOPS & INFRA',
    title: '배포부터 운영까지\nDevOps · 인프라 채용',
    keyword: 'DevOps',
    className: 'backend',
    icon: BriefcaseBusiness,
  },
  {
    eyebrow: 'THE ROOKIE',
    title: '첫 커리어를 시작하는\n신입 개발자 채용',
    keyword: '신입',
    className: 'rookie',
    icon: Code2,
  },
  {
    eyebrow: 'BACKEND PICKS',
    title: '서비스의 중심을 만드는\n백엔드 포지션',
    keyword: '백엔드',
    className: 'backend',
    icon: BriefcaseBusiness,
  },
  {
    eyebrow: 'FRONTEND PICKS',
    title: '사용자 경험을 완성하는\n프론트엔드 포지션',
    keyword: '프론트엔드',
    className: 'frontend',
    icon: Sparkles,
  },
  {
    eyebrow: 'DATA & AI',
    title: '데이터로 가치를 만드는\nAI · 데이터 포지션',
    keyword: '데이터',
    className: 'rookie',
    icon: Sparkles,
  },
  {
    eyebrow: 'MOBILE DEVELOPERS',
    title: '손안의 경험을 만드는\n모바일 앱 개발',
    keyword: '모바일',
    className: 'frontend',
    icon: Code2,
  },
];

const MainPage = ({ onSearch }) => {
  const { token } = useMemberSession();
  const [jobs, setJobs] = useState([]);
  const [candidateCount, setCandidateCount] = useState(0);
  const [errorMessage, setErrorMessage] = useState('');
  const [heroKeyword, setHeroKeyword] = useState('');
  const [loading, setLoading] = useState(true);
  const initialRequest = useRef(null);

  const submitSearch = (event) => {
    event.preventDefault();
    onSearch?.(heroKeyword.trim());
  };

  const searchByKeyword = (value) => {
    setHeroKeyword(value);
    onSearch?.(value);
  };

  useEffect(() => {
    const loadRandomJobs = async () => {
      try {
        setErrorMessage('');
        initialRequest.current ??= fetchRandomDeveloperJobs(8);
        const data = await initialRequest.current;
        setJobs(data.content);
        setCandidateCount(data.candidateCount);
      } catch (error) {
        setErrorMessage('추천 공고를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.');
        console.error('랜덤 공고 로드 실패:', error);
      } finally {
        setLoading(false);
      }
    };

    loadRandomJobs();
  }, []);

  return (
    <div className="main-page">
      <section className="landing-hero" aria-labelledby="landing-title">
        <div className="hero-copy">
          <span className="hero-kicker" data-motion-reveal="" data-motion-step="0"><Sparkles size={15} /> 개발자 채용 탐색의 새로운 기준</span>
          <h1 id="landing-title" data-motion-reveal="lead" data-motion-step="0" data-motion-enter="headline">
            흩어진 개발자 채용공고,<br />
            <strong>데브잡스에서 한 번에.</strong>
          </h1>
          <p data-motion-reveal="" data-motion-step="1">여러 채용 플랫폼을 오갈 필요 없이, 지금 나에게 맞는 개발자 포지션을 빠르게 찾아보세요.</p>

          <form className="hero-search" action="/jobs" method="get" data-motion-reveal="" data-motion-step="2" data-motion-enter="search" onSubmit={submitSearch} role="search">
            <Search size={22} aria-hidden="true" />
            <label className="sr-only" htmlFor="hero-job-search">채용공고 검색</label>
            <input
              id="hero-job-search"
              name="keyword"
              type="search"
              value={heroKeyword}
              onChange={(event) => setHeroKeyword(event.target.value)}
              placeholder="직무, 기술 스택, 회사명으로 검색"
              maxLength={50}
            />
            <button type="submit">공고 찾기</button>
          </form>

          <div className="quick-searches" data-motion-reveal="" data-motion-step="2" aria-label="인기 검색어">
            <span>인기 검색</span>
            {QUICK_SEARCHES.map((item) => (
              <button type="button" key={item} onClick={() => searchByKeyword(item)}>#{item}</button>
            ))}
          </div>
        </div>

        <div className="hero-visual" data-motion-reveal="" data-motion-step="3" data-motion-enter="visual" aria-hidden="true">
          <div className="code-window">
            <div className="window-bar"><span /><span /><span /></div>
            <div className="code-lines">
              <p><em>const</em> nextCareer = &#123;</p>
              <p>&nbsp;&nbsp;role: <strong>&apos;Developer&apos;</strong>,</p>
              <p>&nbsp;&nbsp;matches: <b>{candidateCount || '8+'}</b>,</p>
              <p>&nbsp;&nbsp;status: <strong>&apos;ready&apos;</strong></p>
              <p>&#125;;</p>
            </div>
          </div>
          <div className="floating-chip chip-one">새 공고 업데이트</div>
          <div className="floating-chip chip-two">원하는 기술로 탐색</div>
        </div>
      </section>

      <aside className="career-shortcuts" aria-label="내 커리어 바로가기">
        <strong>{token ? '다음 커리어를 준비해볼까요?' : '내 경험에서 다음 기회를 찾아보세요.'}</strong>
        <nav><Link to="/resumes">이력서 관리</Link><Link to="/my-devjobs">저장·지원 현황</Link><Link to="/resume-analysis">AI 커리어 코칭</Link></nav>
      </aside>
      {token && <HomeRecommendations />}
      <section className="discovery-section" aria-labelledby="discovery-title">
        <div className="section-heading" data-motion-reveal="">
          <div>
            <span className="section-eyebrow">CURATED FOR DEVELOPERS</span>
            <h2 id="discovery-title">지금 주목할 포지션</h2>
          </div>
          <p>관심 있는 테마를 선택하면 관련 공고만 바로 모아볼 수 있어요.</p>
        </div>

        <div className="theme-grid">
          {DISCOVERY_THEMES.map(({ eyebrow, title, keyword: themeKeyword, className, icon: Icon }) => (
            <button
              type="button"
              className={`theme-card ${className}`}
              data-motion-reveal=""
              key={themeKeyword}
              onClick={() => searchByKeyword(themeKeyword)}
            >
              <span className="theme-icon">{React.createElement(Icon, { size: 22 })}</span>
              <span className="theme-eyebrow">{eyebrow}</span>
              <strong>{title.split('\n').map((line, index) => <React.Fragment key={line}>{index > 0 && <br />}{line}</React.Fragment>)}</strong>
              <span className="theme-link">공고 보기 <ArrowRight size={17} /></span>
            </button>
          ))}
        </div>
      </section>

      {!token && <HomeRecommendations />}
      <section className="positions-section" id="positions" aria-labelledby="positions-title">
        <div className="section-heading positions-heading" data-motion-reveal="">
          <div>
            <span className="section-eyebrow">DISCOVER POSITIONS</span>
            <h2 id="positions-title">오늘의 개발자 채용공고</h2>
          </div>
          <div className="positions-summary">
            <p>개발 직무 후보 중 무작위로 선택한 8개 공고예요.</p>
            <Link to="/jobs">전체 공고 보기 <ArrowRight size={16} /></Link>
          </div>
        </div>

        <div className="job-list">
          {jobs.map(job => <JobCard key={job.id} job={job} />)}
        </div>

        {loading && <LoadingSpinner />}
        {errorMessage && <div className="end-message error-message">{errorMessage}</div>}
        {!loading && !errorMessage && jobs.length === 0 && <div className="empty-state">현재 노출할 공고가 없습니다.</div>}
      </section>

      <section className="login-cta" data-motion-reveal="">
        <div>
          <span className="section-eyebrow">YOUR NEXT MOVE</span>
          <h2>마음에 드는 공고를 놓치지 마세요.</h2>
          <p>{token ? '저장한 공고와 지원 현황을 한곳에서 이어서 확인하세요.' : '로그인하고 데브잡스에서 나만의 커리어 탐색을 시작하세요.'}</p>
        </div>
        <Link to={token ? '/my-devjobs' : '/login'}>{token ? '내 채용 활동 보기' : '로그인하고 시작하기'} <ArrowRight size={18} /></Link>
      </section>

      <footer className="landing-footer">
        <strong>DevJobs</strong>
        <p>개발자를 위한 채용공고를 한곳에.</p>
        <nav aria-label="법적 고지">
          <a href="/terms">이용약관</a>
          <a href="/privacy">개인정보 처리방침</a>
        </nav>
        <span>© {new Date().getFullYear()} DevJobs</span>
      </footer>
    </div>
  );
};

export default MainPage;
