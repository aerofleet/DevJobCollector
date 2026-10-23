import React from 'react';
import styles from './BasicInfo.module.css';

const BasicInfo = ({ data, onChange }) => {
  const handleChange = (field, value) => {
    onChange({ ...data, [field]: value });
  };

  return (
    <section id="basicInfo" className={styles.section}>
      <h2 className={styles.title}>기본정보</h2>
      <div className={styles.grid}>
        <label className={styles.field}>
          <span className={styles.label}>이름</span>
          <input
            className={styles.input}
            value={data.name || ''}
            onChange={(e) => handleChange('name', e.target.value)}
            placeholder="홍길동"
          />
        </label>
        <label className={styles.field}>
          <span className={styles.label}>이메일</span>
          <input
            className={styles.input}
            type="email"
            value={data.email || ''}
            onChange={(e) => handleChange('email', e.target.value)}
            placeholder="example@email.com"
          />
        </label>
        <label className={styles.field}>
          <span className={styles.label}>연락처</span>
          <input
            className={styles.input}
            value={data.phone || ''}
            onChange={(e) => handleChange('phone', e.target.value)}
            placeholder="010-1234-5678"
          />
        </label>
        <label className={styles.field}>
          <span className={styles.label}>출생연도 · 선택</span>
          <input
            className={styles.input}
            type="text"
            inputMode="numeric"
            maxLength={4}
            placeholder="YYYY"
            value={data.birthYear || (data.birthDate || '').slice(0, 4)}
            onChange={(e) => onChange({ ...data, birthYear: e.target.value.replace(/\D/g, ''), birthDate: '' })}
          />
        </label>
      </div>
    </section>
  );
};

export default BasicInfo;
