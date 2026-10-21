import styles from './Projects.module.css';

export function ExperienceEvidenceFields({ data, onChange, label }) {
  const fields = [
    ['problem', '해결하려던 문제', '어떤 상황에서 무엇이 불편하거나 필요했나요?'],
    ['role', '내 역할과 담당 범위', '팀 전체의 성과와 내가 직접 맡은 일을 구분해 주세요.'],
    ['actions', '직접 수행한 작업', '어떤 기술을 왜 선택했고, 어떻게 구현했나요?'],
    ['outcome', '결과와 확인 방법', '측정 기록이 있으면 조건·전후 값·단위를, 없으면 확인한 결과를 적어 주세요.'],
    ['evidence', '근거 자료', '테스트 기록, 코드, 회고 등 결과를 확인할 수 있는 자료를 적어 주세요.'],
  ];
  return <details className={styles.guide}><summary>경험을 구체적으로 정리하기 · 선택</summary>
    <p>기존 설명에 충분히 적었다면 다시 입력하지 않아도 됩니다. 빈칸에 숫자나 경험을 만들어 넣지 마세요.</p>
    {fields.map(([key, title, hint]) => <label className={styles.field} key={key}><span>{title}</span>
      <textarea className={styles.textarea} aria-label={`${label} ${title}`} value={data[key] || ''} maxLength={4000}
        placeholder={hint} onChange={event => onChange(key, event.target.value)} /></label>)}
  </details>;
}

export function CareerProfile({ data = {}, onChange }) {
  const update = (key, value) => onChange({ ...data, [key]: value });
  return <section id="careerProfile" className={styles.section} aria-label="지원 방향">
    <h2 className={styles.title}>지원 방향</h2><p className={styles.hint}>한 번 저장하면 분석과 공고 추천에 함께 사용합니다. 정하지 않은 조건은 비워 두세요.</p>
    <div className={styles.grid}>
      <label className={styles.field}>희망 직무<select className={styles.input} value={data.category || ''} onChange={e => update('category', e.target.value)}>
        <option value="">아직 정하지 않음</option>{['백엔드','프론트엔드','풀스택','데이터','인공지능','모바일','보안','DevOps'].map(v => <option key={v}>{v}</option>)}
      </select></label>
      <label className={styles.field}>희망 지역<select className={styles.input} value={data.location || ''} onChange={e => update('location', e.target.value)}>
        <option value="">지역 제한 없음</option>{['서울','경기','인천','부산','대구','대전','광주','울산','세종','강원','충북','충남','전북','전남','경북','경남','제주','원격'].map(v => <option key={v}>{v}</option>)}
      </select></label>
      <label className={styles.field}>고용 형태<select className={styles.input} value={data.employmentType || ''} onChange={e => update('employmentType', e.target.value)}>
        <option value="">형태 제한 없음</option>{['정규직','계약직','인턴','프리랜서'].map(v => <option key={v}>{v}</option>)}
      </select></label>
    </div>
    <label className={styles.field}>경험 요약<textarea className={styles.textarea} value={data.summary || ''} maxLength={4000}
      onChange={e => update('summary', e.target.value)} placeholder="주로 다룬 기술, 해결한 문제, 앞으로 하고 싶은 일을 적어 주세요." /></label>
  </section>;
}
