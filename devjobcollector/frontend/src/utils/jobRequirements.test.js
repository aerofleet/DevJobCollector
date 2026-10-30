import { test } from 'node:test';
import assert from 'node:assert/strict';
import { extractExperienceWording, extractJobDuties, extractJobRequirements, extractJobSalary, extractJobTechnologySections, extractMentionedTools, formatJobLocation } from './jobRequirements.js';
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
