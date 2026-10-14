import { getAccessToken } from '../utils/accessToken';
import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { fetchJobDetail, recordJobEngagement } from '../api/jobApi';
import { createApplication, createBookmark, recordRecentJob } from '../api/careerActivityApi';
import LoadingSpinner from '../components/common/LoadingSpinner';
import TechStackBadge from '../components/job/TechStackBadge';
import StructuredJobContent from '../components/job/StructuredJobContent';
import { formatDate } from '../utils/dateParser';
import { getDaysRemaining } from '../utils/dateParser';
import '../styles/DetailPage.css';

const DetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [job, setJob] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activityMessage, setActivityMessage] = useState('');
  const [activityPending, setActivityPending] = useState('');
  const viewEvent = useRef(null);

  useEffect(() => {  
  let cancelled = false;
  if (viewEvent.current?.id !== id) viewEvent.current = { id, eventId: crypto.randomUUID() };
  const eventId = viewEvent.current.eventId;
  const loadJobDetail = async () => {
    if (!id) return;
    
    try {
      setLoading(true);
      const data = await fetchJobDetail(id);
      if (cancelled) return;
      setJob(data);
      if (data.sourcePlatform === 'COMPANY_DIRECT') {
        recordJobEngagement(id, 'VIEW', eventId).catch(() => {});
      }
      if (getAccessToken()) {
        recordRecentJob(id).catch((activityError) => {
          if (activityError.response?.status !== 401) console.error('최근 조회 기록 실패:', activityError);
        });
      }
    } catch (err) {
      setError('공고를 불러오는데 실패했습니다.');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };
  
  loadJobDetail();
  return () => { cancelled = true; };
  }, [id]);

  if (loading) return <LoadingSpinner />;
  if (error) return <div className="error-message">{error}</div>;
  if (!job) return <div className="error-message">공고를 찾을 수 없습니다.</div>;

  const daysRemaining = getDaysRemaining(job.endDate);

  const requireMember = () => {
    if (getAccessToken()) return true;
    const nextPath = `/job/${id}`;
    sessionStorage.setItem('postLoginNextPath', nextPath);
    navigate(`/login?next=${encodeURIComponent(nextPath)}`);
    return false;
  };

  const handleBookmark = async () => {
    if (!requireMember()) return;
    setActivityPending('bookmark');
    setActivityMessage('');
    try {
      await createBookmark(id);
      setActivityMessage('저장한 공고에 추가했습니다.');
    } catch (activityError) {
      if (activityError.response?.status !== 401) setActivityMessage('공고 저장에 실패했습니다.');
    } finally {
      setActivityPending('');
    }
  };

  const handleApply = () => {
    if (!requireMember()) return;
    if (job.sourcePlatform === 'COMPANY_DIRECT') recordJobEngagement(id, 'APPLY_CLICK').catch(() => {});
    setActivityMessage('');
    createApplication(id).then(() => setActivityMessage('지원 기록을 저장했습니다. 열린 채용 사이트에서 지원을 완료해주세요.')).catch((activityError) => {
      if (activityError.response?.status !== 401) setActivityMessage('지원 기록 저장에 실패했습니다. 열린 채용 사이트에서 지원 여부를 확인해주세요.');
    });
    window.open(job.originalUrl, '_blank', 'noopener,noreferrer');
  };

  return (
    <div className="detail-page">
      <button className="back-button" onClick={() => navigate(-1)}>
        ← 돌아가기
      </button>

      <div className="detail-container">
        <header className="detail-header">
          <h1>{job.title}</h1>
          {job.sourcePlatform === 'COMPANY_DIRECT' && <span className="company-direct-badge">기업 직접 등록</span>}
          <span>{job.companyName} {'>'}</span>
        </header>

        <section className="detail-section-top">
          <div className="info-grid">
            <div className="info-item">
              <span className="label">위치</span>
              <span className="value">{job.location || '위치 미정'}</span>
            </div>
            <div className="info-item">
              <span className="label">경력</span>
              <span className="value">{job.experience || '경력무관'}</span>
            </div>
            <div className="info-item">
              <span className="label">공고일</span>
              <span className="value">{formatDate(job.startDate)}</span>
            </div>
            <div className="info-item">
              <span className="label">마감일</span>
              <span className="value">{formatDate(job.endDate)}</span>
            </div>
            <div className="info-item">
              {daysRemaining !== null && daysRemaining >= 0 && (
                <span className='days-badge-d'>D-{daysRemaining}</span>
              )}
            </div>
          </div>
        </section>

        <section className="detail-section">
          <h3>카테고리</h3>
          <div className="tech-stack">
            {job.techStacks?.map((tech) => (
              <TechStackBadge key={tech.id} tech={tech} />
            ))}
          </div>
        </section>

        {job.processInfo && (
          <section className="detail-section">
            <div className="detail-section-heading">
              <span className="detail-section-number">01</span>
              <h2>전형 절차</h2>
            </div>
            <StructuredJobContent content={job.processInfo} />
          </section>
        )}

        {job.applyQual && (
          <section className="detail-section">
            <div className="detail-section-heading">
              <span className="detail-section-number">{job.processInfo ? '02' : '01'}</span>
              <div>
                <p>JOB DESCRIPTION</p>
                <h2>상세 내용</h2>
              </div>
            </div>
            {job.sourcePlatform === 'COMPANY_DIRECT'
              ? <div style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{job.applyQual}</div>
              : <StructuredJobContent content={job.applyQual} />}
          </section>
        )}

        <div className="action-buttons">
          <button
            type="button"
            className="save-job-button im-button"
            disabled={activityPending === 'bookmark'}
            onClick={handleBookmark}
          >
            {activityPending === 'bookmark' ? '저장 중...' : '공고 저장'}
          </button>
          <button type="button" className="apply-button im-button" onClick={handleApply}>지원하기 🚀</button>
        </div>
        <div className="im-feedback" role="status" aria-live="polite" aria-atomic="true">
          {activityMessage && <div className="im-toast"><span>{activityMessage}</span><button type="button" className="im-button" aria-label="알림 닫기" onClick={() => setActivityMessage('')}>닫기</button></div>}
        </div>
      </div>
    </div>
  );
};

export default DetailPage;
