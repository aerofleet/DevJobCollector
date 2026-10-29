import { test } from 'node:test';
import assert from 'node:assert/strict';
import { extractJobRequirements, extractJobSalary, extractMentionedTools } from './jobRequirements.js';
import { jobSourceLabel } from './jobSource.js';

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

test('extracts inline Kakao requirement sections without unrelated text', () => {
  const result = extractJobRequirements('◆ 조직소개 - 소개 내용 ◆ 지원자격 - 백엔드 개발 경력 3년 이상인 분 ◆ 우대사항 - 실무 경력 7년 이상인 분 ◆ 접수 방법 - 채용사이트 지원');
  assert.deepEqual(result, { required: ['백엔드 개발 경력 3년 이상인 분'], preferred: ['실무 경력 7년 이상인 분'] });
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
