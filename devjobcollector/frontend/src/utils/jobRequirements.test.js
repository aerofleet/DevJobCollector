import { test } from 'node:test';
import assert from 'node:assert/strict';
import { extractJobRequirements, extractJobSalary, extractMentionedTools, formatJobLocation } from './jobRequirements.js';
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

test('reads encoded HTML requirement sections and retains adjacent text', () => {
  const result = extractJobRequirements('&lt;p&gt;&lt;strong&gt;자격&lt;/strong&gt; &lt;strong&gt;요건&lt;/strong&gt;&lt;/p&gt;&lt;ul&gt;&lt;li&gt;&lt;strong&gt;3&lt;/strong&gt;&lt;strong&gt;년 이상&lt;/strong&gt;의 실무 경력&lt;/li&gt;&lt;/ul&gt;');
  assert.deepEqual(result.required, ['3년 이상의 실무 경력']);
});

test('labels known sources and falls back when the source is missing', () => {
  assert.equal(jobSourceLabel({ sourcePlatform: 'COMPANY_PAGE', originalUrl: 'https://recruit.navercorp.com/jobs/1' }), '네이버 채용');
  assert.equal(jobSourceLabel({ sourcePlatform: 'GREENHOUSE' }), 'Greenhouse');
  assert.equal(jobSourceLabel({}), '출처 정보 없음');
});
