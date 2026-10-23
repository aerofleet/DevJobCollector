import { useEffect, useRef } from 'react';
import { safeResumeUrl } from './resumeSections';
import styles from './ResumePreview.module.css';

const period = item => [item.startDate, item.isCurrent ? '현재' : item.endDate].filter(Boolean).join(' — ');
const selected = value => Array.isArray(value) ? value.join(' · ') : value;
export default function ResumePreview({ title, data, onClose }) {
  const dialog = useRef(null);
  useEffect(() => {
    const element = dialog.current, previous = document.activeElement;
    element.showModal();
    const overflow = document.body.style.overflow; document.body.style.overflow = 'hidden';
    return () => { element.close(); document.body.style.overflow = overflow; previous?.focus(); };
  }, []);
  const list = (key, label, titleField, subtitleField) => data[key]?.length > 0 && <section><h3>{label}</h3>{data[key].map((item, i) => <article key={i}>
    <h4>{item[titleField] || `${label} ${i + 1}`}</h4><p className={styles.meta}>{[item[subtitleField], item.level, item.status, item.workType === 'NON_DEVELOPMENT' ? '비개발 경력' : '', period(item), item.date, item.gpa ? `학점 ${item.gpa}${item.gpaScale ? ` / ${item.gpaScale}` : ''}` : ''].filter(Boolean).join(' · ')}</p>
    {item.companyDescription && <p>{item.companyDescription}</p>}{item.overview && <p>{item.overview}</p>}
    {item.techStack && <p className={styles.meta}>{item.techStack}</p>}{item.description && <p>{item.description}</p>}
    {['problem', 'role', 'actions', 'outcome', 'evidence'].map(field => item[field] && <p key={field}>{item[field]}</p>)}
    {[item.link, item.repository].filter(Boolean).map((url, index) => safeResumeUrl(url) && <a key={index} href={safeResumeUrl(url)} target="_blank" rel="noopener noreferrer">{url}</a>)}
  </article>)}</section>;
  return <dialog ref={dialog} className={styles.dialog} onCancel={onClose} aria-labelledby="resume-preview-title">
    <div className={styles.toolbar}><h2 id="resume-preview-title">이력서 미리보기</h2><button type="button" onClick={onClose} autoFocus>닫기</button></div>
    <div className={styles.paper}>
      <p className={styles.meta}>{title}</p><header className={styles.identity}><div><h1>{data.basicInfo?.name || '이름을 입력해 주세요'}</h1>
        <p>{[data.basicInfo?.email, data.basicInfo?.phone].filter(Boolean).join(' · ')}</p>
        <p className={styles.meta}>{data.basicInfo?.birthDate || (data.basicInfo?.birthYear ? `${data.basicInfo.birthYear}년생` : '')}</p></div>
        {data.basicInfo?.photo?.startsWith('data:image/jpeg;base64,') && <img src={data.basicInfo.photo} alt="이력서 사진" />}</header>
      {data.introduction && <section><h3>간단소개</h3><p>{data.introduction}</p></section>}
      {data.careerProfile && ['category', 'location', 'employmentType', 'summary'].some(key => selected(data.careerProfile[key])) && <section><h3>개발 직무·지원 방향</h3><p>{selected(data.careerProfile.category)}</p><p className={styles.meta}>{[selected(data.careerProfile.location), selected(data.careerProfile.employmentType)].filter(Boolean).join(' / ')}</p><p>{data.careerProfile.summary}</p></section>}
      {data.techStack?.length > 0 && <section><h3>기술스택</h3><div className={styles.tags}>{data.techStack.map(item => <span key={item.name}>{item.name}</span>)}</div></section>}
      {data.links?.length > 0 && <section><h3>링크</h3>{data.links.map((item, i) => <p key={i}>{safeResumeUrl(item.url) ? <a href={safeResumeUrl(item.url)} target="_blank" rel="noopener noreferrer">{item.label || item.url} ↗</a> : `${item.label || '링크'}: 주소를 확인해 주세요`}</p>)}</section>}
      {list('education', '학력', 'school', 'major')}{list('experience', '경력', 'company', 'position')}{list('projects', '프로젝트', 'title', '')}
      {list('training', '교육이력', 'title', 'organization')}{list('activities', '기타사항', 'title', 'organization')}
      {data.coverLetter && <section><h3>자기소개서</h3><p>{data.coverLetter}</p></section>}
    </div>
  </dialog>;
}
