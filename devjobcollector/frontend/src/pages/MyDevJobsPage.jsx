import React, { useEffect, useMemo, useState } from 'react';
import { Bookmark, BriefcaseBusiness, ChevronRight, Clock3, RefreshCw, Search, Trash2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import {
  deleteBookmark,
  fetchApplications,
  fetchBookmarks,
  fetchRecentJobs,
  updateApplicationStatus,
} from '../api/careerActivityApi';
import MemberSidebar from '../components/member/MemberSidebar';
import '../styles/MemberPages.css';

const TABS = [
  { id: 'saved', label: '저장한 공고', icon: Bookmark },
  { id: 'applied', label: '지원 현황', icon: BriefcaseBusiness },
  { id: 'recent', label: '최근 본 공고', icon: Clock3 },
];

const EMPTY_COPY = {
  saved: ['아직 저장한 공고가 없어요.', '관심 있는 공고를 저장하면 여기에서 한 번에 볼 수 있어요.'],
  applied: ['진행 중인 지원 내역이 없어요.', '지원한 포지션의 진행 상태를 이곳에서 관리할 수 있어요.'],
  recent: ['최근 확인한 공고가 없어요.', '채용공고를 둘러보면 최근 본 내역이 차곡차곡 쌓여요.'],
};

const STATUS_LABELS = {
  APPLIED: '지원 완료',
  DOCUMENT_SCREENING: '서류 전형',
  INTERVIEW: '면접',
  OFFERED: '최종 합격',
  REJECTED: '불합격',
  WITHDRAWN: '지원 철회',
};

const formatActivityDate = (value) => {
  if (!value) return '';
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' }).format(new Date(value));
};

const MyDevJobsPage = () => {
  const [activeTab, setActiveTab] = useState('saved');
  const [activities, setActivities] = useState({ saved: [], applied: [], recent: [] });
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');
  const [pendingKey, setPendingKey] = useState('');
  const [title, description] = EMPTY_COPY[activeTab];

  const loadActivities = async () => {
    setLoading(true);
    setErrorMessage('');
    try {
      const [saved, applied, recent] = await Promise.all([
        fetchBookmarks(), fetchApplications(), fetchRecentJobs(),
      ]);
      setActivities({ saved, applied, recent });
    } catch (error) {
      if (error.response?.status !== 401) {
        setErrorMessage('채용 활동을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadActivities();
  }, []);

  const activeItems = useMemo(() => activities[activeTab] ?? [], [activeTab, activities]);

  const handleDeleteBookmark = async (jobPostId) => {
    const key = `bookmark-${jobPostId}`;
    setPendingKey(key);
    try {
      await deleteBookmark(jobPostId);
      setActivities((current) => ({
        ...current,
        saved: current.saved.filter((item) => item.jobPostId !== jobPostId),
      }));
    } finally {
      setPendingKey('');
    }
  };

  const handleStatusChange = async (applicationId, status) => {
    const key = `application-${applicationId}`;
    setPendingKey(key);
    try {
      const updated = await updateApplicationStatus(applicationId, status);
      setActivities((current) => ({
        ...current,
        applied: current.applied.map((item) => (
          item.applicationId === applicationId ? updated : item
        )),
      }));
    } finally {
      setPendingKey('');
    }
  };

  return (
    <main className="member-page">
      <div className="member-layout">
        <MemberSidebar />
        <div className="member-main">
          <header className="member-page-heading">
            <span className="member-eyebrow">MY DEVJOBS</span>
            <h1>마이데브잡</h1>
            <p>관심 공고부터 지원 과정까지 내 채용 활동을 모아보세요.</p>
          </header>

          <section className="activity-summary" aria-label="채용 활동 요약">
            {TABS.map(({ id, label, icon: Icon }) => (
              <button type="button" key={id} onClick={() => setActiveTab(id)} className={activeTab === id ? 'active' : ''}>
                <span>{React.createElement(Icon, { size: 19 })}</span>
                <strong>{loading ? '–' : activities[id].length}</strong>
                <small>{label}</small>
                <ChevronRight size={17} aria-hidden="true" />
              </button>
            ))}
          </section>

          <section className="member-content-card">
            <div className="member-tabs" role="tablist" aria-label="마이데브잡 목록">
              {TABS.map(({ id, label }) => (
                <button
                  type="button"
                  role="tab"
                  aria-selected={activeTab === id}
                  className={activeTab === id ? 'active' : ''}
                  onClick={() => setActiveTab(id)}
                  key={id}
                >
                  {label}
                </button>
              ))}
            </div>
            {loading && <div className="member-activity-state">채용 활동을 불러오는 중입니다.</div>}
            {!loading && errorMessage && (
              <div className="member-activity-state error" role="alert">
                <p>{errorMessage}</p>
                <button type="button" onClick={loadActivities}><RefreshCw size={16} /> 다시 시도</button>
              </div>
            )}
            {!loading && !errorMessage && activeItems.length === 0 && (
              <div className="member-empty-state">
                <span><Search size={28} /></span>
                <h2>{title}</h2>
                <p>{description}</p>
                <Link to="/jobs">채용공고 둘러보기</Link>
              </div>
            )}
            {!loading && !errorMessage && activeItems.length > 0 && (
              <div className="member-activity-list">
                {activeItems.map((item) => (
                  <article className="member-activity-item" key={`${activeTab}-${item.jobPostId}`}>
                    <Link className="member-activity-copy" to={`/job/${item.jobPostId}`}>
                      <small>{item.companyName}</small>
                      <h2>{item.title}</h2>
                      <p>{[item.location, item.experience].filter(Boolean).join(' · ') || '조건 미정'}</p>
                    </Link>
                    <div className="member-activity-meta">
                      {activeTab === 'saved' && (
                        <>
                          <time>{formatActivityDate(item.bookmarkedAt)}</time>
                          <button
                            type="button"
                            aria-label={`${item.title} 저장 취소`}
                            disabled={pendingKey === `bookmark-${item.jobPostId}`}
                            onClick={() => handleDeleteBookmark(item.jobPostId)}
                          ><Trash2 size={17} /></button>
                        </>
                      )}
                      {activeTab === 'applied' && (
                        <>
                          <time>{formatActivityDate(item.appliedAt)}</time>
                          <select
                            aria-label={`${item.title} 지원 상태`}
                            value={item.status}
                            disabled={pendingKey === `application-${item.applicationId}`}
                            onChange={(event) => handleStatusChange(item.applicationId, event.target.value)}
                          >
                            {Object.entries(STATUS_LABELS).map(([value, label]) => (
                              <option key={value} value={value}>{label}</option>
                            ))}
                          </select>
                        </>
                      )}
                      {activeTab === 'recent' && (
                        <>
                          <time>{formatActivityDate(item.lastViewedAt)}</time>
                          <span>{item.viewCount}회 조회</span>
                        </>
                      )}
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    </main>
  );
};

export default MyDevJobsPage;
