import { Link } from 'react-router-dom';

const statuses = { APPLIED: '반영했어요', NOT_APPLICABLE: '해당 없음', INCORRECT: '이 지적이 틀렸어요' };
const sections = { projects: '프로젝트', experience: '경력', techStack: '기술 스택' };
const evidenceLocation = (location) => `${location.label} · ${location.page ? `${location.page}쪽` : sections[location.section] || location.section}`;

export default function ResumeEvidenceReport({ result, busy, onFeedback }) {
  const review = result.review;
  if (!review) return null;
  const findings = review.findings || [];
  const applied = findings.filter((finding) => finding.feedback === 'APPLIED').length;
  const checked = findings.filter((finding) => finding.feedback && finding.feedback !== 'OPEN').length;
  return <div className="evidence-report">
    <div className="evidence-intro">
      <span className="evidence-eyebrow">EVIDENCE FIRST</span>
      <h3>근거를 보고, 직접 판단하세요.</h3>
      <p>규칙으로 찾은 보완 후보입니다. 인용문은 분석 당시 추출한 텍스트와 대조했습니다. 문맥과 OCR 오류까지 검증한 것은 아닙니다.</p>
      <div className="evidence-metrics">
        <div><strong>{findings.length}<small>개</small></strong><span>원문이 있는 보완 후보 · 최대 20개</span></div>
        <div><strong>{review.observations.withMeasurement}<small> / {review.observations.outcomeSentences}문장</small></strong><span>성과 표현 중 숫자·단위 포함</span></div>
        <div><strong>{result.detectedSkills?.length ?? Object.keys(review.skillEvidence || {}).length}<small>개</small></strong><span>자료에서 확인한 기술</span></div>
      </div>
      <p className="analysis-note">종합 점수 미산출 · 검증된 평가 기준이 없어 점수로 환산하지 않습니다.</p>
      <details className="evidence-method"><summary>검사 기준과 한계</summary>
        <p>{review.observations.basis}</p><p>{review.score.reason} 채용 적합도나 합격 확률을 뜻하지 않습니다.</p>
        <p>PDF 쪽 번호는 파일 내 순서입니다. 이미지의 1쪽은 이미지 한 장을 뜻합니다. 제목·개인 정보 등 모든 항목을 검사하는 서비스는 아닙니다.</p>
      </details>
    </div>
    <div className="evidence-layout">
      <section className="evidence-findings" aria-label="근거가 있는 보완 후보">
        <h3>원문으로 확인하는 보완 후보</h3>
        {!findings.length && <p className="evidence-empty">현재 규칙에서 인용 가능한 보완 후보를 찾지 못했습니다. 내용이 완전하거나 오류가 없다는 의미는 아닙니다.</p>}
        {findings.map((finding, index) => <article className="evidence-finding" key={finding.id}>
          <div className="evidence-card-top"><span className="evidence-badge">규칙 검사</span><span>검토 {String(index + 1).padStart(2, '0')}</span></div>
          <h4>성과의 확인 근거를 보완해 주세요</h4>
          <ul className="evidence-locations">{finding.locations.map((location) => <li key={location.sourceKey}>{evidenceLocation(location)}</li>)}</ul>
          <blockquote><span>추출 원문과 일치</span><p><mark>{finding.quote}</mark></p></blockquote>
          <p>{finding.problem}</p>
          <details className="evidence-template"><summary>직접 채우는 수정 예시</summary><p>{finding.rewriteTemplate}</p><small>빈칸에는 확인 가능한 사실만 입력하세요. 숫자를 임의로 만들지 마세요.</small></details>
          <div className="evidence-feedback" role="group" aria-label={`검토 ${index + 1} 피드백`}>
            {Object.entries(statuses).map(([value, label]) => <button key={value} type="button" disabled={busy}
              aria-pressed={finding.feedback === value} onClick={() => onFeedback(finding.id, finding.feedback === value ? 'OPEN' : value)}>{label}</button>)}
          </div>
        </article>)}
      </section>
      <aside className="evidence-sidebar" aria-label="검토 현황">
        <h3>내 검토 현황</h3><strong>{checked} / {findings.length}개 확인</strong>
        <progress value={checked} max={Math.max(findings.length, 1)} aria-label="피드백을 남긴 보완 후보" />
        <p>직접 반영 표시 {applied}개</p><p className="analysis-note">피드백은 내 기록에 저장됩니다. ‘반영했어요’는 수정 여부를 자동 검증한 상태가 아닙니다. 다시 누르면 취소됩니다.</p>
        <Link to={`/resume?resumeId=${result.resumeId}`}>이력서 수정하기 →</Link>
        {result.comparison && <div className="evidence-comparison"><h4>이전 분석과 비교</h4>
          <p>유지 {result.comparison.retained}개 · 새 후보 {result.comparison.newFindings}개 · 이번에 미검출 {result.comparison.notDetected}개</p>
          <small>문구·첨부자료 변경도 차이를 만듭니다. 미검출이 해결을 뜻하지는 않습니다.</small></div>}
        <h4>자료에서 확인한 기술</h4><div className="evidence-chips">{(result.detectedSkills || Object.keys(review.skillEvidence || {})).map((skill) => <span key={skill}>{skill}</span>)}</div>
        <p className="analysis-note">기술명 언급을 확인한 결과이며 숙련도는 평가하지 않습니다.</p>
      </aside>
    </div>
  </div>;
}

export function JobEvidence({ job, review }) {
  return <div className="job-evidence"><span className="evidence-badge">공고 데이터</span>
    <p className="analysis-note">{job.skillEvidenceSource === 'JOB_TITLE' ? '공고 제목' : '수집된 기술 태그'}와 자료의 기술명을 대조했습니다. 필수·우대 요건 구분과 실제 지원 자격은 공고 원문에서 확인해 주세요.</p>
    <ul>{job.matchPoints.map((skill) => <li key={skill}><strong>{skill}</strong><span>자료에서 확인</span>
      <small>{review?.skillEvidence?.[skill]?.map(evidenceLocation).join(' / ') || '이 결과에 저장된 위치 정보 없음'}</small></li>)}
      {job.missingPoints.map((skill) => <li key={skill}><strong>{skill}</strong><span>자료에서 미확인</span><small>보유하지 않았다는 뜻은 아닙니다.</small></li>)}</ul>
  </div>;
}
