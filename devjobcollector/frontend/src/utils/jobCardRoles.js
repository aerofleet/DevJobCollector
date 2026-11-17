// 직무명과 명확한 직무 분류만 사용한다. 기술명이나 조직명으로 직무를 추정하지 않는다.
const ROLES = [
  ['기술 프로그램 매니저', /\btechnical\s+program(?:me)?\s+manag(?:er|ement)\b|기술\s*프로그램\s*매니저/i],
  ['백엔드', /백엔드|\bback[ -]?end\b/i],
  ['프론트엔드', /프론트엔드|\bfront[ -]?end\b/i],
  ['풀스택', /풀스택|\bfull[ -]?stack\b/i],
  ['모바일', /모바일\s*(?:앱|개발|엔지니어)|\b(?:android|ios|mobile)\s+(?:engineer|developer|development)\b/i],
  ['데이터 엔지니어', /데이터\s*엔지니어|\bdata\s+engineer/i],
  ['데이터 분석가', /데이터\s*분석가|\bdata\s+analyst\b/i],
  ['데이터 사이언티스트', /데이터\s*사이언티스트|\bdata\s+scientist\b/i],
  ['AI·머신러닝', /(?:ai|인공지능|머신러닝|딥러닝)\s*(?:개발|엔지니어)|\b(?:ai|machine learning|ml)\s+(?:engineer|developer|scientist)\b/i],
  ['DevOps', /데브옵스|\bdevops\b/i],
  ['SRE', /\bsre\b|\bsite reliability engineer/i],
  ['정보보안', /정보보안|보안\s*엔지니어|\bsecurity\s+engineer/i],
  ['QA·테스트', /\bqa\b|품질\s*(?:관리|보증|엔지니어)|\b(?:test|quality assurance)\s+engineer/i],
  ['프로덕트 매니저', /프로덕트\s*매니저|\bproduct\s+manag(?:er|ement)\b/i],
  ['UX·UI 디자인', /\b(?:product|ux|ui)\s+design(?:er)?\b|(?:ux|ui)\s*디자이너/i],
  ['게임 개발', /게임\s*(?:개발|프로그래머)|\bgame\s+(?:developer|engineer)\b/i],
  ['임베디드·펌웨어', /임베디드|펌웨어|\b(?:embedded|firmware)\s+(?:engineer|developer)\b/i],
  ['소프트웨어 개발', /소프트웨어\s*(?:개발|엔지니어)|\bsoftware\s+(?:engineer|developer|development)\b/i],
];

// 개발 직무 외에도 제목에서 명시된 모집 직무를 그대로 표시한다.
const OTHER_ROLE = /미화원|운전원|(?:예금|보험|고객)\s*상담원|상담원|경비원|간호사|사회복지사|회계사|세무사|변호사|디자이너|마케터|영업\s*담당자/gi;
const OTHER_ENGLISH_ROLES = [
  ['컴플라이언스 매니저', /\bcompliance\s+manager\b/i],
  ['부동산 거래 매니저', /\breal\s+estate\s+transactions?\s+manager\b/i],
  ['리스크 관리', /\brisk\s+management\b/i],
  ['영업 담당자', /\baccount\s+executive\b/i],
  ['이벤트 콘텐츠 기획', /\bevent\s+content\s+strategist\b/i],
  ['주식 보상 관리', /\bequity\s+administrator\b/i],
  ['솔루션 엔지니어', /\bsolutions?\s+engineer\b/i],
  ['솔루션 아키텍트', /\bsolutions?\s+architect\b/i],
  ['지원 엔지니어링 매니저', /\bmanager\s*,?\s*support\s+engineering\b/i],
];

export function jobCardRoles(job) {
  const title = typeof job.title === 'string' ? job.title : '';
  const category = typeof job.jobCategory === 'string' ? job.jobCategory : '';
  const roles = ROLES.filter(([, pattern]) => pattern.test(title) || pattern.test(category)).map(([label]) => label);
  const explicitRoles = [...new Set(title.match(OTHER_ROLE) || [])].filter(role => role !== '디자이너' || !roles.includes('UX·UI 디자인'));
  const englishRoles = OTHER_ENGLISH_ROLES.filter(([, pattern]) => pattern.test(title)).map(([label]) => label);
  const filming = /\bcinematographer\b/i.test(title);
  const editing = /\bvideo\s+editor\b/i.test(title);
  if (filming && editing) englishRoles.push('영상 촬영·편집');
  else if (filming) englishRoles.push('영상 촬영');
  else if (editing) englishRoles.push('영상 편집');
  if (roles.length || explicitRoles.length || englishRoles.length) return [...new Set([...roles, ...explicitRoles, ...englishRoles])];
  // 직무를 확실히 분리할 수 없으면 추측하지 않고 공개된 공고명을 표시한다.
  return title.trim() ? [title.trim()] : [];
}
