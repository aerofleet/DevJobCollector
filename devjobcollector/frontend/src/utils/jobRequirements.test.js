import { test } from 'node:test';
import assert from 'node:assert/strict';
import { extractDaangnSections, extractExperienceWording, extractJobDuties, extractJobEmploymentType, extractJobRequirements, extractJobSalary, extractJobTechnologySections, extractMentionedTools, extractRollingDeadline, formatInfoGridValue, formatJobLocation } from './jobRequirements.js';

test('extracts Pinterest minimum years in product design without treating education as work experience', () => {
  const requirements = extractJobRequirements('What we’re looking for\n• Bachelor’s degree in design or equivalent experience.\n• Minimum 5 years in Product Design (or equivalent).\nRelocation Statement:');
  assert.equal(extractExperienceWording(requirements), '5년 이상');
  assert.equal(extractExperienceWording({ required: ['Minimum 5 years in education'], preferred: [] }), null);
  assert.equal(extractExperienceWording({ required: ['Minimum 5 years in Product Design experience'], preferred: [] }), '5년 이상');
});

test('separates MongoDB minimum and preferred qualifications', () => {
  const text = 'What You’ll Do\n• Harden hosts\nWhat We’re Looking For\nMinimum Qualifications:\n• 10+ years in SRE, Infrastructure Engineering, or Cloud Security Engineering\n• Strong coding skills in Go, Python, C, C++, or Rust\nPreferred Qualifications:\n• Practical experience with eBPF-based security tooling\nAbout MongoDB\nCompany information';
  assert.deepEqual(extractJobDuties(text), ['Harden hosts']);
  assert.deepEqual(extractJobRequirements(text), {
    required: ['10+ years in SRE, Infrastructure Engineering, or Cloud Security Engineering', 'Strong coding skills in Go, Python, C, C++, or Rust'],
    preferred: ['Practical experience with eBPF-based security tooling'],
  });
});

test('separates sales duties, required and preferred qualifications across heading variants', () => {
  const text = 'What you’ll do at Figma Weave:\n• Lead the sales team\n• Coach customer meetings\nWe\'d love to hear from you if you have:\n• 8+ years of sales experience\nWhile it’s not required, it’s an added plus if you also have:\n• Experience with MEDDIC\nAt Figma, one of our values is Grow as you go.\nCompany information';
  assert.deepEqual(extractJobDuties(text), ['Lead the sales team', 'Coach customer meetings']);
  assert.deepEqual(extractJobRequirements(text), { required: ['8+ years of sales experience'], preferred: ['Experience with MEDDIC'] });
  assert.deepEqual(extractJobDuties('What you’ll do at Acme:\n• Manage budgets\nBenefits:\n• Leave'), ['Manage budgets']);
});
import { recoverSectionHeading } from './jobSectionRecovery.js';

test('extracts French job duties and qualifications without workplace text', () => {
  const text = "Il s'agit d'un poste à temps plein basé au Canada.\nUne journée typique :\nSuperviser l'équipe\n• Gérer les dossiers\nVotre expertise:\n• 8 années ou plus d'expérience progressive, dont 5 années en gestion\nVos compétences et aptitudes:\n• Communication\nVotre lieu de travail:\nTélétravail au Canada";
  const requirements = extractJobRequirements(text);
  assert.deepEqual(extractJobDuties(text), ["Superviser l'équipe", 'Gérer les dossiers']);
  assert.equal(requirements.required.length, 2);
  assert.equal(extractExperienceWording(requirements), '8년 이상');
  assert.equal(extractJobEmploymentType(text), '풀타임');
});

test('separates inline Korean duties and required skills before preferred qualifications', () => {
  const text = '담당 업무 ﻿추천 모델 연구 - 모델 개발 - 서비스 운영\n\n필요 역량 ﻿- 경력 3년 이상 - Python 경험\n\n우대 사항 ﻿- 관련 학위\n\n전형 절차 및 안내 사항 ﻿- 서류 전형';
  assert.deepEqual(extractJobDuties(text), ['추천 모델 연구', '모델 개발', '서비스 운영']);
  assert.deepEqual(extractJobRequirements(text), { required: ['경력 3년 이상', 'Python 경험'], preferred: ['관련 학위'] });
});

test('separates mixed Databricks recruiting qualifications and contract terms', () => {
  const text = 'This is a 6 month contract, with an opportunity to get the same extended.\nThe impact you will have:\n• Source candidates\n• Build a pipeline\nWhat we look for:\n• Sourcing experience\n• Leadership hiring is a plus\n• Communication skills\n• Engineering hiring is a plus\nAbout Databricks\nCompany description\nBenefits\nHoliday';
  assert.deepEqual(extractJobDuties(text), ['Source candidates', 'Build a pipeline']);
  assert.deepEqual(extractJobRequirements(text), { required: ['Sourcing experience', 'Communication skills'], preferred: ['Leadership hiring is a plus', 'Engineering hiring is a plus'] });
  assert.equal(extractJobEmploymentType(text), '계약직 (6개월)');
  assert.equal(extractJobEmploymentType('A 6 month contract was previously completed.'), null);
});

test('recovers explicit question-style section titles without guessing from prose', () => {
  const text = '주요 업무는?\n• API 운영\n자격 요건은?\n• Java 경험\n우대 사항은?\n• AWS 경험\n채용 절차\n• 면접';
  assert.deepEqual(extractJobDuties(text), ['API 운영']);
  assert.deepEqual(extractJobRequirements(text), { required: ['Java 경험'], preferred: ['AWS 경험'] });
  assert.equal(recoverSectionHeading('Java 경험은 우대 사항입니다'), null);
  assert.equal(recoverSectionHeading('필수 및 우대 요건'), null);
  assert.equal(recoverSectionHeading('우대 사항이 없나요?'), null);
});

test('separates Channel Corp DevOps duties, qualifications and preferred experience', () => {
  const text = '어떤 기회가 있나요?\n• 기술 리더십\n어떤 업무를 담당하나요?\n• AWS와 Kubernetes 운영\n• Terraform 자동화\n어떤 경력과 역량이 필요한가요?\n• 문제 해결 능력: 분산 시스템 이슈 해결\n• Kubernetes 실무: 프로덕션 클러스터 운영\n• Infrastructure as Code: Terraform 활용\n• 프로그래밍 능력: 하나 이상의 언어\n• 학부 수준의 Computer Science 지식\n이런 점이 있으면 더 좋아요\n• 대규모 트래픽 경험\n• 비용 최적화: 클라우드 비용 30% 이상 절감\n• GitOps 실무\n• 보안 자동화\n• 마이그레이션 경험\n• Istio 서비스 메쉬 운영\n• Platform Engineering\n• 오픈소스 기여\n어떤 방식으로 채용하나요?\n• 서류 제출 후 인터뷰';
  const requirements = extractJobRequirements(text);
  assert.deepEqual(extractJobDuties(text), ['AWS와 Kubernetes 운영', 'Terraform 자동화']);
  assert.equal(requirements.required.length, 5);
  assert.equal(requirements.preferred.length, 8);
  assert.equal(requirements.preferred.at(-1), '오픈소스 기여');
  assert.equal(extractExperienceWording(requirements), null);
});

test('separates TwelveLabs role-fit and preferred qualifications from team links', () => {
  const text = `IN THIS ROLE, YOU WILL\n- API 개발\nYOU MAY BE A GOOD FIT IF YOU HAVE\n- 백엔드 엔지니어링 분야에서 5년 이상의 경력을 보유하신 분\n- 스타트업에서 프로젝트를 이끈 경험\n- Go, Java/Kotlin 프로덕션 시스템 운영 경험\n- 관계형 데이터베이스와 쿼리 최적화 경험\n- 이벤트 기반 분산 시스템 경험\n- Kubernetes, AWS 환경의 대규모 시스템 경험\n- 인증, 빌링, 조직 관리 구현 경험\nPREFERRED QUALIFICATIONS\n- B2B SaaS 경험\n- Go 실무 경험\n- AI/ML 모델 연동 경험\n- FFmpeg 영상 처리 경험\n- CI/CD 및 모니터링 경험\nREAD MORE ABOUT THE TEAM\n- 팀 인터뷰\nHIRING PROCESS\n서류 검토`;
  const result = extractJobRequirements(text);
  assert.equal(result.required.length, 7);
  assert.equal(result.preferred.length, 5);
  assert.equal(extractExperienceWording(result), '5년 이상');
  assert.ok(![...result.required, ...result.preferred].includes('팀 인터뷰'));
});

test('separates Daangn duties, required and preferred qualifications, notices and process', () => {
  const text = '이런 일을 해요\n• 정산 검증\n• 파트너 대사\n이런 분과 함께하고 싶어요\n• 데이터 대조\n• 소통 역량\n이런 분이면 더 좋아요!\n• 정산 실무 경력 1년 이상\n• 엑셀, SQL, 파이썬 경험\n참고해 주세요\n• 최초 3개월 계약 후 평가\n이렇게 합류해요\n1. 서류 전형 → 2. 직무 인터뷰 → 3. 최종 합격\n당근 전형 소개\n서류 전형\n일반 안내';
  const req = extractJobRequirements(text);
  assert.deepEqual(extractJobDuties(text), ['정산 검증', '파트너 대사']);
  assert.deepEqual(req.required, ['데이터 대조', '소통 역량']);
  assert.deepEqual(req.preferred, ['정산 실무 경력 1년 이상', '엑셀, SQL, 파이썬 경험']);
  assert.equal(extractExperienceWording(req), null);
  assert.deepEqual(extractDaangnSections(text), { notices: ['최초 3개월 계약 후 평가'], process: ['1. 서류 전형 → 2. 직무 인터뷰 → 3. 최종 합격'] });
  assert.deepEqual(extractMentionedTools(req, text), ['SQL', 'Python', 'Excel']);
});

test('separates industrial recruitment duties from all following sections', () => {
  const text = '지원 전, 꼭 확인해 주세요!\n• 보충역 신규 편입\n이런 일을 해요\n• 백엔드 서비스를 개발해요\n• 대규모 트래픽을 개선해요\n이런 분을 찾고 있어요\n• 서비스를 운영해 본 경험이 있는 분\n이런 분이면 더 좋아요!\n• Kafka 경험\n참고해 주세요\n• 3개월 수습\n이렇게 합류해요\n1. 서류 전형 → 2. 인터뷰\n팀 동료들의 한마디\n산업기능요원 복무 후기';
  assert.deepEqual(extractJobDuties(text), ['백엔드 서비스를 개발해요', '대규모 트래픽을 개선해요']);
  assert.deepEqual(extractJobRequirements(text), { required: ['서비스를 운영해 본 경험이 있는 분'], preferred: ['Kafka 경험'] });
  assert.deepEqual(extractDaangnSections(text), { notices: ['3개월 수습'], process: ['1. 서류 전형 → 2. 인터뷰'] });
});

test('extracts Instacart audit duties, minimum and preferred qualifications', () => {
  const text = "What you'll cook up in your first year\nAudit Delivery & Reporting\n• Audit AWS, GCP, Datadog, and Okta\nTools & Process Improvement\n• Implement Workiva\nThe secret ingredients we're looking for\nMust-have pantry staples (Minimum Qualifications)\n• 12-15 years of progressive professional experience, with at least 5 years in a leadership role\nOptional garnishes (Preferred Qualifications)\n• CISA, CRISC, or CIA certification.\n#LI-Remote";
  const result = extractJobRequirements(text);
  assert.equal(result.required.length, 1);
  assert.deepEqual(result.preferred, ['CISA, CRISC, or CIA certification.']);
  assert.deepEqual(extractJobDuties(text), ['Audit AWS, GCP, Datadog, and Okta', 'Implement Workiva']);
  assert.deepEqual(extractMentionedTools(result, text), ['Datadog', 'Okta', 'Workiva', 'AWS', 'GCP']);
});

test('extracts Woowahan duties, qualifications and office tools', () => {
  const text = '[업무내용]\n- 상품 등록\n[지원자격]\n- 오피스툴(Google Sheets, Excel 등) 활용 역량\n[우대사항]\n- 유통사 AMD 경험';
  const requirements = extractJobRequirements(text);
  assert.deepEqual(extractJobDuties(text), ['상품 등록']);
  assert.deepEqual(requirements.required, ['오피스툴(Google Sheets, Excel 등) 활용 역량']);
  assert.deepEqual(requirements.preferred, ['유통사 AMD 경험']);
  assert.deepEqual(extractMentionedTools(requirements, text), ['Google Sheets', 'Excel']);
});

test('extracts Kakao duties, required qualifications and preferred qualifications', () => {
  const text = '조직 소개\n생성 모델 연구\n업무내용\n- 모델 학습\n- 추론 최적화\n자격요건\n- 석사 이상 혹은 관련 프로젝트 경험\n- Python 및 PyTorch 경험\n우대사항\n- GPU 분산 학습 경험';
  const result = extractJobRequirements(text);
  assert.equal(result.required.length, 2);
  assert.deepEqual(result.preferred, ['GPU 분산 학습 경험']);
  assert.deepEqual(extractJobDuties(text), ['모델 학습', '추론 최적화']);
  assert.deepEqual(extractMentionedTools(result, text), ['Python', 'PyTorch']);
});

test('separates Tripbtoz preferred conditions from working conditions and benefits', () => {
  const text = '주요업무\n고객 문의 응대\n자격요건\n고객 서비스 분야에서 2년 이상 경험\n기본적인 IT 활용 능력(Google Workspace, Slack 등)\n우대사항\n여행, 호텔, OTA 분야 경험\n근무조건 및 근무환경\n근무형태 : 정규직(수습기간 3개월_급여100%)\n혜택 및 복지\n트립캐시 연 50만원';
  const result = extractJobRequirements(text);
  assert.deepEqual(result.preferred, ['여행, 호텔, OTA 분야 경험']);
  assert.deepEqual(extractMentionedTools(result, text), ['Google Workspace', 'Slack']);
  assert.equal(extractJobSalary(text), null);
});

test('extracts Cheiron square-marker sections and source-mentioned tools', () => {
  const text = "■ What You'll Do\n - Build backend services with Python, FastAPI, and Postgres\n■ Requirements\n - 2+ years of experience shipping production backend systems\n - Active use of Claude Code and Cursor\n■ Nice to Have\n - Experience with LangGraph and RAG\n - Experience on AWS, Terraform, and Kubernetes\n■ Benefits & Perks\n - Health insurance\n■ Interview Process\nScreening";
  const result = extractJobRequirements(text);
  assert.equal(result.required.length, 2);
  assert.equal(result.preferred.length, 2);
  assert.ok(!result.preferred.some(item => /Health insurance|Screening/.test(item)));
  assert.deepEqual(extractJobDuties(text), ['Build backend services with Python, FastAPI, and Postgres']);
  const tools = extractMentionedTools(result, text);
  for (const name of ['Python', 'FastAPI', 'PostgreSQL', 'RAG', 'LangGraph', 'AWS', 'Terraform', 'Kubernetes', 'Claude', 'Cursor']) assert.ok(tools.includes(name), name);
  assert.equal(extractJobSalary(text), null);
  assert.deepEqual(extractMentionedTools({ required: [], preferred: [] }, 'Move the cursor to the next page and use a rag.'), []);
});

test('extracts FriendliAI preferred experience without including benefits', () => {
  const text = 'QUALIFICATIONS\n- 3+ years of professional Python engineering building libraries, SDKs, or developer tools used in production.\nPREFERRED EXPERIENCE\n- Maintainer or significant OSS contributor in Python libraries.\n- Containers & local dev tooling (Docker); familiarity with Kubernetes basics.\nBENEFITS\n- Flexible working hours';
  const result = extractJobRequirements(text);
  assert.equal(result.required.length, 1);
  assert.deepEqual(result.preferred, ['Maintainer or significant OSS contributor in Python libraries.', 'Containers & local dev tooling (Docker); familiarity with Kubernetes basics.']);
});

test('extracts bracketed Nuua AI agent qualifications and mentioned tools', () => {
  const text = '[주요업무]\n• LangChain, LangGraph, OpenAI, Claude 및 Terraform 활용\n[자격요건]\n• 3년 이상의 백엔드 개발 및 운영 경험\n• C++, Python, Java, Go, Javascript 등 언어 무관\n[우대사항]\n• Python 기반 웹 프레임워크 활용 역량\n[혜택 및 복지]\n• 장기 근속 여행지원금';
  const requirements = extractJobRequirements(text);
  assert.equal(requirements.required.length, 2);
  assert.deepEqual(requirements.preferred, ['Python 기반 웹 프레임워크 활용 역량']);
  const tools = extractMentionedTools(requirements, text);
  for (const name of ['LangChain', 'LangGraph', 'Terraform', 'OpenAI', 'Claude', 'C++', 'Python', 'Java', 'Go', 'JavaScript']) assert.ok(tools.includes(name), name);
  assert.equal(extractJobSalary(text), null);
  assert.ok(!extractMentionedTools({ required: [], preferred: [] }, 'We go to the office').includes('Go'));
});

test('stops Nuua preferred qualifications before benefits and recruiting process', () => {
  const text = '자격요건\n• 개발 경력 3년 이상\n• Java, Spring Framework 개발 경험\n우대사항\n• 예약 플랫폼 연동 개발 경험\n혜택 및 복지\n• 장기 근속자 여행지원금 100만원\n채용 프로세스\n• 서류심사 → 면접';
  const requirements = extractJobRequirements(text);
  assert.deepEqual(requirements.required, ['개발 경력 3년 이상', 'Java, Spring Framework 개발 경험']);
  assert.deepEqual(requirements.preferred, ['예약 플랫폼 연동 개발 경험']);
  assert.deepEqual(extractMentionedTools(requirements, text), ['Java', 'Spring']);
  assert.equal(extractJobSalary(text), null);
});

test('extracts Discord counsel requirements and separates bonus qualifications without inventing tools', () => {
  const text = `What you'll be doing:\n• Negotiate API and SDK integration agreements.\nWhat you should have:\n• A JD and active membership in good standing with at least one U.S. state bar.\n• At least 4 years of commercial transactional experience, with a combination of top law firm and/or in-house experience at a technology company preferred.\n• Experience drafting and negotiating complex commercial agreements.\nBonus points:\n• Familiarity with payments, virtual currency, or subscription and bundling arrangements.\nThe base salary range for this full-time position is listed below.`;
  const result = extractJobRequirements(text);
  assert.equal(result.required.length, 3);
  assert.match(result.required[1], /At least 4 years/);
  assert.deepEqual(result.preferred, ['Familiarity with payments, virtual currency, or subscription and bundling arrangements.']);
  assert.deepEqual(extractMentionedTools(result, text), []);
});

test('shows tools from the whole posting and preserves explicit technology lists', () => {
  const text = '# 팀 소개\nKubernetes와 Cloud Native 활용\n# 이력서 추천\nAWS와 CI/CD 서버 운영\n# 토스에서 사용하는 기술\n- Java Spring Framework, JPA/Hibernate, Gradle\n- Jenkins, Git, Docker, Kubernetes + Istio\n- Kafka, Elastic, InfluxData, Grafana, Memcached\n# 꼭 확인해주세요\n기타 안내';
  assert.deepEqual(extractJobTechnologySections(text), ['Java Spring Framework, JPA/Hibernate, Gradle', 'Jenkins, Git, Docker, Kubernetes + Istio', 'Kafka, Elastic, InfluxData, Grafana, Memcached']);
  const tools = extractMentionedTools(extractJobRequirements(text), text);
  for (const name of ['Java', 'Spring', 'JPA', 'Hibernate', 'Gradle', 'Jenkins', 'Git', 'Docker', 'Kubernetes', 'Istio', 'Kafka', 'Elastic', 'InfluxData', 'Grafana', 'Memcached', 'AWS', 'CI/CD']) assert.ok(tools.includes(name), name);
  assert.deepEqual(extractMentionedTools({ required: [], preferred: [] }, 'JavaScript와 GitLab 사용'), ['JavaScript']);
  assert.deepEqual(extractJobTechnologySections('<h3>기술 스택</h3><ul><li>UnknownTool, FooDB</li></ul><h3>복리후생</h3><p>휴가</p>'), ['UnknownTool, FooDB']);
});

test('recognizes total engineering experience with a management minimum without adding them', () => {
  const text = 'Minimum requirements\n• 10+ total years of engineering experience with a minimal 4 years of management experience.\nPreferred qualifications\n• Domain knowledge in payments';
  assert.equal(extractExperienceWording(extractJobRequirements(text)), '10년 이상');
  assert.equal(extractExperienceWording(extractJobRequirements('Preferred qualifications\n• 10+ total years of engineering experience with a minimal 4 years of management experience.')), null);
  assert.equal(extractExperienceWording(extractJobRequirements('Minimum requirements\n• 10+ total years of engineering experience or a minimal 4 years of management experience.')), null);
});

test('separates repeated PagerDuty qualification sections and stops before salary and workplace text', () => {
  const text = '<p>PREFERRED QUALIFICATIONS</p><ul><li>Experience in GTM strategy and operations</li></ul><p>BASIC QUALIFICATIONS</p><ul><li>10+ years in management consulting or sales</li><li>Bachelor\'s degree in Business</li></ul><p>PREFERRED QUALIFICATIONS</p><ul><li>MBA or equivalent advanced degree</li><li>Salesforce, Tableau, Anaplan, Gong, Snowflake</li></ul><p>The base salary range for this position is 147,000 - 222,000 USD. This role may also be eligible for bonus.</p><p>PagerDuty is a flexible, hybrid workplace.</p><p>Where we work</p><p>Atlanta office two times per week.</p>';
  assert.deepEqual(extractJobRequirements(text), {
    required: ['10+ years in management consulting or sales', "Bachelor's degree in Business"],
    preferred: ['Experience in GTM strategy and operations', 'MBA or equivalent advanced degree', 'Salesforce, Tableau, Anaplan, Gong, Snowflake'],
  });
  assert.match(extractJobSalary(text), /147,000 - 222,000 USD/);
  assert.deepEqual(extractJobRequirements('Basic qualifications\n• Required experience\nPreferred qualifications\n• Preferred experience\nThe base salary range for this position is 100,000 USD\nCompany information'), { required: ['Required experience'], preferred: ['Preferred experience'] });
});

test('removes parenthetical details only from compact summary values', () => {
  assert.equal(formatInfoGridValue('인턴 (6개월, 정규직 전환 비대상)'), '인턴');
  assert.equal(formatInfoGridValue('서울 (강남 (역삼)) · 부산 （해운대）'), '서울 · 부산');
  assert.equal(formatInfoGridValue('경력 2년 이상(협의 가능)'), '경력 2년 이상');
  assert.equal(formatInfoGridValue('(확인 필요)'), '');
  assert.equal(formatInfoGridValue('서울 (미완성'), '서울 (미완성');
});

test('extracts labeled Korean role sections, internship terms and rolling deadline', () => {
  const text = '우리 팀과 함께할 미션을 소개합니다.\n• 산업 리서치\n• 일본 현지 통역\n이런 경험을 가진 분과 함께 성장하고 싶습니다! (필수요건)\n• 한국어/일본어 역량\n• 해외 출장 가능\n이런 경험들이 있다면 저희가 찾는 그 분입니다! (우대요건)\n• 영어 역량\n• 유학 경험\n크래프톤의 도전에 함께 하기 위해 아래의 전형 과정이 필요합니다.\n• 서류 전형\n• 상시 채용으로 채용 완료 시 조기 마감될 수 있습니다.\n고용형태\n• 인턴 (6개월, 정규직 전환 비대상)';
  const req = extractJobRequirements(text);
  assert.deepEqual(extractJobDuties(text), ['산업 리서치', '일본 현지 통역']);
  assert.deepEqual(req.required, ['한국어/일본어 역량', '해외 출장 가능']);
  assert.deepEqual(req.preferred, ['영어 역량', '유학 경험']);
  assert.equal(extractExperienceWording(req), null);
  assert.equal(extractJobEmploymentType(text), '인턴 (6개월, 정규직 전환 비대상)');
  assert.equal(extractRollingDeadline(text), '상시 채용 (채용 완료 시 조기 마감 가능)');
  assert.equal(extractRollingDeadline('상시 채용이 아닙니다.'), null);
  assert.equal(extractJobEmploymentType('인턴 경험 우대'), null);
});

test('classifies role-fit requirements and years in a professional field', () => {
  const text = 'The Opportunity:\n• Develop forecasts\n• Build dashboards\nHow Do I Know if the Role is Right For Me?\n• 6+ years in analytics or strategy, ideally at a marketplace.\n• Proficient in SQL and Python\nCompensation & Benefits\n• 12+ years in analytics';
  const req = extractJobRequirements(text);
  assert.deepEqual(extractJobDuties(text), ['Develop forecasts', 'Build dashboards']);
  assert.equal(req.required.length, 2);
  assert.equal(extractExperienceWording(req), '6년 이상');
  assert.deepEqual(extractMentionedTools(req), ['Python', 'SQL']);
  assert.equal(extractExperienceWording(extractJobRequirements('How Do I Know if the Role is Right For Me?\n• 6+ years in college')), null);
  assert.deepEqual(extractJobDuties('The Opportunity:\n• Company overview'), []);
});

test('extracts minimum years in a looking-for section without treating preferred experience as required', () => {
  assert.equal(extractExperienceWording(extractJobRequirements('What we’re looking for:\n• Minimum 5 years experience scoping product research, ideally in ad tech')), '5년 이상');
  assert.equal(extractExperienceWording(extractJobRequirements('What we’re looking for:\n• Minimum 5 years experience preferred')), null);
  assert.equal(extractExperienceWording(extractJobRequirements('Preferred qualifications\n• Minimum 5 years experience')), null);
});

test('separates inline NAVER-style sections and preserves alternative qualifications', () => {
  const text = "What You'll Do ﻿· 데이터 수집·정제 파이프라인 구축 ﻿· 로봇 제어 연구\n\nRequired Skills ﻿· 경력구분 : 무관 (신입 및 경력) · ※ 석사/박사 학위 소지 또는 5년 이상의 연구/개발 경력 · C++, Python, ROS2, PyTorch, JAX 전문성\n\nPreferred Skills ﻿· LeRobot 활용 경험 ﻿· MuJoCo, Isaac Sim 사용 경험\n\n채용하고 싶은 사람 ﻿· 소통하는 사람\n\n전형절차 및 기타사항 ﻿· 서류전형";
  const result = extractJobRequirements(text);
  assert.deepEqual(extractJobDuties(text), ['데이터 수집·정제 파이프라인 구축', '로봇 제어 연구']);
  assert.equal(result.required.length, 3);
  assert.equal(result.preferred.length, 2);
  assert.match(result.required[1], /학위 소지 또는 5년/);
  assert.equal(extractExperienceWording(result), '경력무관');
  assert.deepEqual(extractMentionedTools(result), ['Python', 'C++', 'ROS2', 'PyTorch', 'JAX', 'LeRobot', 'MuJoCo', 'Isaac Sim']);
  assert.equal(extractExperienceWording(extractJobRequirements('자격 조건\n- 석사 학위 또는 5년 이상의 개발 경력')), null);
});
import { jobSourceLabel } from './jobSource.js';

test('separates mixed required and preferred clauses from duties and pay information', () => {
  const text = 'Responsibilities\n• Advise on compensation programs.\nQualifications & Experience Required\n• JD and state bar membership.\n• 10+ years of employment-law experience; in-house experience strongly preferred.\n• Experience advising on privacy is strongly preferred.\nPay Range Transparency\nThe salary varies by location.\nZone 2 Pay Range\n$197,800—$272,050 USD\nAbout Databricks\nCompany introduction.';
  const requirements = extractJobRequirements(text);
  assert.deepEqual(extractJobDuties(text), ['Advise on compensation programs.']);
  assert.deepEqual(requirements.required, ['JD and state bar membership.', '10+ years of employment-law experience']);
  assert.deepEqual(requirements.preferred, ['in-house experience strongly preferred.', 'Experience advising on privacy is strongly preferred.']);
  assert.equal(extractExperienceWording(requirements), '10년 이상');
  assert.equal(extractJobSalary(text), 'Zone 2 Pay Range $197,800—$272,050 USD');
  assert.equal(extractJobSalary('compensation plans\nClear, audience-appropriate guidance.'), null);
});

test('separates MongoDB role qualifications from duties and success measures', () => {
  const text = 'What you’ll do\n• Manage and coach engineers\n• Drive service reliability\nWe’re looking for someone who\n• Has at least 8 years of professional software development experience\n• Has at least 4 years of people management experience and performance management\n• Has experience with at least one major cloud provider technology (AWS, Azure, GCP)\nSuccess Measures\n• In 1 month, learn the system\nAbout MongoDB\nMongoDB builds databases';
  const requirements = extractJobRequirements(text);
  assert.deepEqual(extractJobDuties(text), ['Manage and coach engineers', 'Drive service reliability']);
  assert.deepEqual(requirements.required, ['Has at least 8 years of professional software development experience', 'Has at least 4 years of people management experience and performance management', 'Has experience with at least one major cloud provider technology (AWS, Azure, GCP)']);
  assert.deepEqual(extractMentionedTools(requirements), ['AWS', 'Azure', 'GCP']);
  assert.equal(extractExperienceWording(requirements), null);
});

test('separates responsibilities, required skills, tools and Philippine salary', () => {
  const text = "What you'll do:\n• Resolve customer inquiries.\n• Investigate product issues.\nRequired Skills and Experience:\n• 2+ years of experience in customer support.\n• Proficiency with Salesforce Service Cloud, JIRA, or Amazon Connect.\nPID - P78871\nPay Transparency Notice: annual base salary.\nAnnual base salary range (excluding bonus):\n₱798,300—₱798,300 PHP\n• Application Limit: maximum 3 applications.";
  const requirements = extractJobRequirements(text);
  assert.deepEqual(extractJobDuties(text), ['Resolve customer inquiries.', 'Investigate product issues.']);
  assert.deepEqual(requirements.required, ['2+ years of experience in customer support.', 'Proficiency with Salesforce Service Cloud, JIRA, or Amazon Connect.']);
  assert.deepEqual(requirements.preferred, []);
  assert.equal(extractExperienceWording(requirements), '2년 이상');
  assert.deepEqual(extractMentionedTools(requirements), ['JIRA', 'Salesforce Service Cloud', 'Amazon Connect']);
  assert.equal(extractJobSalary(text), 'Annual base salary range (excluding bonus): ₱798,300—₱798,300 PHP');
});

test('stops responsibilities at unfamiliar labeled sections', () => {
  assert.deepEqual(extractJobDuties("What you'll do:\n- Build services\nTools used:\n- Java\nPay transparency:\n$100,000"), ['Build services']);
  assert.equal(extractExperienceWording(extractJobRequirements('Preferred qualifications:\n- 2+ years of experience')), null);
});

test('extracts Toss qualifications and preferred experience without pulling in the recruitment process', () => {
  const text = '# 합류하면 함께 할 업무예요\n- 자금 집행\n# 이런 분과 함께하고 싶어요\n- 자금 오퍼레이션 업무를 2년 이상 경험하신 분이면 좋아요\n- SAP 실무 경험을 반드시 갖추신 분\n# 이런 경험이 있다면 더 좋아요\n- 자금 프로세스 개선 경험\n# 토스로의 합류 여정\n- 서류 접수';
  const result = extractJobRequirements(text);
  assert.deepEqual(extractJobDuties(text), ['자금 집행']);
  assert.deepEqual(result, { required: ['자금 오퍼레이션 업무를 2년 이상 경험하신 분이면 좋아요', 'SAP 실무 경험을 반드시 갖추신 분'], preferred: ['자금 프로세스 개선 경험'] });
  assert.equal(extractExperienceWording(result), '2년 이상');
  assert.deepEqual(extractMentionedTools(result), ['SAP']);
  assert.deepEqual(extractJobRequirements('# 지원 조건\n- 서울 근무 가능\n# 전형 일정\n- 지원 접수 마감').required, ['서울 근무 가능']);
});

test('does not turn preferred or competing experience claims into a headline', () => {
  assert.equal(extractExperienceWording(extractJobRequirements('우대사항\n- 개발 경력 3년 이상')), null);
  assert.equal(extractExperienceWording(extractJobRequirements('자격 요건\n- 개발 경력 3년 이상\n- 운영 경력 5년 이상')), null);
  assert.equal(extractExperienceWording(extractJobRequirements('자격 요건\n- 개발 경력 3년 이상 우대')), null);
});

test('stops Coupang preferred requirements at recruitment information', () => {
  const result = extractJobRequirements('자격 요건\n• 학사 이상\n우대 사항\n• SQL 경험\n전형 절차 및 안내 사항\n전형절차\n• 서류 전형\n참고사항\n• 조기 마감 가능');
  assert.deepEqual(result, { required: ['학사 이상'], preferred: ['SQL 경험'] });
});

test('preserves source technology section lines and stops at the next heading', () => {
  const text = '# 토스가 사용하는 기술\n- 코어: React, TypeScript, Next.js\n- 상태 관리: Tanstack Query, Jotai\n# 토스로의 합류 여정\n- 서류 접수';
  assert.deepEqual(extractJobTechnologySections(text), ['코어: React, TypeScript, Next.js', '상태 관리: Tanstack Query, Jotai']);
  assert.deepEqual(extractJobTechnologySections('# 업무\n- React를 사용해요'), []);
});

test('shows structured Lever salary with currency, interval, and conditions', () => {
  const salary = 'Salary: AUD 100000.5 – 150000 (per-year-salary) — Base pay; excludes bonus.';
  assert.equal(extractJobSalary(salary), salary);
  assert.equal(extractJobSalary('Salary: AUD from 100000 (per-year-salary)'), 'Salary: AUD from 100000 (per-year-salary)');
  assert.deepEqual(extractJobRequirements(`Preferred qualifications\n- Java\n${salary}\nAdditional information`), { required: [], preferred: ['Java'] });
});

test('preserves long salary statements with compensation context', () => {
  const statement = `The annual on target earnings range for this role is A$306,500–A$459,700. ${'Additional context. '.repeat(12)}This range applies to the primary location.`;
  assert.equal(extractJobSalary(`Compensation\n${statement}`), `Compensation ${statement}`);
});

test('preserves long requirements and stops before position details', () => {
  const items = Array.from({ length: 15 }, (_, i) => `- Requirement ${i}`);
  const longItem = 'x'.repeat(501);
  const content = ['Minimum requirements', ...items, `- ${longItem}`, 'Preferred qualifications', '- Preferred item', 'Position details', 'Applications close: 30th November 2026'].join('\n');
  const result = extractJobRequirements(content);
  assert.equal(result.required.length, 16);
  assert.equal(result.required[15], longItem);
  assert.deepEqual(result.preferred, ['Preferred item']);
});

test('extracts only named requirement sections and stops at other headings', () => {
  const result = extractJobRequirements('주요 업무\n- 업무 내용\n[자격 요건]\n- Java 경험\n- SQL 경험\n우대사항\n• 클라우드 경험\n복리후생\n- 식대');
  assert.deepEqual(result, { required: ['Java 경험', 'SQL 경험'], preferred: ['클라우드 경험'] });
});

test('does not classify unstructured content and limits extracted items', () => {
  assert.deepEqual(extractJobRequirements('경력 3년 이상의 개발자를 찾습니다.'), { required: [], preferred: [] });
  assert.deepEqual(extractJobRequirements('Requirements\n- one\n- two\n- three\n- four\nBenefits\n- holiday').required, ['one', 'two', 'three', 'four']);
});

test('extracts Coinbase qualifications, preferred tools and labeled salary separately', () => {
  const html = '<p>What we look for in you (i.e. job requirements):</p><ul><li>Shift and weekend work is a requirement.</li><li>Minimum of 2-3 years of relevant experience.</li></ul><p>Nice to haves:</p><ul><li>Understanding of Google apps, JIRA, Salesforce Service Cloud, or Amazon Connect.</li></ul><p>Requirements:</p><ul><li>Use Gemini responsibly.</li></ul><p>#P78778</p><p>Pay Transparency Notice: The target annual base salary is below.</p><div>Annual base salary range (excluding bonus):</div><div><span>₹700,000</span><span>&mdash;</span><span>₹700,000 INR</span></div><p>Application Limit: 3 applications.</p>';
  const result = extractJobRequirements(html);
  assert.deepEqual(result.required, ['Shift and weekend work is a requirement.', 'Minimum of 2-3 years of relevant experience.', 'Use Gemini responsibly.']);
  assert.deepEqual(result.preferred, ['Understanding of Google apps, JIRA, Salesforce Service Cloud, or Amazon Connect.']);
  assert.deepEqual(extractMentionedTools(result), ['Google apps', 'JIRA', 'Salesforce Service Cloud', 'Amazon Connect', 'Gemini']);
  assert.equal(extractJobSalary(html), 'Annual base salary range (excluding bonus): ₹700,000—₹700,000 INR');
  assert.equal(extractJobSalary('Requirements\n5 years of experience'), null);
});

test('extracts Pinterest requirements and salary across intervening context', () => {
  const html = '<p>What we’re looking for:</p><ul><li>Minimum 10 years experience in high-scale infrastructure.</li><li>Experience with IaC or CI/CD.</li></ul><p>In-Office Requirement Statement:</p><ul><li>Office visits 1–2 times every 6 months.</li></ul><p>In an effort to provide greater transparency, we are sharing the base salary range for this position. The position is also eligible for equity. Final salary depends on location and experience.</p><p>Information about benefits is available here.</p><div>US based applicants only</div><div>$177,185&mdash;$364,795 USD</div><p>Our Commitment to Inclusion:</p>';
  const result = extractJobRequirements(html);
  assert.deepEqual(result.required, ['Minimum 10 years experience in high-scale infrastructure.', 'Experience with IaC or CI/CD.']);
  assert.deepEqual(result.preferred, []);
  assert.deepEqual(extractMentionedTools(result), ['IaC', 'CI/CD']);
  assert.equal(extractJobSalary(html), '기본급 US based applicants only $177,185—$364,795 USD');
  assert.equal(extractJobSalary('Salary is competitive.\nOur Commitment to Inclusion:\n$123,000 USD'), null);
});

test('recognizes Figma required and preferred sections and deduplicates locations', () => {
  const text = '<p>We\'d love to hear from you if you have:</p><ul><li>3+ years of experience leading engineering teams</li><li>Experience using large language models</li></ul><p>While not required, it’s an added plus if you also have:</p><ul><li>Understanding of design systems</li></ul><p>At Figma, one of our values is Grow as you go.</p><p>Pay Transparency Disclosure</p>';
  const result = extractJobRequirements(text);
  assert.deepEqual(result.required, ['3+ years of experience leading engineering teams', 'Experience using large language models']);
  assert.deepEqual(result.preferred, ['Understanding of design systems']);
  assert.deepEqual(extractMentionedTools(result), ['LLM']);
  assert.equal(formatJobLocation('New York, NY; San Francisco, CA • New York, NY • United States'), 'New York, NY · San Francisco, CA · United States');
  assert.equal(formatJobLocation('Seoul; 서울; Remote'), 'Seoul · 서울 · Remote');
});

test('extracts inline Kakao requirement sections without unrelated text', () => {
  const result = extractJobRequirements('◆ 조직소개 - 소개 내용 ◆ 지원자격 - 백엔드 개발 경력 3년 이상인 분 ◆ 우대사항 - 실무 경력 7년 이상인 분 ◆ 접수 방법 - 채용사이트 지원');
  assert.deepEqual(result, { required: ['백엔드 개발 경력 3년 이상인 분'], preferred: ['실무 경력 7년 이상인 분'] });
});

test('extracts technology names from Kakao qualifications without matching substrings', () => {
  const text = '◆ 지원자격 - 백엔드 서버 개발 경력 만 3년 이상이신 분 - Java/Kotlin Spring 기반 웹/애플리케이션/REST API 개발 경험이 있으신 분 - RDBMS, NoSQL 스키마 설계 및 활용 경험이 있으신 분 - Docker와 Kubernetes에 대한 개념 ◆ 우대사항 - JavaScript 경험';
  assert.deepEqual(extractMentionedTools(extractJobRequirements(text)), ['Java', 'Kotlin', 'JavaScript', 'Spring', 'RDBMS', 'NoSQL', 'Docker', 'Kubernetes']);
  assert.deepEqual(extractMentionedTools(extractJobRequirements('자격 요건\nJavaScript 경험')), ['JavaScript']);
});

test('recognizes iOS without treating general frameworks and business domains as technologies', () => {
  const result = extractJobRequirements('자격 조건\nNative iOS 개발 경력 5년 이상으로, iOS 플랫폼 및 주요 Framework에 대한 깊은 이해\n우대 사항\nE-commerce, O2O 또는 대규모 모바일 서비스 개발 경험');
  assert.deepEqual(extractMentionedTools(result), ['iOS']);
});

test('recognizes databases mentioned in preferred qualifications', () => {
  const result = extractJobRequirements('지원자격\n객체지향 언어 경험\n우대사항\nJava/Spring 기반 백엔드 개발 경험\nPostgreSQL, MongoDB 등을 활용한 데이터베이스 설계 및 개발 경험');
  assert.deepEqual(extractMentionedTools(result), ['Java', 'Spring', 'PostgreSQL', 'MongoDB']);
});

test('extracts GitLab qualifications independently of technology and stops at team information', () => {
  const html = '<p>What You’ll Bring&nbsp;</p><ul><li>Experience supporting multiple senior leaders.</li><li>Proficiency with tools such as Google Workspace, Slack and Zoom.</li><li>Clear, approachable communication across functions.</li></ul><p>About the team</p><p>You will join our team.</p><p>Benefits</p><ul><li>Flexible Paid Time Off</li></ul>';
  assert.deepEqual(extractJobRequirements(html), { required: ['Experience supporting multiple senior leaders.', 'Proficiency with tools such as Google Workspace, Slack and Zoom.', 'Clear, approachable communication across functions.'], preferred: [] });
  assert.deepEqual(extractJobRequirements('What you bring\n- Communication skills\nBonus points\n- Event planning experience\nAbout the team\nTeam description'), { required: ['Communication skills'], preferred: ['Event planning experience'] });
});

test('extracts Airbnb expertise, inline preferred item and pay range', () => {
  const html = '<p>Your Expertise:</p><ul><li>4+ years of accounting experience, with at least 1 year in month-end close.</li><li>Advanced Excel skills.</li><li>Preferred: public accounting experience; SQL or another database query language.</li></ul><p>Your Location:</p><p>US Remote Eligible.</p><div>Pay Range</div><div>$106,000&mdash;$125,000 USD</div>';
  const result = extractJobRequirements(html);
  assert.deepEqual(result.required, ['4+ years of accounting experience, with at least 1 year in month-end close.', 'Advanced Excel skills.']);
  assert.deepEqual(result.preferred, ['public accounting experience; SQL or another database query language.']);
  assert.deepEqual(extractMentionedTools(result), ['SQL', 'Excel']);
  assert.equal(extractJobSalary(html), 'Pay Range $106,000—$125,000 USD');
});

test('reads encoded HTML requirement sections and retains adjacent text', () => {
  const result = extractJobRequirements('&lt;p&gt;&lt;strong&gt;자격&lt;/strong&gt; &lt;strong&gt;요건&lt;/strong&gt;&lt;/p&gt;&lt;ul&gt;&lt;li&gt;&lt;strong&gt;3&lt;/strong&gt;&lt;strong&gt;년 이상&lt;/strong&gt;의 실무 경력&lt;/li&gt;&lt;/ul&gt;');
  assert.deepEqual(result.required, ['3년 이상의 실무 경력']);
});

test('labels known sources and falls back when the source is missing', () => {
  assert.equal(jobSourceLabel({ sourcePlatform: 'COMPANY_PAGE', originalUrl: 'https://recruit.navercorp.com/jobs/1' }), '네이버 채용');
  assert.equal(jobSourceLabel({ sourcePlatform: 'GREENHOUSE' }), 'Greenhouse');
  assert.equal(jobSourceLabel({}), '출처 정보 없음');
});
