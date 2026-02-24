import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { fetchJobDetail } from '../api/jobApi';
import LoadingSpinner from '../components/common/LoadingSpinner';
import TechStackBadge from '../components/job/TechStackBadge';
import { formatDate } from '../utils/dateParser';
import { getDaysRemaining } from '../utils/dateParser';
import '../styles/DetailPage.css';

const DetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [job, setJob] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {  
  const loadJobDetail = async () => {
    if (!id) return;
    
    try {
      setLoading(true);
      const data = await fetchJobDetail(id);
      setJob(data);
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
            <h3>전형 절차</h3>
            <div className="description">
              {job.processInfo}
            </div>
          </section>
        )}

        {job.applyQual && (
          <section className="detail-section">
            <h3>상세 설명</h3>
            <div className="description">
              {job.applyQual}
            </div>
          </section>
        )}

        <div className="action-buttons">
          <a 
            href={job.originalUrl} 
            target="_blank" 
            rel="noopener noreferrer"
            className="apply-button"
          >
            지원하기 🚀
          </a>
        </div>
      </div>
    </div>
  );
};

export default DetailPage;