import React, { useState } from 'react';
import styles from './TechStack.module.css';

const presetTechs = ['Java', 'Spring Boot', 'React', 'TypeScript', 'MySQL', 'Docker'];

const TechStack = ({ data, onChange }) => {
  const [inputValue, setInputValue] = useState('');

  const addTech = (name) => {
    if (!name || data.some((item) => item.name.toLowerCase() === name.toLowerCase())) {
      return;
    }
    onChange([...data, { name, category: 'backend', level: 3 }]);
    setInputValue('');
  };

  const removeTech = (name) => {
    onChange(data.filter((item) => item.name !== name));
  };

  return (
    <section id="techStack" className={styles.section}>
      <h2 className={styles.title}>기술스택</h2>
      <div className={styles.tags}>
        {presetTechs.map((tech) => (
          <button key={tech} type="button" className={styles.tag} onClick={() => addTech(tech)}>
            + {tech}
          </button>
        ))}
      </div>
      <div className={styles.inputRow}>
        <input
          className={styles.input}
          placeholder="기술스택 직접 입력"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              addTech(inputValue.trim());
            }
          }}
        />
        <button type="button" className={styles.addButton} onClick={() => addTech(inputValue.trim())}>
          추가
        </button>
      </div>
      <div className={styles.selected}>
        {data.map((tech) => (
          <span key={tech.name} className={styles.selectedTag}>
            {tech.name}
            <button type="button" onClick={() => removeTech(tech.name)}>
              x
            </button>
          </span>
        ))}
      </div>
    </section>
  );
};

export default TechStack;
