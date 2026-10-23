import React from 'react';
import styles from './ResumeNav.module.css';
import { resumeSections } from './resumeSections';

const ResumeNav = ({ activeSection, completionRate, setActiveSection, enabledSections, onToggle, completion }) => {
  const scrollToSection = (sectionId) => {
    const element = document.getElementById(sectionId);
    if (!element) {
      return;
    }
    element.scrollIntoView({ behavior: 'smooth', block: 'start' });
    setActiveSection(sectionId);
  };

  return (
    <aside className={styles.nav}>
      <section className={styles.section}>
        <h2 className={styles.title}>항목 편집</h2>
        <p className={styles.legend}>필수 항목 · 경력/프로젝트는 택 1</p>
        <ul className={styles.list}>
          {resumeSections.map((section) => (
            <li key={section.id}>
              <button
                type="button"
                className={`${styles.item} ${activeSection === section.id ? styles.active : ''}`}
                onClick={() => scrollToSection(section.id)}
                disabled={section.optional && !enabledSections.includes(section.id)}
                aria-current={activeSection === section.id ? 'location' : undefined}
              >
                {section.label} {section.requirement && <small>{section.requirement}</small>}
              </button>
              {section.optional && <input type="checkbox" aria-label={`${section.label} 항목 표시`} checked={enabledSections.includes(section.id)} onChange={() => onToggle(section.id)} />}
            </li>
          ))}
        </ul>
        <p className={styles.legend}>항목을 접어도 작성 내용은 저장·미리보기에 유지됩니다.</p>
      </section>
      <section className={styles.progress}>
        <p className={styles.progressTitle}>작성 항목 입력 현황</p>
        <div className={styles.progressBar}>
          <div className={styles.progressFill} style={{ width: `${completionRate}%` }} />
        </div>
        <p className={styles.progressRate}>{completionRate}%</p>
        <ul className={styles.checklist}>{completion.map(item => <li key={item.label}><span aria-label={item.complete ? '입력됨' : '미입력'}>{item.complete ? '✓' : '○'}</span> {item.label}</li>)}</ul>
        <p className={styles.legend}>작성 안내 기준입니다. 미완성 상태도 저장할 수 있어요.</p>
      </section>
    </aside>
  );
};

export default ResumeNav;
