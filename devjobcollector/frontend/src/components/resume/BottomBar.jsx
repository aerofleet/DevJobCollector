import React from 'react';
import styles from './BottomBar.module.css';

const BottomBar = ({ onSave, isSaving, completionRate }) => {
  return (
    <div className={styles.bar}>
      <div className={styles.inner}>
        <span className={styles.rate}>완성도 {completionRate}%</span>
        <button type="button" className={styles.button} onClick={onSave} disabled={isSaving}>
          {isSaving ? '저장 중...' : '저장하기'}
        </button>
      </div>
    </div>
  );
};

export default BottomBar;
