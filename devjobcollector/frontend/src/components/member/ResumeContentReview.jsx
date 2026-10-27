import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { confirmContentReview, previewContentReview, getContentReview, requestContentReview, saveFindingFeedback } from '../../api/resumeAnalysisApi';

const sectionNames = { projects: '프로젝트', experience: '경력', careerProfile: '지원 방향', techStack: '기술 스택', introduction: '간단소개', education: '학력', training: '교육이력', activities: '기타사항', coverLetter: '자기소개서' };
const location = item => `${item.label || '저장된 자료'} · ${item.page ? `${item.page}쪽` : sectionNames[item.section] || item.section || '위치 미확인'}`;
const errorMessage = error => {
  const code = error.response?.data?.code;
  if (['ANALYSIS_INPUT_CHANGED', 'PREVIEW_CHANGED'].includes(code)) return '이력서나 추천 자료가 변경되었습니다. 새 분석 결과에서 전송 내용을 다시 확인해 주세요.';
  if (code === 'CONTENT_REVIEW_TOO_LARGE') return '분석할 텍스트가 허용 분량을 초과했습니다. 필요한 경험과 첨부파일을 정리한 뒤 다시 분석해 주세요.';
  if (code === 'NO_RESUME_CONTENT') return '분석할 경험 내용이 없습니다. 프로젝트·경력 설명이나 첨부파일을 추가해 주세요.';
  if (error.response?.status === 429) return '오늘의 AI 요청 한도에 도달했습니다. 저장된 분석 결과를 확인해 주세요.';
  return '요청 처리 상태를 확인해 주세요. 저장된 결과에서 이어서 진행할 수 있어요.';
};

export default function ResumeContentReview({ result, disabled, onResult }) {
  const [review, setReview] = useState(result.aiReview || null);
  const [preview, setPreview] = useState(null);
  const [consent, setConsent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const onResultRef = useRef(onResult);
  useEffect(() => { onResultRef.current = onResult; }, [onResult]);
  useEffect(() => {
    let active = true;
    getContentReview(result.id).then(value => { if (active) { setReview(value); onResultRef.current(value); } }).catch(() => {});
    return () => { active = false; };
  }, [result.id]);
  const run = async operation => {
    if (busy) return;
    setBusy(true); setError('');
    try { await operation(); } catch (failure) { setError(errorMessage(failure)); }
    finally { setBusy(false); }
  };
  const sync = value => { setReview(value); onResultRef.current(value); };
  const state = review?.status || 'NOT_REQUESTED';
  return <section id="ai-content-review" className="content-review" aria-label="AI 내용 분석">
    <h3>이렇게 고쳐보세요</h3>
    {state !== 'COMPLETED' && <p>내 자료로 채운 완성 문장과 공고별 어필법을 받아보세요.</p>}
    {error && <p role="alert" className="analysis-error">{error}</p>}
    {state === 'COMPLETED' && result.inputFingerprint && <button type="button" disabled={disabled || busy} onClick={() => run(async () => { setConsent(false); setPreview(await previewContentReview(result.id)); })}>공고 적합성 재검토 내용 확인</button>}
    {review?.fitReviewRequired && <p role="status">저장된 적합성 판정을 다시 확인해야 해요. 이력서를 수정했다면 새 분석에서, 공고가 바뀌었다면 전송 내용 확인 후 재검토해 주세요.</p>}
    {!result.inputFingerprint ? <p>이전 분석 기록입니다. 현재 자료로 새 분석을 만들면 AI 내용 분석을 사용할 수 있습니다.</p> : (state === 'NOT_REQUESTED' || (state === 'COMPLETED' && preview)) && <>
      {!preview && <button type="button" disabled={disabled || busy} onClick={() => run(async () => { setConsent(false); setPreview(await previewContentReview(result.id)); })}>
        {busy ? '전송할 자료 준비 중…' : 'AI에 전송할 내용 확인'}</button>}
      {preview && <div className="content-review-preview">
        <h4>전송 내용 미리보기</h4>
        <p>아래 텍스트와 공고 정보가 OpenAI로 전송됩니다. 이름·연락처는 자동 마스킹했어요. 전송 전에 본문에 남은 개인정보를 확인하고, 필요한 부분은 이력서에서 수정해 주세요.</p>
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
        <p className="analysis-note">AI 요청은 근거 정리와 합산해 하루 최대 5회입니다. 같은 전송 내용은 저장된 결과를 사용하고, 변경된 내용은 동의 후 다시 검토합니다. 실패한 요청도 포함되며 자동 재시도하지 않습니다.</p>
      </div>}
    </>}
    {['PENDING', 'INTERRUPTED', 'FAILED'].includes(state) && <p role="status">{state === 'PENDING' ? 'AI가 분석 중입니다.' : 'AI 내용 분석을 완료하지 못했습니다. 로컬 점검과 공고 후보는 계속 확인할 수 있습니다.'} 저장된 결과를 확인하거나 새 분석에서 이어가세요.</p>}
    <button className="content-review-refresh" type="button" disabled={busy || disabled} onClick={() => run(async () => sync(await getContentReview(result.id)))}>AI 처리 상태 확인</button>
    {state === 'COMPLETED' && <div>
      {!review.findings?.length && <p>경험의 역할·과정·결과를 조금 더 적고 다시 분석해보세요. 구체적인 자료가 있으면 완성 문장으로 제안해드려요.</p>}
      {review.findings?.map(finding => <article className="evidence-finding" key={finding.id}>
        <span className="evidence-badge evidence-badge-ai">AI 수정 제안</span>
        <p className="analysis-note">{location(finding)}</p>
        <blockquote><span>원문</span><p><mark>{finding.quote}</mark></p></blockquote>
        <div className="evidence-template"><strong>이렇게 바꿔보세요</strong>
          <p>{finding.suggestion || (finding.rewriteTemplate?.includes('[') ? '이전 결과예요. 현재 자료로 다시 분석하면 완성 문장을 받을 수 있어요.' : finding.rewriteTemplate)}</p>
        </div>
        <h4>왜 효과적인가요?</h4><p>{finding.problem}</p>
        {finding.confirm?.map((confirm, index) => <div className="coaching-confirm" key={index} role="group" aria-label={confirm.question}>
          <strong>확인해 주세요</strong><p>{confirm.question}</p>
          {confirm.options.map((option, optionIndex) => <button type="button" key={optionIndex} disabled={busy || disabled}
            aria-pressed={finding.selectedOption === optionIndex} onClick={() => run(async () => sync(await confirmContentReview(result.id, finding.id, optionIndex)))}>{option.label}</button>)}
          {finding.selectedOption >= 0 && <button type="button" disabled={busy || disabled} onClick={() => run(async () => sync(await confirmContentReview(result.id, finding.id, -1)))}>답변 취소</button>}
          {finding.selectedOption >= 0 && <p role="status">답변을 수정안에 반영했어요.</p>}
        </div>)}
        <div className="evidence-feedback" role="group" aria-label="AI 제안 피드백">
          {Object.entries({ APPLIED: '반영했어요', NOT_APPLICABLE: '해당 없음', INCORRECT: '이 지적이 틀렸어요' }).map(([type, label]) => <button key={type} type="button" aria-pressed={finding.feedback === type} disabled={busy || disabled}
            onClick={() => run(async () => { const next = finding.feedback === type ? 'OPEN' : type;
              await saveFindingFeedback(result.id, finding.id, next); sync({ ...review, findings: review.findings.map(item => item.id === finding.id ? { ...item, feedback: next } : item) });
            })}>{label}</button>)}
        </div>
        <Link to={`/resume?resumeId=${result.resumeId}`}>이력서에서 확인하고 수정하기 →</Link>
      </article>)}
    </div>}
  </section>;
}

export function AiJobReview({ review }) {
  if (!review) return null;
  const criteria = review.criteria || [];
  const leadWith = review.lead_with?.trim();
  const gap = review.gap?.trim();
  const names = { RELATED: '연결되는 경험 있음', REVIEW_NEEDED: '추가 확인 필요', LOW_RELEVANCE: '관련성 낮음' };
  return <div className="ai-job-review"><strong><span className="evidence-badge evidence-badge-ai">AI 판단</span> {names[review.relevance]}</strong>
    {review.decision && <p><strong>{({ RECOMMENDED: '추천', STRETCH: '도전 지원', NOT_RECOMMENDED: '부적합', INSUFFICIENT_INFORMATION: '정보 부족', REVIEW_REQUIRED: '재검토 필요' })[review.decision]}</strong> — {review.decisionReason}</p>}
    {(leadWith || gap) && <div className="coaching-job-fit">
      {leadWith && <div><h4>앞세울 경험</h4><p>{leadWith}</p></div>}
      {gap && <div><h4>보완할 요건</h4><p>{gap}</p></div>}
    </div>}
    {!criteria.length ? <div className="analysis-note">
      <p>공고와 이력서를 비교할 검증된 인용 근거가 없습니다. 지원자의 경험이 부족하다는 판정은 아닙니다.</p>
      <p>공고 상세와 저장된 이력서를 확인해 주세요. 자료를 보완한 뒤 AI 내용 분석에서 변경된 전송 내용을 확인하고 재검토할 수 있습니다. 같은 전송 내용은 저장된 결과를 사용합니다.</p>
      {review.jobId && <Link to={`/job/${review.jobId}`}>공고 상세 확인</Link>}
      {' '}<a href="#ai-content-review">AI 내용 분석으로 이동</a>
    </div> : <details><summary>공고와 경험의 근거 보기</summary>
    {criteria.map((criterion, index) => <div key={index}>
      <p><strong>공고 근거</strong> “{criterion.jobQuote}”</p>
      {criterion.resumeQuote ? <p><strong>이력서 근거</strong> “{criterion.resumeQuote}”<small>{location(criterion)}</small></p> : <p>이 요건과 비교할 이력서 근거를 확인하지 못했습니다.</p>}
      <p>{criterion.explanation}</p><span>{criterion.assessment === 'SUPPORTED' ? '연결되는 경험' : '지원 전 확인할 항목'}</span>
    </div>)}</details>}
  </div>;
}
