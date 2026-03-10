import React from 'react';
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
    updated[index] = { ...updated[index], [field]: value };
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
              value={project.startDate}
              onChange={(e) => updateProject(index, 'startDate', e.target.value)}
            />
            <input
              className={styles.input}
              type="date"
              value={project.endDate}
              onChange={(e) => updateProject(index, 'endDate', e.target.value)}
            />
          </div>
          <textarea
            className={styles.textarea}
            placeholder="프로젝트 설명"
            value={project.description}
            onChange={(e) => updateProject(index, 'description', e.target.value)}
          />
          <button type="button" className={styles.removeButton} onClick={() => removeProject(index)}>
            삭제
          </button>
        </div>
      ))}
    </section>
  );
};

export default Projects;
