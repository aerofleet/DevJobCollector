import { getAccessToken } from '../utils/accessToken';
import { trackGrowth } from '../utils/growthAnalytics';
import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { fetchJobDetail, recordJobEngagement } from '../api/jobApi';
import { createApplication, createBookmark, recordRecentJob } from '../api/careerActivityApi';
import LoadingSpinner from '../components/common/LoadingSpinner';
import TechStackBadge from '../components/job/TechStackBadge';
import StructuredJobContent from '../components/job/StructuredJobContent';
import { jobSourceLabel } from '../utils/jobSource';
import { extractJobRequirements, extractJobSalary, extractMentionedTools, formatJobLocation } from '../utils/jobRequirements';
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
      if (!viewEvent.current.tracked) { trackGrowth('JOB_VIEW', id); viewEvent.current.tracked = true; }
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
  const requirements = extractJobRequirements(job.applyQual);
  const isPublicAlio = job.sourcePlatform === 'PUBLIC_ALIO';
  const salary = extractJobSalary(job.applyQual);
  const deadlineEvidence = job.deadlineEvidence;
  const mentionedTools = extractMentionedTools(requirements);
  const registeredTools = new Set((job.techStacks || []).map((tech) => tech.stackName?.trim().toLowerCase()));
  const additionalTools = mentionedTools.filter((name) => !registeredTools.has(name.toLowerCase()));
  let originalUrl = null;
  try {
    const parsed = new URL(job.originalUrl);
    if (['http:', 'https:'].includes(parsed.protocol)) originalUrl = parsed.href;
  } catch { /* 원문 주소가 없거나 유효하지 않습니다. */ }

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
    trackGrowth('APPLY_CLICK', id);
    if (job.sourcePlatform === 'COMPANY_DIRECT') recordJobEngagement(id, 'APPLY_CLICK').catch(() => {});
    setActivityMessage('');
    createApplication(id).then(() => setActivityMessage('지원 기록을 저장했습니다. 열린 채용 사이트에서 지원을 완료해주세요.')).catch((activityError) => {
      if (activityError.response?.status !== 401) setActivityMessage('지원 기록 저장에 실패했습니다. 열린 채용 사이트에서 지원 여부를 확인해주세요.');
    });
    if (originalUrl) window.open(originalUrl, '_blank', 'noopener,noreferrer');
  };

  return (
    <div className="detail-page">
      <button className="back-button" onClick={() => {
        if (window.history.state?.idx > 0) navigate(-1);
        else navigate('/jobs', { replace: true });
      }}>
        ← 돌아가기
      </button>

      <div className="detail-container">
        <header className="detail-header">
          <h1>{job.title}</h1>
          <p className="detail-source">출처 · {jobSourceLabel(job)}</p>
          <span>{job.companyName}</span>
        </header>

        <section className="detail-section-top">
          <div className="info-grid">
            <div className="info-item">
              <span className="label">위치</span>
              <span className="value">{formatJobLocation(job.location) || '위치 미정'}</span>
            </div>
            <div className="info-item">
              <span className="label">경력</span>
              <span className="value">{job.experience || '경력 확인 필요'}</span>
            </div>
            {isPublicAlio && job.jobCategory && <div className="info-item"><span className="label">채용 분야</span><span className="value">{job.jobCategory}</span></div>}
            {isPublicAlio && job.hireType && <div className="info-item"><span className="label">고용 형태</span><span className="value">{job.hireType}</span></div>}
            <div className="info-item">
              <span className="label">공고일 또는 수집일</span>
              <span className="value">{formatDate(job.startDate)}</span>
            </div>
            <div className="info-item">
              <span className="label">마감일</span>
              <span className="value">{deadlineEvidence?.status === 'CONFLICT' || deadlineEvidence?.status === 'AMBIGUOUS' ? '마감일 원문 확인 필요' : job.endDate?.startsWith('9999-12-31') ? (deadlineEvidence?.status === 'PRESENT' ? `${deadlineEvidence.localDate.replaceAll('-', '.')} (본문 기준 · 원문 확인)` : '마감일 확인 필요') : formatDate(job.endDate)}</span>
            </div>
            <div className="info-item">
              {daysRemaining !== null && daysRemaining >= 0 && (
                <span className='days-badge-d'>D-{daysRemaining}</span>
              )}
            </div>
          </div>
        </section>

        <section className="detail-section">
          <h3>기술 스택</h3>
          <div className="tech-stack">
            {job.techStacks?.map((tech) => (
              <TechStackBadge key={tech.id} tech={tech} />
            ))}
          </div>
          {additionalTools.length > 0 && <><p className="job-content-empty">원문 자격·우대 항목에 언급된 기술 및 도구</p><div className="tech-stack">{additionalTools.map((name) => <TechStackBadge key={name} tech={{ stackName: name }} />)}</div></>}
          {!job.techStacks?.length && !additionalTools.length && <p className="job-content-empty">{isPublicAlio ? '등록된 기술 스택이 없습니다.' : '등록된 기술 스택이 없습니다. 원문을 확인해 주세요.'}</p>}
        </section>

        {isPublicAlio ? <>
          <section className="detail-section">
            <h3>지원 자격</h3>
            <StructuredJobContent content={job.applyQual} emptyMessage="제공된 지원 자격이 없습니다." />
          </section>
          {job.publicPreference && <section className="detail-section"><h3>우대 사항</h3><StructuredJobContent content={job.publicPreference} /></section>}
          {job.publicPreferenceConditions && <section className="detail-section"><h3>우대 조건</h3><StructuredJobContent content={job.publicPreferenceConditions} /></section>}
          {job.publicDisqualification && <section className="detail-section"><h3>결격 사유</h3><StructuredJobContent content={job.publicDisqualification} /></section>}
          <section className="detail-section">
            <h3>전형 절차 및 방법</h3>
            <StructuredJobContent content={job.processInfo} emptyMessage="제공된 전형 정보가 없습니다." />
            <p className="detail-source-note">출처: 공공기관 알리오 · 제공된 공고 정보와 첨부파일을 확인해 주세요.</p>
          </section>
          {job.files?.length > 0 && <section className="detail-section"><h3>첨부파일</h3><ul>{job.files.map((file) => {
            let href = null;
            try { const parsed = new URL(file.fileUrl); if (['http:', 'https:'].includes(parsed.protocol)) href = parsed.href; } catch { /* 유효한 주소만 연결합니다. */ }
            return <li key={file.id}>{href ? <a href={href} target="_blank" rel="noopener noreferrer">{file.fileName || '첨부파일'}</a> : (file.fileName || '첨부파일')}</li>;
          })}</ul></section>}
        </> : <>
        <section className="detail-section">
          <h3>자격 조건</h3>
          {requirements.required.length ? <ul>{requirements.required.map((item, index) => <li key={index}>{item}</li>)}</ul>
            : <p className="job-content-empty">자격 조건을 확인하지 못했어요. 원문을 확인해 주세요.</p>}
        </section>
        <section className="detail-section">
          <h3>우대 사항</h3>
          {requirements.preferred.length ? <ul>{requirements.preferred.map((item, index) => <li key={index}>{item}</li>)}</ul>
            : <p className="job-content-empty">우대 사항을 확인하지 못했어요. 원문을 확인해 주세요.</p>}
        </section>
        <section className="detail-section">
          <h3>급여 정보</h3>
          {salary && <p>{salary}</p>}
          {job.supplementalSalaryText && <>
            <p className="detail-source-note">원문 페이지에서 추가로 수집한 급여·복리후생 정보입니다. 최신 조건은 원문을 확인해 주세요.</p>
            <StructuredJobContent content={job.supplementalSalaryText} />
          </>}
          {!salary && !job.supplementalSalaryText && <p className="job-content-empty">급여 정보를 확인하지 못했어요. 원문을 확인해 주세요.</p>}
          <p className="detail-source-note">일부 항목만 표시됩니다. 정확한 자격 조건과 상세 내용은 원문에서 확인해 주세요.</p>
          {originalUrl && <a className="detail-original-link" href={originalUrl} target="_blank" rel="noopener noreferrer">공고 원문 보기 ↗</a>}
        </section>
        {job.applyQual && <section className="detail-section">
          <details>
            <summary>수집된 공고 내용 전체 보기</summary>
            <p className="detail-source-note">수집된 내용이며 원문 전체와 다를 수 있습니다. 최신 내용은 공고 원문을 확인해 주세요.</p>
            <StructuredJobContent content={job.applyQual} />
          </details>
        </section>}
        </>}

        <div className="action-buttons">
          <button type="button" className="save-job-button im-button" onClick={async () => {
            const url = `${window.location.origin}/job/${id}`;
            try {
              if (navigator.share) await navigator.share({ title: `${job.title} | 데브잡스`, url });
              else { await navigator.clipboard.writeText(url); setActivityMessage('공고 링크를 복사했습니다.'); }
              trackGrowth('SHARE', id);
            } catch (error) { if (error.name !== 'AbortError') setActivityMessage('링크를 복사하지 못했습니다. 주소창의 공고 주소를 복사해주세요.'); }
          }}>공고 공유</button>
          <button
            type="button"
            className="save-job-button im-button"
            disabled={activityPending === 'bookmark'}
            onClick={handleBookmark}
          >
            {activityPending === 'bookmark' ? '저장 중...' : '공고 저장'}
          </button>
          <button type="button" className="apply-button im-button" onClick={handleApply} disabled={!originalUrl}>지원하기 🚀</button>
        </div>
        <div className="im-feedback" role="status" aria-live="polite" aria-atomic="true">
          {activityMessage && <div className="im-toast"><span>{activityMessage}</span><button type="button" className="im-button" aria-label="알림 닫기" onClick={() => setActivityMessage('')}>닫기</button></div>}
        </div>
      </div>
    </div>
  );
};

export default DetailPage;
