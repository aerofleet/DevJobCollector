import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { fetchJobDetail } from '../api/jobApi';
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

  useEffect(() => {  
  const loadJobDetail = async () => {
    if (!id) return;
    
    try {
      setLoading(true);
      const data = await fetchJobDetail(id);
      setJob(data);
      if (localStorage.getItem('accessToken')) {
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
  }, [id]);

  if (loading) return <LoadingSpinner />;
  if (error) return <div className="error-message">{error}</div>;
  if (!job) return <div className="error-message">공고를 찾을 수 없습니다.</div>;

  const daysRemaining = getDaysRemaining(job.endDate);

  const requireMember = () => {
    if (localStorage.getItem('accessToken')) return true;
    const nextPath = `/job/${id}`;
    sessionStorage.setItem('postLoginNextPath', nextPath);
    navigate(`/login?next=${encodeURIComponent(nextPath)}`);
    return false;
  };

  const handleBookmark = async () => {
    if (!requireMember()) return;
    setActivityPending('bookmark');
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
    createApplication(id).catch((activityError) => {
      if (activityError.response?.status !== 401) console.error('지원 기록 실패:', activityError);
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
            <StructuredJobContent content={job.applyQual} />
          </section>
        )}

        <div className="action-buttons">
          <button
            type="button"
            className="save-job-button"
            disabled={activityPending === 'bookmark'}
            onClick={handleBookmark}
          >
            {activityPending === 'bookmark' ? '저장 중...' : '공고 저장'}
          </button>
          <button type="button" className="apply-button" onClick={handleApply}>지원하기 🚀</button>
        </div>
        {activityMessage && <p className="detail-activity-message" role="status">{activityMessage}</p>}
      </div>
    </div>
  );
};

export default DetailPage;
