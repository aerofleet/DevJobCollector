import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import MemberSidebar from '../components/member/MemberSidebar';
import ResumeEvidenceReport, { JobEvidence } from '../components/member/ResumeEvidenceReport';
import ResumeWritingSuggestions from '../components/member/ResumeWritingSuggestions';
import { listResumes } from '../api/resumeApi';
import authenticatedApi from '../api/authenticatedApi';
import {
  getAnalysisCapabilities, listAnalyses, getAnalysis, createAnalysis, deleteAnalysis, saveAnalysisFeedback,
  getExplanation, requestExplanation, saveFindingFeedback,
} from '../api/resumeAnalysisApi';
import '../styles/MemberPages.css';
import '../styles/ResumeAnalysis.css';

const feedbackOptions = {
  MATCHED: '추천이 맞음', CAREER_MISMATCH: '경력 불일치', SKILL_MISMATCH: '기술 스택 불일치',
  LOCATION_MISMATCH: '지역 불일치', ALREADY_SEEN: '이미 본 공고', NOT_INTERESTED: '관심 없는 직무', INACCURATE_JOB: '공고 내용 부정확',
};
const explainError = (error, files = []) => {
  const status = error.response?.status;
  if (error.response?.data?.code === 'ATTACHMENT_READ_FAILED') {
    const file = files.find((item) => item.id === error.response.data.attachmentId);
    return `${file ? `“${file.filename}”` : '첨부파일'}을 읽지 못했습니다. 파일 상태를 확인하거나 잠시 후 다시 시도해 주세요. 첨부파일을 제외한 결과는 만들지 않았습니다.`;
  }
  if (status === 429) return '오늘의 분석 또는 요청 한도에 도달했습니다. 저장된 결과를 확인하거나 내일 다시 시도해 주세요.';
  if (status === 404) return '이력서 또는 분석 결과를 찾을 수 없습니다. 삭제 여부를 확인해 주세요.';
  if (status === 409) return '요청 내용이 변경되었습니다. 조건을 확인한 뒤 다시 시도해 주세요.';
  if (status === 422) return '첨부파일을 읽지 못했거나 분석 가능한 분량을 초과했습니다. 이력서의 첨부파일을 확인해 주세요. 파일을 제외한 결과로 처리하지 않았습니다.';
  return '요청을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.';
};

export default function ResumeAnalysisPage() {
  const [params] = useSearchParams();
  const [capabilities, setCapabilities] = useState(null);
  const [resumes, setResumes] = useState([]);
  const [history, setHistory] = useState([]);
  const [resumeId, setResumeId] = useState(params.get('resumeId') || '');
  const [attachmentState, setAttachmentState] = useState({ resumeId: '', files: [], failed: false });
  const [consent, setConsent] = useState(false);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);
  const [busy, setBusy] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);
  const [error, setError] = useState('');
  const [externalConsent, setExternalConsent] = useState(false);
  const [explanation, setExplanation] = useState(null);
  const retry = useRef(null);
  const running = useRef(false);

  useEffect(() => {
    if (!resumeId) return;
    let cancelled = false;
    authenticatedApi.get(`/members/me/resumes/${resumeId}/attachments`).then(({ data }) => {
      if (!cancelled) setAttachmentState({ resumeId, files: data, failed: false });
    }).catch(() => {
      if (!cancelled) setAttachmentState({ resumeId, files: [], failed: true });
    });
    return () => { cancelled = true; };
  }, [resumeId]);

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
    catch (failure) { if (failure.response?.status !== 401) setError(explainError(failure, attachmentState.files)); }
    finally { running.current = false; setBusy(false); }
  };

  const analyze = (event) => {
    event.preventDefault();
    if (!consent || !resumeId || !capabilities) return;
    const body = { resumeId: Number(resumeId), consentVersion: capabilities.consentVersion };
    const fingerprint = JSON.stringify(body);
    if (retry.current?.fingerprint !== fingerprint) retry.current = { fingerprint, key: crypto.randomUUID() };
    perform(async () => {
      setResult(null);
      setAnalyzing(true);
      try { setResult(await createAnalysis(body, retry.current.key)); }
      finally { setAnalyzing(false); }
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
          <p>저장된 이력서와 첨부파일로 시작하세요. 같은 정보를 다시 입력할 필요 없어요.</p>
        </header>
        {loading && <p role="status">분석 화면을 불러오는 중입니다.</p>}
        {unavailable && <section className="analysis-panel"><h2>기능을 준비하고 있습니다.</h2><Link to="/resumes">이력서 관리로 돌아가기</Link></section>}
        {error && <p role="alert" className="analysis-error">{error}</p>}
        {!loading && capabilities && <>
          <section className="analysis-panel">
            <h2>분석할 이력서</h2>
            <p>저장된 이력서와 첨부 PDF·이미지로 보완 후보와 공고 추천 근거를 확인하세요. AI 근거 정리는 결과에서 선택할 수 있습니다.</p>
            {!resumes.length ? <p>먼저 이력서를 작성해 주세요. <Link to="/resume">이력서 작성</Link></p> :
              <form onSubmit={analyze}>
                <div className="analysis-fields">
                  <label>이력서<select value={resumeId} onChange={(e) => { setResumeId(e.target.value); setResult(null); setConsent(false); setError(''); }} disabled={busy}>
                    {resumes.map((resume) => <option key={resume.id} value={resume.id}>{resume.title}</option>)}
                  </select></label>
                </div>
                <div className="analysis-sources">
                  <strong>함께 분석하는 자료</strong>
                  <p>저장된 이력서 · 기술 스택 · 프로젝트 · 경력</p>
                  {attachmentState.resumeId !== resumeId ? <p role="status">첨부파일을 확인하는 중입니다.</p>
                    : attachmentState.failed ? <p>첨부 목록을 표시하지 못했습니다. 분석할 때 서버에서 다시 확인합니다.</p>
                    : attachmentState.files.length ? <ul>{attachmentState.files.map((file) => <li key={file.id}>{file.filename}</li>)}</ul>
                    : <p>첨부파일 없음 · 저장된 이력서만으로 분석합니다.</p>}
                  <Link to={`/resume?resumeId=${resumeId}`}>이력서·첨부파일 수정</Link>
                </div>
                <label className="analysis-consent"><input type="checkbox" checked={consent} disabled={busy}
                  onChange={(e) => setConsent(e.target.checked)} />
                  <span>[선택] 저장된 이력서와 첨부파일의 서비스 내부 분석 및 원문 인용문을 포함한 결과 저장에 동의합니다. 원문은 외부 AI에 전송하지 않으며, 결과는 본인만 조회하고 언제든 삭제할 수 있습니다.</span>
                </label>
                <p className="analysis-note">새로운 분석은 하루 최대 {capabilities.dailyLimit}회입니다. 같은 자료의 유효한 결과는 재사용합니다. 첨부파일은 파일당 5MB, PDF는 10쪽까지 읽습니다. 스캔·이미지는 글자 인식 상태에 따라 읽지 못할 수 있습니다.</p>
                <button className="primary-member-button" type="submit" disabled={!consent || busy || !resumeId}>
                  {busy ? '처리 중…' : '분석하기'}
                </button>
              </form>}
          </section>
          {analyzing && <section className="analysis-panel analysis-progress" role="status" aria-live="polite">
            <span className="evidence-eyebrow">REVIEW IN PROGRESS</span><h2>자료를 분석하고 있습니다</h2>
            <ol><li>선택한 이력서와 분석 동의 확인 완료</li><li aria-current="step">서버에서 자료 추출·규칙 검사·공고 조회 처리 중</li><li>결과 응답 대기</li></ol>
            <p>응답을 받으면 확인된 근거를 표시합니다. 지금은 내부 단계별 진행률을 제공하지 않습니다.</p>
          </section>}
          {result && <section className="analysis-panel" aria-label="분석 결과" aria-live="polite">
            <div className="analysis-heading"><h2>분석 결과</h2><button type="button" disabled={busy} onClick={() => remove(result.id)}>결과 삭제</button></div>
            <p className="analysis-note">이력서 점검은 서비스 내부 규칙 검사입니다. AI는 별도 동의 후 추천 근거를 선택하며, 원문을 읽거나 사실 여부를 검증하지 않습니다.</p>
            {result.inputMode === 'SAVED_RESUME' && <div className="analysis-sources">
              <strong>이번 분석에 사용한 자료</strong>
              <p>저장된 이력서{result.sources?.length ? ` + 첨부파일 ${result.sources.length}개` : ''}</p>
              {!!result.sources?.length && <ul>{result.sources.map((file) => <li key={file.id}>{file.filename} · 읽기 완료</li>)}</ul>}
              <p>확인된 기술: {result.detectedSkills?.join(', ') || '확인되지 않음'}</p>
              <p>저장된 경력 기간: {result.derivedPreferences?.careerYears == null ? '확인되지 않음' : `${result.derivedPreferences.careerYears}년 (완료된 기간 기준)`}</p>
              <p className="analysis-note">희망 지역·고용 형태는 추정하지 않습니다. 첨부파일 인용은 추출 텍스트 기준이므로 원본과 함께 확인해 주세요.</p>
            </div>}
            <ResumeEvidenceReport result={result} busy={busy} onFeedback={(findingId, type) => perform(async () => {
              await saveFindingFeedback(result.id, findingId, type);
              setResult((current) => ({ ...current, review: { ...current.review, findings: current.review.findings.map((finding) =>
                finding.id === findingId ? { ...finding, feedback: type } : finding) } }));
            })} />
            {(capabilities.externalProcessing || currentExplanation?.status === 'COMPLETED') && <div className="analysis-explanation">
              <h3>AI 추천 근거 정리</h3>
              <p className="analysis-note">AI가 확인된 근거 중 강조할 항목을 선택합니다.
                문장은 검증된 안내 문구로 표시하며, 추천 순위는 바뀌지 않습니다. AI의 선택은 정답이나 채용 가능성을 보장하지 않습니다.</p>
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
            <ResumeWritingSuggestions result={result} />
            <h3>추천 공고</h3>
            {!result.recommendations.length && <p>현재 자료와 연결할 수 있는 공개 공고를 찾지 못했습니다. 수집된 기술 정보가 부족하거나 공고가 마감되었을 수 있습니다. 지원 가능한 공고가 전혀 없다는 뜻은 아닙니다.</p>}
            {!result.recommendations.length && <Link className="evidence-browse" to="/jobs">전체 공개 공고 둘러보기 →</Link>}
            <div className="analysis-jobs">{result.recommendations.map((job) => <article key={job.jobId} className="analysis-job">
              <span className="analysis-score">{job.rank}번째 추천 · 근거 확인</span>
              <h4><Link to={`/job/${job.jobId}`}>{job.title}</Link></h4>
              <p>{job.companyName} · {job.location || '지역 미기재'} · {job.experience || '경력 미기재'}</p>
              <JobEvidence job={job} review={result.review} />
              {currentExplanation?.jobs?.find((item) => item.jobId === job.jobId)?.highlights?.length > 0 &&
                <div><strong><span className="evidence-badge evidence-badge-ai">AI 판단</span> AI가 선택한 추천 근거</strong><ul>{currentExplanation.jobs.find((item) => item.jobId === job.jobId).highlights.map((text) =>
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
