import React from 'react';
import { ExperienceEvidenceFields } from './AnalysisFields';
import styles from './Experience.module.css';

const emptyExperience = {
  company: '',
  position: '',
  startDate: '',
  endDate: '',
  description: '',
};

const Experience = ({ data, onChange }) => {
  const addExperience = () => onChange([...data, { ...emptyExperience }]);

  const removeExperience = (index) => {
    onChange(data.filter((_, i) => i !== index));
  };

  const updateExperience = (index, field, value) => {
    const updated = [...data];
    updated[index] = { ...updated[index], [field]: value };
    onChange(updated);
  };

  return (
    <section id="experience" className={styles.section}>
      <div className={styles.header}>
        <h2 className={styles.title}>경력</h2>
        <button type="button" className={styles.addButton} onClick={addExperience}>
          경력 추가
        </button>
      </div>
      {data.length === 0 && <p className={styles.empty}>등록된 경력이 없습니다.</p>}
      {data.map((experience, index) => (
        <div key={`experience-${index}`} className={styles.card}>
          <div className={styles.grid}>
            <input
              className={styles.input}
              placeholder="회사명"
              value={experience.company}
              onChange={(e) => updateExperience(index, 'company', e.target.value)}
            />
            <input
              className={styles.input}
              placeholder="직무"
              value={experience.position}
              onChange={(e) => updateExperience(index, 'position', e.target.value)}
            />
            <input
              className={styles.input}
              type="date"
              value={experience.startDate}
              onChange={(e) => updateExperience(index, 'startDate', e.target.value)}
            />
            <input
              className={styles.input}
              type="date"
              value={experience.endDate}
              disabled={experience.isCurrent === true}
              onChange={(e) => updateExperience(index, 'endDate', e.target.value)}
            />
          </div>
          <label><input type="checkbox" checked={experience.isCurrent === true} onChange={e => updateExperience(index, 'isCurrent', e.target.checked)} /> 현재 재직 중</label>
          <textarea
            className={styles.textarea}
            placeholder="담당 업무 및 성과"
            value={experience.description}
            onChange={(e) => updateExperience(index, 'description', e.target.value)}
          />
          <ExperienceEvidenceFields data={experience} label={`경력 ${index + 1}`} onChange={(key, value) => updateExperience(index, key, value)} />
          <button type="button" className={styles.removeButton} onClick={() => removeExperience(index)}>
            삭제
          </button>
        </div>
      ))}
    </section>
  );
};

export default Experience;
