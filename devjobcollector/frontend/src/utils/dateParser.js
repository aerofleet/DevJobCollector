/**
 * ATS에서 마감일을 제공하지 않는 공고인지 확인
 * @param {string} dateString - ISO 8601 형식의 날짜 문자열
 * @returns {boolean}
 */
export const isOpenEndedDate = (dateString) => {
  if (!dateString) return false;
  return dateString.split('T')[0] === '9999-12-31';
};

/**
 * ISO 8601 날짜 문자열을 "YYYY.MM.DD" 형식으로 변환
 * @param {string} dateString - ISO 8601 형식의 날짜 문자열
 * @returns {string} 변환된 날짜 문자열
 */
export const formatDate = (dateString) => {
  if (!dateString) return '-';
  if (isOpenEndedDate(dateString)) return '채용 시 마감';

  const date = new Date(dateString);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}.${month}.${day}`;
};

/**
 * 마감일까지 남은 일수 계산
 * @param {string} endDate - 마감일
 * @returns {number|null} 남은 일수
 */
export const getDaysRemaining = (endDate) => {
  if (!endDate || isOpenEndedDate(endDate)) return null;

  const datePart = endDate.split('T')[0];
  const [y, m, d] = datePart.split('-').map(Number);
  if (![y, m, d].every(Number.isFinite)) return null;

  const end = Date.UTC(y, m - 1, d);
  const today = new Date();
  today.setUTCHours(0, 0, 0, 0);

  return Math.round((end - today.getTime()) / 86400000);
};
