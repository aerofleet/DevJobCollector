import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { useMemberSession } from '../../contexts/memberSession';
import { getHomeRecommendations, getCurrentRecommendations, listAnalyses, saveCurrentRecommendationFeedback } from '../../api/resumeAnalysisApi';
import JobCard from '../job/JobCard';
import JobComparisonDiagnostics from './JobComparisonDiagnostics';
import { comparisonSummary } from '../../utils/jobComparison';
import './HomeRecommendations.css';

export default function HomeRecommendations() {
  const { token, member, status, loadMember } = useMemberSession();
  useEffect(() => { if (token && status === 'idle') loadMember(); }, [token, status, loadMember]);
  if (!token) return <section className="home-recommendations home-recommendations-guest" aria-labelledby="personal-positions-title">
    <h2 id="personal-positions-title">나에게 맞는 추천 포지션을 찾고 있다면?</h2>
    <Link className="recommendation-login-banner" to="/login?next=%2Fresume-analysis">
      <span className="recommendation-login-message"><span aria-hidden="true">👋</span><span>로그인하고 내 이력서에 맞는 포지션을 추천받아보세요!</span></span>
      <span className="recommendation-login-action">회원가입/로그인 <ArrowRight size={16} aria-hidden="true" /></span>
    </Link>
  </section>;
  return <section className="home-recommendations home-recommendations-member" aria-label="내 이력서로 찾는 추천 포지션">
    <header><h2 id="personal-positions-title">{member?.name || '회원'}님을 위한 맞춤 AI 추천!</h2></header>
    <SavedRecommendations key={token} />
  </section>;
}

function SavedRecommendations() {
  const [history, setHistory] = useState([]);
  const [selected, setSelected] = useState('');
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [pending, setPending] = useState(null);
  const [notice, setNotice] = useState('');
  const [reason, setReason] = useState(null);
  const dialogRef = useRef(null);

  useEffect(() => {
    if (reason) dialogRef.current?.showModal();
  }, [reason]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true); setError(''); setResult(null); setNotice('');
      try {
        const [items, data] = await Promise.all([listAnalyses(), selected ? getCurrentRecommendations(selected) : getHomeRecommendations()]);
        const available = items.filter(item => item.status === 'COMPLETED_LOCAL');
        if (cancelled) return;
        setHistory(available);
        setResult(data);
      } catch (failure) {
        if (!cancelled) setError(failure.response?.status === 404
          ? '추천 결과를 찾을 수 없어요. 이력서 분석에서 새 추천을 만들어 주세요.'
          : '추천을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.');
      } finally { if (!cancelled) setLoading(false); }
    }
    load();
    return () => { cancelled = true; };
  }, [selected, attempt]);

  const feedback = async (job, type) => {
    setPending(job.jobId); setNotice('');
    try {
      await saveCurrentRecommendationFeedback(result.id, job.jobId, type);
      setResult(await getCurrentRecommendations(result.id));
      setNotice(type === 'MATCHED' ? '제외를 해제했어요. 현재 추천 순위에 따라 표시돼요.' : '추천에서 제외했어요. 다른 공고가 있으면 채워드려요.');
    } catch { setNotice('추천 상태를 갱신하지 못했어요. 다시 시도해 주세요.'); }
    finally { setPending(null); }
  };
  const reviews = result?.aiReview?.status === 'COMPLETED' ? result.aiReview.jobs || [] : [];
  const order = { RELATED: 0, REVIEW_NEEDED: 1, LOW_RELEVANCE: 3 };
  const jobs = [...(result?.recommendations || [])].sort((a, b) =>
    (order[reviews.find(item => item.jobId === a.jobId)?.relevance] ?? 2)
    - (order[reviews.find(item => item.jobId === b.jobId)?.relevance] ?? 2)).slice(0, 8);
  const analysisLink = result?.id ? `/resume-analysis?analysisId=${result.id}` : result?.resumeId ? `/resume-analysis?resumeId=${result.resumeId}` : '/resume-analysis';
  const emptyMessage = ({
    NO_AI_APPROVED_MATCHES: 'AI가 검토한 공고 중 현재 추천할 공고가 없어요. 아래에서 부적합·정보 부족의 근거를 확인할 수 있어요.',
    AI_REVIEW_REQUIRED: 'Senior·시니어·Vice President·Director·Manager 등 경력과 책임 범위 검토가 필요한 공고이거나, 이력서·공고가 변경된 후보예요. 전송 내용을 확인하고 AI 적합성을 검토해 주세요.',
    MISSING_CRITERIA: '추천 기준이 부족해요. 이력서에 희망 직무·기술을 저장하면 홈에서 바로 추천받을 수 있어요.',
    ALL_EXCLUDED: result?.id ? '조건에 맞는 현재 공고를 모두 제외했어요. 아래에서 제외를 해제하거나 전체 공고를 살펴보세요.' : '이전 분석에서 제외한 공고예요. 추천 기준 변경에서 해당 분석을 선택해 제외를 해제할 수 있어요.',
    NO_CURRENT_MATCHES: '현재 조회한 공고 중 기술 또는 직무가 연결되는 공고를 찾지 못했어요. 희망 직무를 확인하거나 전체 공고를 살펴보세요.',
    SAVED_JOBS_UNAVAILABLE: '저장된 추천 공고가 마감되거나 공개가 중단됐어요. 현재 공고에서도 대체할 추천을 찾지 못했어요.',
  })[result?.recommendationStatus?.reason] || '현재 추천 조건에 맞는 공고가 없어요. 추천 기준을 확인하거나 전체 공고를 살펴보세요.';
  return <>
    {history.length > 0 && <details className="recommendation-settings"><summary>추천 기준 변경</summary><div className="recommendation-controls"><label>추천 기준 이력서
      <select value={selected} disabled={loading || pending !== null} onChange={event => setSelected(event.target.value)}>
        <option value="">자동 추천 · 최근 저장 이력서</option>
        {history.map(item => <option key={item.id} value={String(item.id)}>{item.resumeTitle} · {item.createdAt?.slice(0, 10)}</option>)}
      </select></label></div></details>}
    {loading ? <p role="status">이력서 추천을 불러오는 중이에요.</p> : error ? <div role="alert"><p>{error}</p>
      <button onClick={() => setAttempt(value => value + 1)}>다시 시도</button> <Link to="/resume-analysis">이력서 분석 열기</Link></div>
      : !result || result.recommendationStatus?.reason === 'NO_RESUME' ? <div className="recommendation-empty"><h3>첫 추천을 받아보세요.</h3><p>이력서에 희망 직무와 기술을 저장하면 맞춤 공고가 바로 표시돼요.</p>
        <Link to="/resume">이력서 작성하고 추천받기 →</Link></div>
        : <>
          <p className="recommendation-caption">내 이력서를 바탕으로 찾은 공고예요.
            {result.recommendationStatus?.roleSource === 'RESUME_TITLE' && <> 기준 직무: {result.recommendationStatus.categories.join(', ')}.</>}
            {' '}<Link to={`/resume?resumeId=${result.resumeId}`}>이력서 확인</Link></p>
          <p className="recommendation-caption">{result.recommendationStatus?.aiFiltered && 'AI가 경력과 공고 요건을 검토한 추천이에요. '}
            {result.recommendationStatus?.aiPendingCount > 0
              ? <>AI 추천 후보 {result.recommendationStatus.aiPendingCount}개가 있어요. AI 코칭에서 검토하면 지원 적합성을 추가로 확인할 수 있어요.</>
              : !result.recommendationStatus?.aiFiltered && 'AI 코칭에서 공고를 검토하고 지원 적합성을 확인해 보세요.'}</p>
          {jobs.length ? <div className="personal-job-grid">{jobs.map(job => {
            const review = job.aiFit || reviews.find(item => item.jobId === job.jobId);
            return <article key={job.jobId} className="recommended-job">
              <JobCard job={{ ...job, id: job.jobId }} />
              <div className="personal-job-reason"><button type="button" onClick={() => setReason({ job, review })}>추천 근거 보기</button></div>
              {result.id && <footer><button disabled={pending !== null} onClick={() => feedback(job, 'NOT_INTERESTED')} aria-label={`${job.title} 추천 제외`}>{pending === job.jobId ? '처리 중…' : '관심 없음'}</button></footer>}
            </article>;
          })}</div> : <div className="recommendation-empty"><h3>현재 표시할 추천 공고가 없어요.</h3><p>{emptyMessage}</p><Link to="/jobs">전체 채용공고 보기 →</Link></div>}
          {result.id && result.excludedRecommendations?.length > 0 && <details className="recommendation-excluded"><summary>제외한 공고 {result.excludedRecommendations.length}개</summary>
            <ul>{result.excludedRecommendations.map(job => <li key={job.jobId}><Link to={`/job/${job.jobId}`}>{job.title}</Link>
              <button disabled={pending !== null} onClick={() => feedback(job, 'MATCHED')} aria-label={`${job.title} 제외 해제`}>제외 해제</button></li>)}</ul>
          </details>}
          {result.aiExcludedRecommendations?.length > 0 && <details className="recommendation-excluded"><summary>AI 검토로 보류한 공고 {result.aiExcludedRecommendations.length}개</summary>
            {result.aiExcludedRecommendations.map(job => <div key={job.jobId}><Link to={`/job/${job.jobId}`}>{job.title}</Link><p>{comparisonSummary(job.aiFit)?.label || (job.aiDecision === 'NOT_RECOMMENDED' ? '부적합' : '요건 확인 필요')}: {comparisonSummary(job.aiFit)?.reason || job.aiFit?.decisionReason}</p>
              <JobComparisonDiagnostics review={job.aiFit} />
              {job.aiFit?.criteria?.map((criterion, index) => <p key={index}>공고: {criterion.jobQuote} / 이력서: {criterion.resumeQuote || '근거 미확인'} — {criterion.explanation}</p>)}
            </div>)}
          </details>}
          <Link className="recommendation-all" to={analysisLink}>{result.id ? '저장된 분석과 AI 코칭 확인' : '이 이력서로 AI 코칭 받기'} <ArrowRight size={16} /></Link>
        </>}
    {reason && <dialog ref={dialogRef} className="recommendation-reason-dialog" aria-labelledby="recommendation-reason-title" onClose={() => setReason(null)}>
      <div className="recommendation-reason-header"><div><h2 id="recommendation-reason-title">추천 근거</h2><p>{reason.job.title}</p></div>
        <button type="button" aria-label="추천 근거 닫기" onClick={() => dialogRef.current?.close()}>×</button></div>
      <div className="recommendation-reason-content">
        {reason.review?.decision && <><strong>{({ RECOMMENDED: 'AI 추천', STRETCH: '도전 지원', NOT_RECOMMENDED: '부적합', INSUFFICIENT_INFORMATION: '정보 부족' })[reason.review.decision]}</strong><p>{reason.review.decisionReason}</p></>}
        <strong>{reason.review ? 'AI 검토' : '기술·경력 기반'}</strong>
        <p>{reason.job.matchPoints?.length ? `일치 조건: ${reason.job.matchPoints.join(', ')}` : '이력서의 희망 직무와 연결되는 공고예요.'}</p>
        {reason.job.missingPoints?.length > 0 && <p>추가 확인: {reason.job.missingPoints.join(', ')}</p>}
        {reason.review && <><strong>{({ RELATED: '연결되는 경험 있음', REVIEW_NEEDED: '지원 전 추가 확인', LOW_RELEVANCE: '관련성 낮음' })[reason.review.relevance] || '공고 요건 확인'}</strong>
          {reason.review.lead_with && <p>{reason.review.lead_with}</p>}{reason.review.gap && <p>확인할 점: {reason.review.gap}</p>}</>}
        {reason.review?.decision && reason.review.criteria?.map((criterion, index) => <div key={index}><p>공고 근거: {criterion.jobQuote}</p><p>이력서 근거: {criterion.resumeQuote || '확인 가능한 내용 없음'}</p><p>{criterion.explanation}</p></div>)}
      </div>
    </dialog>}
    {notice && <p role="status">{notice}</p>}
  </>;
}
