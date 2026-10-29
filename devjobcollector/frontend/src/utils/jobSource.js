const LABELS = {
  PUBLIC_ALIO: '공공기관 알리오', GREENHOUSE: 'Greenhouse', LEVER: 'Lever',
  SARAMIN: '사람인', WOOWAHAN: '우아한형제들', KAKAO: '카카오 채용',
  JOBKOREA: '잡코리아', COMPANY_PAGE: '기업 채용 페이지', COMPANY_DIRECT: '기업 직접 등록',
};

export function jobSourceLabel(job) {
  if (job.sourcePlatform === 'COMPANY_PAGE') {
    try {
      const host = new URL(job.originalUrl).hostname.toLowerCase();
      if (host === 'recruit.navercorp.com') return '네이버 채용';
      if (host === 'toss.im' || host.endsWith('.toss.im')) return '토스 채용';
    } catch { /* 원문 주소가 없으면 일반 출처명을 표시합니다. */ }
  }
  return LABELS[job.sourcePlatform] || '출처 정보 없음';
}
