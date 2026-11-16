// 홈 추천은 직무를 식별할 수 있는 근거가 있는 공고만 포함한다.
// 기관명이나 직무와 무관한 NCS 분류가 기술 스택에 들어올 수 있어 이를 근거로 쓰지 않는다.
const ROLE = /개발자|프로그래머|백엔드|프론트엔드|풀스택|데브옵스|클라우드\s*엔지니어|데이터\s*(?:엔지니어|사이언티스트|분석가)|(?:머신러닝|인공지능|딥러닝)\s*(?:엔지니어|개발)|(?:소프트웨어|앱|웹|서버|시스템)\s*개발|\b(?:software\s+(?:engineer|developer|development)|(?:backend|back-end|frontend|front-end|full[ -]?stack|devops|sre|mlops)|(?:data|machine learning|ai|cloud|platform|mobile|security)\s+engineer)\b/i;

export function isDeveloperJob(job) {
  const company = (job.companyName || '').trim();
  const title = company ? (job.title || '').split(company).join(' ') : (job.title || '');
  const removeNonSoftwareDevelopers = text => text.replace(/(?:사업|인재|상품|제품|부동산|지역|조직|교육)\s*개발자/g, ' ');
  return ROLE.test(removeNonSoftwareDevelopers(title)) || ROLE.test(removeNonSoftwareDevelopers(job.jobCategory || ''));
}
