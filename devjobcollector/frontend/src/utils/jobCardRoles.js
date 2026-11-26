const JOB_FIELDS = new Set(['일반직', '행정직', '경영직', '사무직', '관리직', '연구직', '기술직', '시설직', '전산직',
  '교수직', '의사직', '간호직', '약무직', '보건직', '위생직', '기능직', '운영직', '공무직', '안전직',
  '교원직', '별정직', '계약직', '전문직', '상담직', '기타']);
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
  ['재무·자금', /\btreasury\s+specialist\b/i],
  ['재무·금융', /\bfinancial\s+operations\s+lead\b/i],
  ['법률 자문', /\bcounsel\s*[-–]\s*product\b/i],
  ['응용과학 연구원', /\bapplied\s+scientist\b/i],
  ['산업안전·위기관리', /\bpeople\s+safety\s*&\s*resilience\s+manager\b/i],
  ['정보보안', /\bprofessional\s+services\s+consultant\s*,\s*application\s+security\b/i],
  ['영업 개발 담당자', /\b(?:account|sales)\s+development\s+representative\b/i],
  ['IT 운영 엔지니어', /\bit\s+operations\s+engineer\b/i],
  ['인사 운영', /\b(?:hr|people)\s+operations\s+specialist\b/i],
  ['리테일 가격 운영', /리테일\s*가격\s*운영/],
  ['계정 관리 매니저', /\bmarketplace\s+account\s+manager\b/i],
  ['채용 운영', /\bcandidate\s+experience\s+specialist\b/i],
  ['기술 지원·품질', /\btechnical\s+quality\s+specialist\b/i],
  ['IT 지원', /\bit\s*어시스턴트/i],
  ['IT 인프라 운영', /\bit\s*인프라\s*담당/i],
  ['영업·계정 관리', /\bsales\s*&\s*account\s+manager\b/i],
  ['거버넌스·리스크·컴플라이언스 프로그램 매니저', /\bprogram\s+manager\s*,\s*governance\s*,\s*risk\s+(?:and|&)\s+compliance\b/i],
  ['Strategy Specialist', /\bstrategy\s+specialist\b/i],
  ['수요 창출 캠페인 매니저', /\bdemand\s+generation\s+campaign\s+manager\b/i],
  ['세일즈 엔지니어', /\bsales\s+engineer\b/i],
  ['AI·인사 혁신 인턴', /\bai\s+and\s+people\s+transformation\s+intern\b/i],
  ['엔지니어링 매니저', /\bengineering\s+manager\b/i],
  ['AI 엔지니어링 매니저', /\bmanager\s*,?\s*ai\s+forward\s+deployed\s+engineering\b/i],
  ['기술 지원 엔지니어', /\b(?:designated\s+support|technical\s+services)\s+engineer\b/i],
  ['프로젝트 매니저 보조', /\bproject\s+manager\s+assistant\b/i],
  ['고객 경험 매니저', /\bcustomer\s+experience\s+manager\b/i],
  ['채용 담당자', /\brecruiter\b/i],
  ['캠페인 마케팅 매니저', /\bcampaign\s+marketing\s+manager\b/i],
  ['임원 비서', /\bexecutive\s+assistant\b/i],
  ['재고 기획 분석가', /\binstock\s+planning\s+analyst\b/i],
  ['인사팀 인턴', /\bpeople\s+team\s+intern\b/i],
  ['고객 엔지니어링 매니저', /\b(?:manager\s*,?\s*customer\s+engineering|customer\s+engineering\s+manager)\b/i],
  ['지역 마케팅 매니저', /\bregional\s+marketing\s+manager\b/i],
  ['사업개발 담당자', /\bbusiness\s+development\s+representative\b/i],
  ['계정 관리 매니저', /\bmanager\s*,?\s*account\s+management\b/i],
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

export function resolveJobCardRoles(job) {
  const title = typeof job.title === 'string' ? job.title : '';
  const category = typeof job.jobCategory === 'string' ? job.jobCategory : '';
  const approvedRole = typeof job.approvedJobRole === 'string' ? job.approvedJobRole.trim() : '';
  if (approvedRole && approvedRole.length <= 32) return { roles: [approvedRole], needsReview: false };
  const fields = Array.isArray(job.approvedJobFields) ? [...new Set(job.approvedJobFields)] : [];
  if (fields.length > 0 && fields.length <= 3 && fields.every(field => JOB_FIELDS.has(field))
      && !(fields.length > 1 && fields.includes('기타'))) {
    return { roles: fields, needsReview: false };
  }
  if (job.id === 4517 && job.sourcePlatform === 'GREENHOUSE') {
    return { roles: ['백엔드', 'Global Operations Technology'], needsReview: false };
  }
  if (job.sourcePlatform === 'PUBLIC_ALIO') {
    const fields = [...new Set(category.split(/[,.]/).map(field => field.trim()).filter(Boolean))];
    return { roles: fields.length ? fields : ['채용 분야 미제공'], needsReview: false };
  }
  const roles = ROLES.filter(([, pattern]) => pattern.test(title) || pattern.test(category)).map(([label]) => label);
  const explicitRoles = [...new Set(title.match(OTHER_ROLE) || [])].filter(role => role !== '디자이너' || !roles.includes('UX·UI 디자인'));
  const englishRoles = OTHER_ENGLISH_ROLES.filter(([, pattern]) => pattern.test(title)).map(([label]) => label);
  if (/\boperations\s+manager\b/i.test(title)) {
    englishRoles.push(/\bfin\s*crime\b/i.test(title) ? '금융범죄 운영 매니저' : '운영 매니저');
  }
  if (/번역\s*pm\b/i.test(title)) {
    const language = /일본어|\bjapanese\b/i.test(title) ? '일본어 ' : '';
    const animation = /애니메이션|\banimation\b/i.test(title) ? '애니메이션 ' : '';
    englishRoles.push(`${language}${animation}번역 PM`);
  }
  if (/공연\s*기획\s*(?:·|및|\/|\s)*운영/.test(title)) englishRoles.push('공연 기획·운영');
  if (/콘텐츠\s*그로스\s*매니저/.test(title)) englishRoles.push('콘텐츠 그로스 매니저');
  if (/직매입\s*운영\s*MD\b/i.test(title)) englishRoles.push('직매입 운영 MD');
  if (/시설\s*관리\s*(?:\(FM\)\s*)?담당자/i.test(title)) englishRoles.push('시설관리 담당자');
  if (/마케팅\s*콘텐츠\s*제작[\s_·-]*어시스턴트/.test(title)) englishRoles.push('마케팅 콘텐츠 제작 어시스턴트');
  if (/\bgrowth\s*&\s*marketing\s+design\b/i.test(title)) {
    englishRoles.push(/인턴|\bintern(?:ship)?\b/i.test(title) ? '그로스·마케팅 디자인 인턴' : '그로스·마케팅 디자인');
  }
  if (/\blocali[sz]ation\s+pm\b/i.test(title)) {
    englishRoles.push(/일본어|\bjapanese\b/i.test(title) ? '일본어 현지화 PM' : '현지화 PM');
  }
  const filming = /\bcinematographer\b/i.test(title);
  const editing = /\bvideo\s+editor\b/i.test(title);
  if (filming && editing) englishRoles.push('영상 촬영·편집');
  else if (filming) englishRoles.push('영상 촬영');
  else if (editing) englishRoles.push('영상 편집');
  if (roles.length || explicitRoles.length || englishRoles.length) {
    return { roles: [...new Set([...roles, ...explicitRoles, ...englishRoles])], needsReview: false };
  }
  // 분류되지 않은 제목은 태그로 노출하지 않고 AI 검토 후보로 남긴다.
  const fallback = title.trim();
  return { roles: fallback && fallback !== '직무 확인 필요' ? ['직무 확인 필요'] : [], needsReview: Boolean(fallback),
    unmatched: Boolean(fallback && fallback !== '직무 확인 필요') };
}

export function jobCardRoles(job) {
  return resolveJobCardRoles(job).roles;
}
