import React from 'react';
import styles from './BottomBar.module.css';

const BottomBar = ({ onSave, onAnalyze, onPreview, isSaving, completionRate }) => {
  return (
    <div className={styles.bar}>
      <div className={styles.inner}>
        <span className={styles.rate}>입력 현황 {completionRate}%</span>
        <button type="button" className={styles.preview} onClick={onPreview}>미리보기</button>
        <button type="button" className={styles.button} onClick={onAnalyze} disabled={isSaving}>저장하고 AI 분석</button>
        <button type="button" className={styles.button} onClick={onSave} disabled={isSaving}>
          {isSaving ? '저장 중...' : '저장하기'}
        </button>
      </div>
    </div>
  );
};

export default BottomBar;
