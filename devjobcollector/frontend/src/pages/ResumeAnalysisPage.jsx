import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import MemberSidebar from '../components/member/MemberSidebar';
import { listResumes } from '../api/resumeApi';
import {
  getAnalysisCapabilities, listAnalyses, getAnalysis, createAnalysis, deleteAnalysis, saveAnalysisFeedback,
  getExplanation, requestExplanation,
} from '../api/resumeAnalysisApi';
import '../styles/MemberPages.css';
import '../styles/ResumeAnalysis.css';

const categories = ['백엔드', '프론트엔드', '풀스택', '데이터', '인공지능', '모바일', '보안', 'DevOps'];
const locations = ['서울', '경기', '인천', '부산', '대구', '대전', '광주', '울산', '세종', '강원', '충북', '충남', '전북', '전남', '경북', '경남', '제주', '원격'];
const feedbackOptions = {
  MATCHED: '추천이 맞음', CAREER_MISMATCH: '경력 불일치', SKILL_MISMATCH: '기술 스택 불일치',
  LOCATION_MISMATCH: '지역 불일치', ALREADY_SEEN: '이미 본 공고', NOT_INTERESTED: '관심 없는 직무', INACCURATE_JOB: '공고 내용 부정확',
};
const explainError = (error) => {
  const status = error.response?.status;
  if (status === 429) return '오늘의 분석 또는 요청 한도에 도달했습니다. 저장된 결과를 확인하거나 내일 다시 시도해 주세요.';
  if (status === 404) return '이력서 또는 분석 결과를 찾을 수 없습니다. 삭제 여부를 확인해 주세요.';
  if (status === 409) return '요청 내용이 변경되었습니다. 조건을 확인한 뒤 다시 시도해 주세요.';
  return '요청을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.';
};

export default function ResumeAnalysisPage() {
  const [params] = useSearchParams();
  const [capabilities, setCapabilities] = useState(null);
  const [resumes, setResumes] = useState([]);
  const [history, setHistory] = useState([]);
  const [resumeId, setResumeId] = useState(params.get('resumeId') || '');
  const [preferences, setPreferences] = useState({ careerYears: '0', category: '', location: '', employmentType: '' });
  const [consent, setConsent] = useState(false);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [externalConsent, setExternalConsent] = useState(false);
  const [explanation, setExplanation] = useState(null);
  const retry = useRef(null);
  const running = useRef(false);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const caps = await getAnalysisCapabilities();
        const [resumeList, analysisList] = await Promise.all([listResumes(), listAnalyses()]);
        if (cancelled) return;
        setCapabilities(caps);
        setResumes(resumeList);
        setHistory(analysisList);
        setResumeId((current) => resumeList.some((resume) => String(resume.id) === current) ? current : String(resumeList[0]?.id || ''));
      } catch (failure) {
        if (cancelled) return;
        if (failure.response?.status === 404) setUnavailable(true);
        else if (failure.response?.status !== 401) setError(explainError(failure));
      } finally { if (!cancelled) setLoading(false); }
    };
    load();
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    let cancelled = false;
    if (result?.id) {
      getExplanation(result.id).then((value) => {
        if (!cancelled) setExplanation(value);
      }).catch(() => {});
    }
    return () => { cancelled = true; };
  }, [result?.id]);

  const currentExplanation = explanation?.analysisId === result?.id ? explanation : null;

  const perform = async (operation) => {
    if (running.current) return;
    running.current = true;
    setBusy(true); setError('');
    try { await operation(); }
    catch (failure) { if (failure.response?.status !== 401) setError(explainError(failure)); }
    finally { running.current = false; setBusy(false); }
  };

  const analyze = (event) => {
    event.preventDefault();
    if (!consent || !resumeId || !capabilities) return;
    const body = { resumeId: Number(resumeId), ...preferences, careerYears: Number(preferences.careerYears), consentVersion: capabilities.consentVersion };
    const fingerprint = JSON.stringify(body);
    if (retry.current?.fingerprint !== fingerprint) retry.current = { fingerprint, key: crypto.randomUUID() };
    perform(async () => {
      const response = await createAnalysis(body, retry.current.key);
      setResult(response);
      retry.current = null;
      setHistory(await listAnalyses());
    });
  };

  const remove = (id) => {
    if (!window.confirm('이 분석 결과와 추천 피드백을 삭제하시겠습니까? 이력서는 유지됩니다.')) return;
    perform(async () => {
      await deleteAnalysis(id);
      setHistory((items) => items.filter((item) => item.id !== id));
      setResult((current) => current?.id === id ? null : current);
    });
  };

  return <main className="member-page">
    <div className="member-layout">
      <MemberSidebar />
      <div className="member-main resume-analysis">
        <header className="member-page-heading">
          <span className="member-eyebrow">RESUME REVIEW</span>
          <h1>이력서 점검 · 맞춤 공고</h1>
          <p>작성한 이력서의 기술 스택과 희망 조건을 바탕으로 지원할 공고를 찾아보세요.</p>
        </header>
        {loading && <p role="status">분석 화면을 불러오는 중입니다.</p>}
        {unavailable && <section className="analysis-panel"><h2>기능을 준비하고 있습니다.</h2><Link to="/resumes">이력서 관리로 돌아가기</Link></section>}
        {error && <p role="alert" className="analysis-error">{error}</p>}
        {!loading && capabilities && <>
          <section className="analysis-panel">
            <h2>분석 조건</h2>
            <p>현재는 서비스 내부에서 처리하는 로컬 분석입니다. 외부 AI에 이력서 내용을 전송하지 않습니다.</p>
            {!resumes.length ? <p>먼저 이력서를 작성해 주세요. <Link to="/resume">이력서 작성</Link></p> :
              <form onSubmit={analyze}>
                <div className="analysis-fields">
                  <label>이력서<select value={resumeId} onChange={(e) => setResumeId(e.target.value)} disabled={busy}>
                    {resumes.map((resume) => <option key={resume.id} value={resume.id}>{resume.title}</option>)}
                  </select></label>
                  <label>경력 연수<input type="number" min="0" max="60" required value={preferences.careerYears} disabled={busy}
                    onChange={(e) => setPreferences({ ...preferences, careerYears: e.target.value })} /></label>
                  {[
                    ['category', '희망 직무', categories], ['location', '희망 지역', locations],
                    ['employmentType', '고용 형태', ['정규직', '계약직', '인턴', '프리랜서']],
                  ].map(([field, label, options]) => <label key={field}>{label}<select value={preferences[field]} disabled={busy}
                    onChange={(e) => setPreferences({ ...preferences, [field]: e.target.value })}>
                    <option value="">선택 안 함</option>{options.map((option) => <option key={option}>{option}</option>)}
                  </select></label>)}
                </div>
                <label className="analysis-consent"><input type="checkbox" checked={consent} disabled={busy}
                  onChange={(e) => setConsent(e.target.checked)} />
                  <span>[선택] 이력서 점검·추천을 위한 서비스 내부 처리와 결과 저장에 동의합니다. 분석 결과는 본인만 조회하며 언제든 삭제할 수 있습니다.</span>
                </label>
                <p className="analysis-note">새로운 분석은 하루 최대 {capabilities.dailyLimit}회입니다. 같은 조건의 유효한 결과는 재사용합니다. 첨부파일은 이 분석에 포함되지 않습니다.</p>
                <button className="primary-member-button" type="submit" disabled={!consent || busy || !resumeId}>
                  {busy ? '처리 중…' : '분석하기'}
                </button>
              </form>}
          </section>
          {result && <section className="analysis-panel" aria-label="분석 결과" aria-live="polite">
            <div className="analysis-heading"><h2>분석 결과</h2><button type="button" disabled={busy} onClick={() => remove(result.id)}>결과 삭제</button></div>
            <p className="analysis-note">로컬 규칙 기반 결과입니다. 점수는 합격 확률이 아니며, 확인되지 않은 조건은 점수를 부여하지 않습니다. 임베딩 유사도는 아직 반영하지 않습니다.</p>
            {(capabilities.externalProcessing || currentExplanation?.status === 'COMPLETED') && <div className="analysis-explanation">
              <h3>AI 추천 근거 정리</h3>
              <p className="analysis-note">OpenAI {capabilities.externalModel || 'gpt-5-nano'}가 확인된 근거 중 강조할 항목을 선택합니다.
                문장은 검증된 안내 문구로 표시하며, 추천 점수와 순위는 바뀌지 않습니다.</p>
              {(!currentExplanation || currentExplanation.status === 'NOT_REQUESTED') && <>
                <label className="analysis-consent"><input type="checkbox" checked={externalConsent} disabled={busy}
                  onChange={(event) => setExternalConsent(event.target.checked)} />
                  <span>[선택] 추천 근거 정리를 위해 기술·경력·지역 등의 일치 여부와 확인 필요 여부를 나타내는 코드가 OpenAI로 전송되는 데 동의합니다.
                    이름, 연락처, 이력서·공고 원문, 기술명, 회원·공고 ID는 전송하지 않습니다.</span>
                </label>
                <button type="button" disabled={busy || !externalConsent || !result.recommendations.length}
                  onClick={() => perform(async () => setExplanation(await requestExplanation(result.id, capabilities.externalConsentVersion)))}>
                  AI로 추천 근거 정리
                </button>
                <p className="analysis-note">회원당 하루 5회, 분석 결과당 1회 요청할 수 있습니다. 실패 시 자동 재호출하지 않습니다.</p>
              </>}
              {currentExplanation?.status === 'PENDING' && <p>처리 중입니다. <button type="button" disabled={busy}
                onClick={() => perform(async () => setExplanation(await getExplanation(result.id)))}>처리 상태 확인</button></p>}
              {['FAILED', 'INTERRUPTED'].includes(currentExplanation?.status) &&
                <p role="status">AI 근거 정리를 완료하지 못했습니다. 아래 로컬 분석 결과는 계속 사용할 수 있습니다. 중복 비용 방지를 위해 이 요청은 다시 호출하지 않습니다.</p>}
              {currentExplanation?.status === 'COMPLETED' && <p role="status">AI가 선택한 근거를 각 추천 공고에 표시했습니다.</p>}
            </div>}
            <h3>이력서 보완 항목</h3>
            {result.improvements.length ? <ul className="analysis-issues">{result.improvements.map((item) =>
              <li key={`${item.sourcePath}-${item.issueType}`}>{item.feedback} <Link to={`/resume?resumeId=${result.resumeId}`}>이력서 수정</Link></li>)}</ul> :
              <p>현재 점검 규칙에서 발견한 보완 항목이 없습니다. 이력서의 완성도를 보장하는 결과는 아닙니다.</p>}
            <h3>추천 공고</h3>
            {!result.recommendations.length && <p>현재 조건에 맞는 공개 공고가 없습니다. 기술 스택이나 희망 직무를 확인해 주세요.</p>}
            <div className="analysis-jobs">{result.recommendations.map((job) => <article key={job.jobId} className="analysis-job">
              <span className="analysis-score">{job.rank}순위 · 일치 점수 {job.matchScore}</span>
              <h4><Link to={`/job/${job.jobId}`}>{job.title}</Link></h4>
              <p>{job.companyName} · {job.location || '지역 미기재'} · {job.experience || '경력 미기재'}</p>
              <p><strong>일치 기술:</strong> {job.matchPoints.join(', ') || '확인된 일치 기술 없음'}</p>
              <p><strong>추가 확인할 기술:</strong> {job.missingPoints.join(', ') || '확인된 요구 기술 내 추가 항목 없음'}</p>
              {currentExplanation?.jobs?.find((item) => item.jobId === job.jobId)?.highlights?.length > 0 &&
                <div><strong>AI가 선택한 추천 근거</strong><ul>{currentExplanation.jobs.find((item) => item.jobId === job.jobId).highlights.map((text) =>
                  <li key={text}>{text}</li>)}</ul></div>}
              <label>추천 피드백<select value={job.feedback || ''} disabled={busy} onChange={(e) => {
                const type = e.target.value;
                if (!type) return;
                perform(async () => {
                  await saveAnalysisFeedback(result.id, job.jobId, type);
                  setResult((current) => ({ ...current, recommendations: current.recommendations.map((item) =>
                    item.jobId === job.jobId ? { ...item, feedback: type } : item) }));
                });
              }}><option value="">선택해 주세요</option>{Object.entries(feedbackOptions).map(([value, label]) =>
                <option key={value} value={value}>{label}</option>)}</select></label>
            </article>)}</div>
          </section>}
          <section className="analysis-panel">
            <h2>최근 분석 기록</h2>
            {!history.length ? <p>저장된 분석 결과가 없습니다.</p> :
              <ul className="analysis-history">{history.map((item) => <li key={item.id}>
                <button type="button" disabled={busy} onClick={() => perform(async () => setResult(await getAnalysis(item.id)))}>
                  {item.resumeTitle} · {item.createdAt.slice(0, 10)}
                </button>
                <button type="button" aria-label={`${item.resumeTitle} 분석 결과 삭제`} disabled={busy} onClick={() => remove(item.id)}>삭제</button>
              </li>)}</ul>}
          </section>
        </>}
      </div>
    </div>
  </main>;
}
