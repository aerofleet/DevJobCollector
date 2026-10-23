import React from 'react';
import { ExperienceEvidenceFields } from './AnalysisFields';
import styles from './Projects.module.css';

const emptyProject = {
  title: '',
  description: '',
  startDate: '',
  endDate: '',
  link: '',
};

const Projects = ({ data, onChange }) => {
  const addProject = () => onChange([...data, { ...emptyProject }]);

  const removeProject = (index) => {
    onChange(data.filter((_, i) => i !== index));
  };

  const updateProject = (index, field, value) => {
    const updated = [...data];
    updated[index] = { ...updated[index], [field]: value, ...(field === 'isCurrent' && value ? { endDate: '' } : {}) };
    onChange(updated);
  };

  return (
    <section id="projects" className={styles.section}>
      <div className={styles.header}>
        <h2 className={styles.title}>프로젝트</h2>
        <button type="button" className={styles.addButton} onClick={addProject}>
          프로젝트 추가
        </button>
      </div>
      {data.length === 0 && <p className={styles.empty}>등록된 프로젝트가 없습니다.</p>}
      {data.map((project, index) => (
        <div key={`project-${index}`} className={styles.card}>
          <div className={styles.grid}>
            <input
              className={styles.input}
              placeholder="프로젝트명"
              value={project.title}
              onChange={(e) => updateProject(index, 'title', e.target.value)}
            />
            <input
              className={styles.input}
              placeholder="프로젝트 링크"
              value={project.link}
              onChange={(e) => updateProject(index, 'link', e.target.value)}
            />
            <input
              className={styles.input}
              type="date"
              aria-label={`프로젝트 ${index + 1} 시작일`}
              value={project.startDate}
              onChange={(e) => updateProject(index, 'startDate', e.target.value)}
            />
            <input
              className={styles.input}
              type="date"
              aria-label={`프로젝트 ${index + 1} 종료일`}
              value={project.endDate}
              disabled={project.isCurrent === true}
              onChange={(e) => updateProject(index, 'endDate', e.target.value)}
            />
          </div>
          <label className={styles.current}><input type="checkbox" checked={project.isCurrent === true} onChange={event => updateProject(index, 'isCurrent', event.target.checked)} /> 현재 진행 중</label>
          <label className={styles.field}>프로젝트 소개<input className={styles.input} value={project.overview || ''} maxLength={500} onChange={event => updateProject(index, 'overview', event.target.value)} placeholder="어떤 사용자의 문제를 해결하는 서비스인가요?" /></label>
          <div className={styles.grid}>
            <label className={styles.field}>사용 기술<input className={styles.input} value={project.techStack || ''} maxLength={500} onChange={event => updateProject(index, 'techStack', event.target.value)} placeholder="Java, Spring Boot, MySQL" /></label>
            <label className={styles.field}>저장소 링크<input className={styles.input} type="url" value={project.repository || ''} maxLength={1000} onChange={event => updateProject(index, 'repository', event.target.value)} placeholder="https://github.com/..." /></label>
          </div>
          <textarea
            className={styles.textarea}
            placeholder="프로젝트 설명"
            value={project.description}
            onChange={(e) => updateProject(index, 'description', e.target.value)}
          />
          <ExperienceEvidenceFields data={project} label={`프로젝트 ${index + 1}`} onChange={(key, value) => updateProject(index, key, value)} />
          <button type="button" className={styles.removeButton} onClick={() => removeProject(index)}>
            삭제
          </button>
        </div>
      ))}
    </section>
  );
};

export default Projects;
