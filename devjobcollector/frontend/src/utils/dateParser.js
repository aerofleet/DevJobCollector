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
  
  const end = new Date(endDate);
  const today = new Date();
  const diffTime = end - today;
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
  
  return diffDays;
};