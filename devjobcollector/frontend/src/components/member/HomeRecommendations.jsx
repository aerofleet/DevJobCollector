import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Sparkles } from 'lucide-react';
import { useMemberSession } from '../../contexts/memberSession';
import { getCurrentRecommendations, listAnalyses, saveCurrentRecommendationFeedback } from '../../api/resumeAnalysisApi';
import './HomeRecommendations.css';

export default function HomeRecommendations() {
  const { token } = useMemberSession();
  if (!token) return <section className="home-recommendations home-recommendations-guest" aria-labelledby="personal-positions-title">
    <h2 id="personal-positions-title">나에게 맞는 추천 포지션을 찾고 있다면?</h2>
    <Link className="recommendation-login-banner" to="/login?next=%2Fresume-analysis">
      <span className="recommendation-login-message"><span aria-hidden="true">👋</span><span>로그인하고 내 이력서에 맞는 포지션을 추천받아보세요!</span></span>
      <span className="recommendation-login-action">회원가입/로그인 <ArrowRight size={16} aria-hidden="true" /></span>
    </Link>
  </section>;
  return <section className="home-recommendations" aria-labelledby="personal-positions-title">
    <header><div><span className="section-eyebrow"><Sparkles size={16} /> YOUR NEXT POSITION</span>
      <h2 id="personal-positions-title">내 이력서로 찾는 추천 포지션</h2></div>
      <Link to="/resume-analysis">이력서 분석 · AI 코칭 <ArrowRight size={16} /></Link></header>
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

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true); setError(''); setResult(null); setNotice('');
      try {
        const items = await listAnalyses();
        const available = items.filter(item => item.status === 'COMPLETED_LOCAL');
        if (cancelled) return;
        setHistory(available);
        const item = available.find(item => String(item.id) === selected) || available[0];
        if (item) {
          const data = await getCurrentRecommendations(item.id);
          if (!cancelled) setResult(data);
        }
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
    - (order[reviews.find(item => item.jobId === b.jobId)?.relevance] ?? 2)).slice(0, 6);
  const analysisLink = result ? `/resume-analysis?analysisId=${result.id}` : '/resume-analysis';
  const emptyMessage = ({
    MISSING_CRITERIA: '추천 기준이 부족해요. 이력서의 희망 직무·기술을 입력하고 새로 분석해 주세요.',
    ALL_EXCLUDED: '조건에 맞는 현재 공고를 모두 제외했어요. 아래에서 제외를 해제하거나 전체 공고를 살펴보세요.',
    NO_CURRENT_MATCHES: '현재 조회한 공고 중 기술 또는 직무가 연결되는 공고를 찾지 못했어요. 희망 직무를 확인하거나 전체 공고를 살펴보세요.',
  })[result?.recommendationStatus?.reason] || '현재 추천 조건에 맞는 공고가 없어요. 추천 기준을 확인하거나 전체 공고를 살펴보세요.';
  return <>
    {history.length > 0 && <div className="recommendation-controls"><label>추천 기준 이력서
      <select value={selected || String(history[0].id)} disabled={loading || pending !== null} onChange={event => setSelected(event.target.value)}>
        {history.map(item => <option key={item.id} value={String(item.id)}>{item.resumeTitle} · {item.createdAt?.slice(0, 10)}</option>)}
      </select></label><Link to="/resume-analysis">현재 이력서로 새로 분석 →</Link></div>}
    {loading ? <p role="status">이력서 추천을 불러오는 중이에요.</p> : error ? <div role="alert"><p>{error}</p>
      <button onClick={() => setAttempt(value => value + 1)}>다시 시도</button> <Link to="/resume-analysis">이력서 분석 열기</Link></div>
      : !result ? <div className="recommendation-empty"><h3>첫 추천을 받아보세요.</h3><p>작성한 이력서를 선택하고 분석에 동의하면 추천이 시작돼요.</p>
        <Link to="/resume-analysis">이력서 선택하고 추천받기 →</Link> <Link to="/resume">새 이력서 작성</Link></div>
        : <>
          <p className="recommendation-caption">{result.createdAt?.slice(0, 10)} 분석의 기술·경력을 바탕으로 현재 공고를 추천해요. 새 공고의 AI 검토는 별도 분석이 필요해요.
            {result.recommendationStatus?.roleSource === 'RESUME_TITLE' && <> 직무는 현재 이력서 제목에서 확인했어요: {result.recommendationStatus.categories.join(', ')}.</>}
            {' '}<Link to={`/resume?resumeId=${result.resumeId}`}>추천 기준 이력서 확인</Link></p>
          {jobs.length ? <div className="personal-job-grid">{jobs.map(job => {
            const review = reviews.find(item => item.jobId === job.jobId);
            return <article key={job.jobId}>
              <div className="personal-job-top"><span>{review ? 'AI 검토' : '기술·경력 기반'}</span><small>{job.endDate ? `마감 ${job.endDate.slice(0, 10)}` : '마감일 확인'}</small></div>
              <p>{job.companyName}</p><h3><Link to={`/job/${job.jobId}`}>{job.title}</Link></h3>
              <p>{job.location || '지역 확인'} · {job.experience || '경력 조건 확인'}</p>
              <div className="personal-job-tags">{(job.matchPoints || []).slice(0, 3).map((point, index) => <span key={index}>{point}</span>)}</div>
              {review && <div className="personal-job-reason"><strong>{({ RELATED: '연결되는 경험 있음', REVIEW_NEEDED: '지원 전 추가 확인', LOW_RELEVANCE: '관련성 낮음' })[review.relevance] || '공고 요건 확인'}</strong>
                {review.lead_with && <p>{review.lead_with}</p>}{review.gap && <p>확인할 점: {review.gap}</p>}</div>}
              <footer><Link to={job.supplemented ? `/job/${job.jobId}` : analysisLink}>{job.supplemented ? '공고 자세히 보기' : '추천 근거 보기'}</Link><button disabled={pending !== null} onClick={() => feedback(job, 'NOT_INTERESTED')} aria-label={`${job.title} 추천 제외`}>{pending === job.jobId ? '처리 중…' : '관심 없음'}</button></footer>
            </article>;
          })}</div> : <div className="recommendation-empty"><h3>현재 표시할 추천 공고가 없어요.</h3><p>{emptyMessage}</p><Link to="/jobs">전체 채용공고 보기 →</Link></div>}
          {result.excludedRecommendations?.length > 0 && <details className="recommendation-excluded"><summary>제외한 공고 {result.excludedRecommendations.length}개</summary>
            <ul>{result.excludedRecommendations.map(job => <li key={job.jobId}><Link to={`/job/${job.jobId}`}>{job.title}</Link>
              <button disabled={pending !== null} onClick={() => feedback(job, 'MATCHED')} aria-label={`${job.title} 제외 해제`}>제외 해제</button></li>)}</ul>
          </details>}
          <Link className="recommendation-all" to={analysisLink}>저장된 분석과 AI 코칭 확인 <ArrowRight size={16} /></Link>
        </>}
    {notice && <p role="status">{notice}</p>}
  </>;
}
