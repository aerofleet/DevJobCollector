import { useState } from 'react';
import styles from './EditorFields.module.css';
import { safeResumeUrl } from './resumeSections';

const definitions = {
  links: { title: '링크', singular: '링크', fields: [['label', '링크 이름', 'text'], ['url', '주소', 'url']] },
  education: { title: '학력', singular: '학력', fields: [['school', '학교명', 'text'], ['major', '전공', 'text'],
    ['level', '학력 구분', ['고등학교', '대학(2·3년)', '대학교(4년)', '대학원(석사)', '대학원(박사)', '기타']],
    ['status', '학적 상태', ['졸업', '졸업예정', '재학', '휴학', '중퇴', '수료']], ['startDate', '입학일', 'date'], ['endDate', '종료일', 'date'],
    ['gpa', '학점 · 선택', 'text'], ['gpaScale', '기준학점', ['4.5', '4.3', '4.0', '100']]] },
  training: { title: '교육이력', singular: '교육', fields: [['title', '교육명', 'text'], ['organization', '교육기관', 'text'],
    ['startDate', '시작일', 'date'], ['endDate', '종료일', 'date'], ['description', '교육 내용', 'textarea']] },
  activities: { title: '기타사항', singular: '기타사항', fields: [['title', '활동·수상·자격명', 'text'], ['organization', '기관', 'text'],
    ['date', '취득·활동일', 'date'], ['description', '상세 내용', 'textarea']] },
};

export function AdditionalList({ id, data = [], onChange }) {
  const definition = definitions[id];
  const update = (index, key, value) => onChange(data.map((item, i) => i === index ? { ...item, [key]: value } : item));
  return <section id={id} className={styles.section} aria-label={definition.title}>
    <div className={styles.header}><h2>{definition.title}</h2><span>{id === 'education' ? '필수 항목' : '선택 항목'}</span></div>
    {!data.length && <p className={styles.hint}>등록한 {definition.title}이 없습니다. 필요한 내용을 추가해 주세요.</p>}
    {data.map((item, index) => <div className={styles.entry} key={index}>
      <div className={styles.grid}>{definition.fields.map(([key, label, type]) => <label key={key} className={type === 'textarea' ? styles.full : ''}>
        <span>{label}</span>
        {type === 'textarea' ? <textarea value={item[key] || ''} maxLength={4000} onChange={event => update(index, key, event.target.value)} />
          : Array.isArray(type) ? <select value={item[key] || ''} onChange={event => update(index, key, event.target.value)}><option value="">선택해 주세요</option>{type.map(value => <option key={value}>{value}</option>)}</select>
            : <input type={type} value={item[key] || ''} maxLength={type === 'url' ? 1000 : 200} onChange={event => update(index, key, event.target.value)} />}
      </label>)}</div>
      {id === 'links' && item.url && (safeResumeUrl(item.url) ? <a href={safeResumeUrl(item.url)} target="_blank" rel="noopener noreferrer">새 창에서 링크 확인 ↗</a>
        : <p role="status" className={styles.error}>http:// 또는 https://로 시작하는 주소를 입력해 주세요.</p>)}
      <button type="button" className={styles.remove} aria-label={`${definition.singular} ${index + 1} 삭제`} onClick={() => onChange(data.filter((_, i) => i !== index))}>삭제</button>
    </div>)}
    <button type="button" className={styles.add} onClick={() => onChange([...data, {}])}>+ {definition.singular} 추가</button>
  </section>;
}

export function ResumeTextSection({ id, title, data = '', onChange, hint }) {
  return <section id={id} className={styles.section} aria-label={title}><div className={styles.header}><h2>{title}</h2><span>선택 항목</span></div>
    <label className={styles.textLabel}><span>{hint}</span><textarea aria-label={title} value={data} maxLength={8000} onChange={event => onChange(event.target.value)} /></label>
    <small>{data.length.toLocaleString()} / 8,000자</small>
  </section>;
}

export function ResumePhoto({ value, onChange, onBusyChange }) {
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const upload = async event => {
    const file = event.target.files?.[0]; event.target.value = '';
    if (!file) return;
    setError('');
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024) {
      setError('5MB 이하의 JPG, PNG, WebP 사진을 선택해 주세요.'); return;
    }
    setBusy(true); onBusyChange(true);
    try {
      const bitmap = await createImageBitmap(file);
      const canvas = document.createElement('canvas'); canvas.width = 240; canvas.height = 320;
      const ctx = canvas.getContext('2d'); ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, 240, 320);
      const ratio = Math.min(240 / bitmap.width, 320 / bitmap.height);
      const width = bitmap.width * ratio, height = bitmap.height * ratio;
      ctx.drawImage(bitmap, (240 - width) / 2, (320 - height) / 2, width, height); bitmap.close();
      onChange(canvas.toDataURL('image/jpeg', 0.8));
    } catch { setError('사진을 읽지 못했습니다. 다른 이미지 파일을 선택해 주세요.'); }
    finally { setBusy(false); onBusyChange(false); }
  };
  return <section id="photo" className={styles.section} aria-label="사진"><div className={styles.header}><h2>사진</h2><span>선택 항목</span></div>
    <div className={styles.photoRow}>{value?.startsWith('data:image/jpeg;base64,') ? <img className={styles.photo} src={value} alt="이력서 사진" /> : <div className={styles.photoPlaceholder}>사진</div>}
      <div><label className={styles.textLabel}>사진 선택<input type="file" accept="image/jpeg,image/png,image/webp" disabled={busy} onChange={upload} /></label>
        <p className={styles.hint}>JPG·PNG·WebP, 최대 5MB. 사진은 이력서를 저장할 때 함께 저장됩니다.</p>
        {busy && <p role="status">사진 준비 중…</p>}{value && <button className={styles.remove} type="button" onClick={() => onChange('')}>사진 삭제</button>}
      </div></div>{error && <p role="alert" className={styles.error}>{error}</p>}
  </section>;
}
