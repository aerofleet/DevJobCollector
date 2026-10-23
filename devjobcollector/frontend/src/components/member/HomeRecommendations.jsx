import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Sparkles } from 'lucide-react';
import { useMemberSession } from '../../contexts/memberSession';
import { getAnalysis, listAnalyses, saveAnalysisFeedback } from '../../api/resumeAnalysisApi';
import './HomeRecommendations.css';

export default function HomeRecommendations() {
  const { token } = useMemberSession();
  return <section className="home-recommendations" aria-labelledby="personal-positions-title">
    <header><div><span className="section-eyebrow"><Sparkles size={16} /> YOUR NEXT POSITION</span>
      <h2 id="personal-positions-title">내 이력서로 찾는 추천 포지션</h2></div>
      <Link to="/resume-analysis">이력서 분석 · AI 코칭 <ArrowRight size={16} /></Link></header>
    {token ? <SavedRecommendations key={token} /> : <div className="recommendation-empty">
      <h3>내 경험과 연결되는 채용공고를 찾아보세요.</h3>
      <p>로그인 후 이력서를 분석하면 기술·경력 조건으로 공고를 추천해요. 별도 동의 후 AI로 경험과 공고 요건도 검토할 수 있어요.</p>
      <Link to="/login?next=%2Fresume-analysis">로그인하고 추천 시작하기 →</Link>
    </div>}
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
          const data = await getAnalysis(item.id);
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

  const hide = async job => {
    setPending(job.jobId); setNotice('');
    try {
      await saveAnalysisFeedback(result.id, job.jobId, 'NOT_INTERESTED');
      setResult(current => ({ ...current, recommendations: current.recommendations.filter(item => item.jobId !== job.jobId) }));
      setNotice('추천에서 제외했어요. 분석 결과에서 다시 노출할 수 있어요.');
    } catch { setNotice('제외하지 못했어요. 다시 시도해 주세요.'); }
    finally { setPending(null); }
  };
  const reviews = result?.aiReview?.status === 'COMPLETED' ? result.aiReview.jobs || [] : [];
  const order = { RELATED: 0, REVIEW_NEEDED: 1, LOW_RELEVANCE: 3 };
  const jobs = [...(result?.recommendations || [])].sort((a, b) =>
    (order[reviews.find(item => item.jobId === a.jobId)?.relevance] ?? 2)
    - (order[reviews.find(item => item.jobId === b.jobId)?.relevance] ?? 2)).slice(0, 6);
  const analysisLink = result ? `/resume-analysis?analysisId=${result.id}` : '/resume-analysis';
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
          <p className="recommendation-caption">{result.createdAt?.slice(0, 10)} 분석 기준 · 이력서를 수정했다면 새로 분석해 주세요. AI 검토 표시는 별도 AI 분석을 완료한 공고에만 표시해요.</p>
          {jobs.length ? <div className="personal-job-grid">{jobs.map(job => {
            const review = reviews.find(item => item.jobId === job.jobId);
            return <article key={job.jobId}>
              <div className="personal-job-top"><span>{review ? 'AI 검토' : '기술·경력 기반'}</span><small>{job.endDate ? `마감 ${job.endDate.slice(0, 10)}` : '마감일 확인'}</small></div>
              <p>{job.companyName}</p><h3><Link to={`/job/${job.jobId}`}>{job.title}</Link></h3>
              <p>{job.location || '지역 확인'} · {job.experience || '경력 조건 확인'}</p>
              <div className="personal-job-tags">{(job.matchPoints || []).slice(0, 3).map((point, index) => <span key={index}>{point}</span>)}</div>
              {review && <div className="personal-job-reason"><strong>{({ RELATED: '연결되는 경험 있음', REVIEW_NEEDED: '지원 전 추가 확인', LOW_RELEVANCE: '관련성 낮음' })[review.relevance] || '공고 요건 확인'}</strong>
                {review.lead_with && <p>{review.lead_with}</p>}{review.gap && <p>확인할 점: {review.gap}</p>}</div>}
              <footer><Link to={analysisLink}>추천 근거 보기</Link><button disabled={pending !== null} onClick={() => hide(job)} aria-label={`${job.title} 추천 제외`}>{pending === job.jobId ? '처리 중…' : '관심 없음'}</button></footer>
            </article>;
          })}</div> : <div className="recommendation-empty"><h3>현재 표시할 추천 공고가 없어요.</h3><p>마감되거나 제외한 공고는 표시하지 않아요. 조건을 보완해 새로 분석하거나 전체 공고를 살펴보세요.</p><Link to="/jobs">전체 채용공고 보기 →</Link></div>}
          <Link className="recommendation-all" to={analysisLink}>추천 전체와 AI 코칭 확인 <ArrowRight size={16} /></Link>
        </>}
    {notice && <p role="status">{notice}</p>}
  </>;
}
