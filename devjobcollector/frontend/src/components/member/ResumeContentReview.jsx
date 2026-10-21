import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { previewContentReview, getContentReview, requestContentReview, saveFindingFeedback } from '../../api/resumeAnalysisApi';

const issueNames = { CLARITY: '설명 구체화', ROLE: '담당 역할', EVIDENCE: '성과 근거', RELEVANCE: '지원 방향' };
const sectionNames = { projects: '프로젝트', experience: '경력', careerProfile: '지원 방향', techStack: '기술 스택' };
const location = item => `${item.label || '저장된 자료'} · ${item.page ? `${item.page}쪽` : sectionNames[item.section] || item.section || '위치 미확인'}`;
const errorMessage = error => {
  const code = error.response?.data?.code;
  if (['ANALYSIS_INPUT_CHANGED', 'PREVIEW_CHANGED'].includes(code)) return '이력서나 추천 자료가 변경되었습니다. 새 분석 결과에서 전송 내용을 다시 확인해 주세요.';
  if (code === 'CONTENT_REVIEW_TOO_LARGE') return '분석할 텍스트가 허용 분량을 초과했습니다. 필요한 경험과 첨부파일을 정리한 뒤 다시 분석해 주세요.';
  if (code === 'NO_RESUME_CONTENT') return '분석할 경험 내용이 없습니다. 프로젝트·경력 설명이나 첨부파일을 추가해 주세요.';
  if (error.response?.status === 429) return '오늘의 AI 요청 한도에 도달했습니다. 저장된 분석 결과를 확인해 주세요.';
  return '요청을 완료하지 못했습니다. 처리 상태를 확인해 주세요. 전송이 시작된 요청은 자동으로 다시 실행하지 않습니다.';
};

export default function ResumeContentReview({ result, disabled, onResult }) {
  const [review, setReview] = useState(result.aiReview || null);
  const [preview, setPreview] = useState(null);
  const [consent, setConsent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    getContentReview(result.id).then(value => { if (active) setReview(value); }).catch(() => {});
    return () => { active = false; };
  }, [result.id]);
  const run = async operation => {
    if (busy) return;
    setBusy(true); setError('');
    try { await operation(); } catch (failure) { setError(errorMessage(failure)); }
    finally { setBusy(false); }
  };
  const sync = value => { setReview(value); onResult(value); };
  const state = review?.status || 'NOT_REQUESTED';
  return <section className="content-review" aria-label="AI 내용 분석">
    <span className="evidence-badge evidence-badge-ai">AI 내용 분석</span>
    <h3>경험을 읽고, 수정 방향을 제안합니다</h3>
    <p>저장된 지원 방향·프로젝트·경력과 첨부 자료를 함께 검토합니다. 개선할 이유와 수정 예시, 추천 공고의 요건과 연결되는 경험을 확인하세요.</p>
    <p className="analysis-note">AI 제안은 검토용 초안입니다. 인용문 일치를 확인해도 해석이나 수정안이 틀릴 수 있으므로 실제 경험과 대조하세요. 이력서는 자동 수정하지 않습니다.</p>
    {error && <p role="alert" className="analysis-error">{error}</p>}
    {!result.inputFingerprint ? <p>이전 분석 기록입니다. 현재 자료로 새 분석을 만들면 AI 내용 분석을 사용할 수 있습니다.</p> : state === 'NOT_REQUESTED' && <>
      {!preview && <button type="button" disabled={disabled || busy} onClick={() => run(async () => { setConsent(false); setPreview(await previewContentReview(result.id)); })}>
        {busy ? '전송할 자료 준비 중…' : 'AI에 전송할 내용 확인'}</button>}
      {preview && <div className="content-review-preview">
        <h4>전송 내용 미리보기</h4>
        <p>아래 텍스트와 공고 정보가 OpenAI로 전송됩니다. 기본정보 필드는 제외하고 알려진 이름·연락처 등을 마스킹했지만, 본문에 남은 개인정보까지 완전히 제거됐다고 보장하지 않습니다. 남아 있는 민감한 정보는 이력서에서 수정한 뒤 다시 분석해 주세요.</p>
        <details open><summary>이력서·첨부 자료 {preview.payload.sources.length}개 구간</summary>
          {preview.payload.sources.map(source => { const ref = preview.sources.find(item => item.id === source.sourceId); return <label key={source.sourceId}>
            {location(ref || {})}<textarea readOnly value={source.text} aria-label={`${source.sourceId} 전송 내용`} /></label>; })}
        </details>
        <details><summary>추천 후보 공고 {preview.payload.jobs.length}개</summary>
          {preview.payload.jobs.map(job => <textarea key={job.slot} readOnly value={job.text} aria-label={`공고 ${job.slot + 1} 전송 내용`} />)}
        </details>
        <label className="analysis-consent"><input type="checkbox" checked={consent} disabled={busy || disabled} onChange={event => setConsent(event.target.checked)} />
          <span>[선택] 위 전송 내용을 확인했으며, AI 내용 분석을 위해 OpenAI로 전송하고 인용문·수정 제안을 포함한 결과를 저장하는 데 동의합니다. 자동 마스킹 후에도 개인정보가 남을 수 있음을 확인했습니다.</span></label>
        <button type="button" disabled={!consent || busy || disabled} onClick={() => run(async () => {
          const value = await requestContentReview(result.id, preview.consentVersion, preview.previewHash); sync(value); setPreview(null); setConsent(false);
        })}>{busy ? 'AI가 경험과 공고를 검토 중…' : '동의하고 AI 내용 분석'}</button>
        <p className="analysis-note">AI 요청은 근거 정리와 합산해 하루 최대 5회, 분석 결과당 내용 분석 1회입니다. 실패한 요청도 포함되며 자동 재시도하지 않습니다.</p>
      </div>}
    </>}
    {['PENDING', 'INTERRUPTED', 'FAILED'].includes(state) && <p role="status">{state === 'PENDING' ? 'AI가 분석 중입니다.' : 'AI 내용 분석을 완료하지 못했습니다. 로컬 점검과 공고 후보는 계속 확인할 수 있습니다.'} 중복 비용 방지를 위해 같은 분석은 다시 전송하지 않습니다.</p>}
    <button className="content-review-refresh" type="button" disabled={busy || disabled} onClick={() => run(async () => sync(await getContentReview(result.id)))}>AI 처리 상태 확인</button>
    {state === 'COMPLETED' && <div>
      <h4>AI가 제안한 수정 방향</h4>
      {!review.findings?.length && <p>원문 근거 검증을 통과한 수정 제안이 없습니다. 이력서에 개선할 점이 전혀 없다는 의미는 아닙니다.</p>}
      {review.findings?.map(finding => <article className="evidence-finding" key={finding.id}>
        <span className="evidence-badge evidence-badge-ai">AI 판단 · {issueNames[finding.issueType] || '보완 제안'}</span>
        <p className="analysis-note">{location(finding)}</p>
        <blockquote><span>원문에서 확인한 인용</span><p><mark>{finding.quote}</mark></p></blockquote>
        <h4>왜 보완하면 좋을까요?</h4><p>{finding.problem}</p>
        <h4>이렇게 수정해 보세요</h4><ol>{finding.actions.map((action, index) => <li key={index}>{action}</li>)}</ol>
        <div className="evidence-template"><strong>수정 초안 · 사실 확인 필요</strong><p>{finding.rewriteTemplate}</p></div>
        <div className="evidence-feedback" role="group" aria-label="AI 제안 피드백">
          {Object.entries({ APPLIED: '반영했어요', NOT_APPLICABLE: '해당 없음', INCORRECT: '이 지적이 틀렸어요' }).map(([type, label]) => <button key={type} type="button" aria-pressed={finding.feedback === type} disabled={busy || disabled}
            onClick={() => run(async () => { const next = finding.feedback === type ? 'OPEN' : type;
              await saveFindingFeedback(result.id, finding.id, next); sync({ ...review, findings: review.findings.map(item => item.id === finding.id ? { ...item, feedback: next } : item) });
            })}>{label}</button>)}
        </div>
        <Link to={`/resume?resumeId=${result.resumeId}`}>이력서에서 확인하고 수정하기 →</Link>
      </article>)}
      <p className="analysis-note">공고별 AI 검토 근거는 아래 추천 공고에 표시합니다. 수집된 공고 일부만 검토하며 전체 지원 자격이나 채용 가능성을 보장하지 않습니다.</p>
    </div>}
  </section>;
}

export function AiJobReview({ review }) {
  if (!review) return null;
  const names = { RELATED: '연결되는 경험 있음', REVIEW_NEEDED: '추가 확인 필요', LOW_RELEVANCE: '관련성 낮음' };
  return <div className="ai-job-review"><strong><span className="evidence-badge evidence-badge-ai">AI 판단</span> {names[review.relevance]}</strong>
    {review.criteria.map((criterion, index) => <div key={index}>
      <p><strong>공고 근거</strong> “{criterion.jobQuote}”</p>
      {criterion.resumeQuote ? <p><strong>이력서 근거</strong> “{criterion.resumeQuote}”<small>{location(criterion)}</small></p> : <p>자료에서 대응하는 근거 미확인</p>}
      <p>{criterion.explanation}</p><span>{criterion.assessment === 'SUPPORTED' ? '문장 근거 확인 · 자격 충족 보증 아님' : '지원 전 확인할 항목'}</span>
    </div>)}
  </div>;
}
