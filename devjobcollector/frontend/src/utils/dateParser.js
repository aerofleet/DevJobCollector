/**
 * ISO 8601 날짜 문자열을 "YYYY.MM.DD" 형식으로 변환
 * @param {string} dateString - ISO 8601 형식의 날짜 문자열
 * @returns {string} 변환된 날짜 문자열
 */
export const formatDate = (dateString) => {
  if (!dateString) return '-';
  
  const date = new Date(dateString);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  
  return `${year}.${month}.${day}`;
};

/**
 * 마감일까지 남은 일수 계산
 * @param {string} endDate - 마감일
 * @returns {number} 남은 일수
 */
export const getDaysRemaining = (endDate) => {
  if (!endDate) return null;

  const datePart = endDate.split('T')[0]; // 연-월-일만 사용
  const [y, m, d] = datePart.split('-').map(Number);
  if (![y, m, d].every(Number.isFinite)) return null;

  // UTC 자정 기준으로 계산해 시간대/오프셋 영향 제거
  const end = Date.UTC(y, m - 1, d);
  const today = new Date();
  today.setUTCHours(0, 0, 0, 0);

  const diffDays = Math.round((end - today.getTime()) / 86400000);
  return diffDays;
};
