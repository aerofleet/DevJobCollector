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

export function jobCardRoles(job) {
  const title = typeof job.title === 'string' ? job.title : '';
  const category = typeof job.jobCategory === 'string' ? job.jobCategory : '';
  const roles = ROLES.filter(([, pattern]) => pattern.test(title) || pattern.test(category)).map(([label]) => label);
  return roles.length ? roles : ['직무 확인 필요'];
}
