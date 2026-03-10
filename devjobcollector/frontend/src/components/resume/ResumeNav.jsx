import React from 'react';
import styles from './ResumeNav.module.css';

const sections = [
  { id: 'basicInfo', label: '기본정보' },
  { id: 'techStack', label: '기술스택' },
  { id: 'projects', label: '프로젝트' },
  { id: 'experience', label: '경력' },
];

const ResumeNav = ({ activeSection, completionRate, setActiveSection }) => {
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
        <ul className={styles.list}>
          {sections.map((section) => (
            <li key={section.id}>
              <button
                type="button"
                className={`${styles.item} ${activeSection === section.id ? styles.active : ''}`}
                onClick={() => scrollToSection(section.id)}
              >
                {section.label}
              </button>
            </li>
          ))}
        </ul>
      </section>
      <section className={styles.progress}>
        <p className={styles.progressTitle}>이력서 완성도</p>
        <div className={styles.progressBar}>
          <div className={styles.progressFill} style={{ width: `${completionRate}%` }} />
        </div>
        <p className={styles.progressRate}>{completionRate}%</p>
      </section>
    </aside>
  );
};

export default ResumeNav;
