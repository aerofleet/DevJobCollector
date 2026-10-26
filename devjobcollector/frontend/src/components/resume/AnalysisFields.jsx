import styles from './Projects.module.css';
import { LOCATION_GROUPS } from '../../data/locationFilters';

const resumeLocationGroups = LOCATION_GROUPS.map(group => ({ ...group,
  options: group.options.map(([key, label]) => key === 'Remote' ? '원격' : label),
}));

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
  const groups = [
    { key: 'category', label: '희망 직무', empty: '아직 정하지 않음', options: ['백엔드','프론트엔드','풀스택','데이터','인공지능','모바일','보안','DevOps'] },
    { key: 'location', label: '희망 지역', empty: '지역 제한 없음', options: resumeLocationGroups.flatMap(group => group.options) },
    { key: 'employmentType', label: '고용 형태', empty: '형태 제한 없음', options: ['정규직','계약직','인턴','프리랜서'] },
  ];
  return <section id="careerProfile" className={styles.section} aria-label="지원 방향">
    <h2 className={styles.title}>지원 방향</h2><p className={styles.hint}>한 번 저장하면 분석과 공고 추천에 함께 사용합니다. 여러 조건을 선택할 수 있으며, 정하지 않은 조건은 비워 두세요.</p>
    <div className={styles.grid}>
      {groups.map(({ key, label, empty, options }) => {
        const values = Array.isArray(data[key]) ? data[key] : data[key] ? [data[key]] : [];
        const selected = options.filter(value => values.includes(value));
        const renderOptions = group => <div key={group.label}>
          {group.label && <strong>{group.label}</strong>}
          <div className={styles.preferenceOptions}>{group.options.map(value => <label key={value}>
            <input type="checkbox" checked={selected.includes(value)} onChange={event => update(key,
              options.filter(option => option === value ? event.target.checked : selected.includes(option)))} />
            {value}
          </label>)}</div>
        </div>;
        return <fieldset key={key} className={styles.preferenceGroup}>
          <legend>{label}</legend>
          <div className={styles.preferenceActions}>
            <button type="button" disabled={selected.length === options.length} onClick={() => update(key, [...options])}>전체 선택</button>
            <button type="button" disabled={!selected.length} onClick={() => update(key, [])}>전체 해제</button>
            <span>{selected.length ? `${selected.length}개 선택` : empty}</span>
          </div>
          {key === 'location' ? <>
            {['국내', '해외'].map(region => {
              const regionGroups = resumeLocationGroups.filter(group => region === '국내'
                ? group.label === '국내' : !['국내', '근무 방식'].includes(group.label));
              const count = regionGroups.flatMap(group => group.options).filter(value => selected.includes(value)).length;
              return <details key={region} className={styles.locationDisclosure} open>
                <summary>{region} <span>· {count}개 선택</span></summary>
                <div className={styles.locationGroups}>{regionGroups.map(group => renderOptions({
                  ...group, label: group.label === region ? '' : group.label,
                }))}</div>
              </details>;
            })}
            {resumeLocationGroups.filter(group => group.label === '근무 방식').map(renderOptions)}
          </> : renderOptions({ label: '', options })}
          {key === 'location' && <p className={styles.hint}>원격을 함께 선택해도 지정한 지역 안에서 추천해요. 원격만 선택하면 국가 제한 없이 후보를 찾아요.</p>}
        </fieldset>;
      })}
    </div>
    <label className={styles.field}>경험 요약<textarea className={styles.textarea} value={data.summary || ''} maxLength={4000}
      onChange={e => update('summary', e.target.value)} placeholder="주로 다룬 기술, 해결한 문제, 앞으로 하고 싶은 일을 적어 주세요." /></label>
  </section>;
}
