import { Link } from 'react-router-dom';

export default function ResumeCoachingSummary({ result }) {
  const ai = result.aiReview?.status === 'COMPLETED' ? result.aiReview : null;
  const observations = result.review?.observations;
  const skills = result.detectedSkills || [];
  const gaps = ai?.gaps || [];
  const missingCareer = result.inputMode === 'SAVED_RESUME' && result.derivedPreferences?.careerYears == null;
  const edit = `/resume?resumeId=${result.resumeId}`;
  return <>
    <section className="coaching-hero" aria-label="지원 방향 요약">
      <span className="evidence-eyebrow">YOUR NEXT CHAPTER</span>
      <h3>{ai?.positioning || '내 경험을 다음 기회로 연결해보세요'}</h3>
      <p>{ai?.positioning ? '자료에서 찾은 나의 포지셔닝이에요. 이력서 첫 줄에 활용해보세요.' : '자료 점검 결과를 확인하고 AI 제안으로 강점과 지원 방향을 구체화해보세요.'}</p>
      <div className="coaching-metrics">
        <div><strong>{observations?.withMeasurement ?? 0}<small> / {observations?.outcomeSentences ?? 0}</small></strong><span>측정값이 있는 성과 문장</span></div>
        <div><strong>{skills.length}</strong><span>자료에서 확인한 기술</span></div>
        <div><strong>{gaps.length + Number(missingCareer)}</strong><span>지금 확인할 부분</span></div>
      </div>
    </section>
    <section className="coaching-section" aria-label="가장 강한 어필 포인트">
      <h3>이건 꼭 앞세우세요</h3>
      {!ai?.strengths?.length && <p>AI 내용 분석을 실행하면 근거와 함께 강조할 경험과 배치를 제안해드려요.</p>}
      <div className="coaching-strengths">{ai?.strengths?.map((strength, index) => <article key={index}>
        <span className="coaching-number">0{index + 1}</span><h4>{strength.point}</h4>
        {strength.evidence?.map((e, i) => <blockquote key={i}>{e.quote}<cite>{e.label}{e.page ? ` · ${e.page}쪽` : ''}</cite></blockquote>)}
        <p className="coaching-placement">↗ {strength.placement}</p>
      </article>)}</div>
    </section>
    <section className="coaching-section" aria-label="놓치고 있는 부분"><h3>놓치고 있는 부분</h3>
      {missingCareer && <article className="coaching-gap"><span>빈 항목</span><div><h4>경력 기간을 확인해 주세요</h4><p>경력 항목의 기간을 저장하면 공고의 경력 조건과 함께 비교할 수 있어요.</p></div><Link to={edit}>기간 입력 →</Link></article>}
      {gaps.map((gap, i) => <article className="coaching-gap" key={i}><span>{({ missing: '빈 항목', weak: '증빙 보완', buried: '묻힌 성과' })[gap.type]}</span><div><h4>{gap.what}</h4><p>{gap.why_it_matters}</p></div><Link to={edit}>내용 보완 →</Link></article>)}
      {!gaps.length && !missingCareer && <p>{result.review?.findings?.length === 0 ? '규칙 점검을 마쳤어요. 아래 AI 제안으로 어필력을 더 높여보세요.' : '아래 수정 제안과 공고 요건을 비교하며 보완할 내용을 찾아보세요.'}</p>}
      <details className="coaching-skills"><summary>확인한 기술 {skills.length}개 살펴보기</summary><div className="evidence-chips">{skills.map(s => <span key={s}>{s}</span>)}</div><Link to={edit}>기술 스택 확인·수정 →</Link></details>
    </section>
  </>;
}
