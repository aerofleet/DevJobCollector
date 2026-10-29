import { test } from 'node:test';
import assert from 'node:assert/strict';
import { extractJobRequirements } from './jobRequirements.js';
import { jobSourceLabel } from './jobSource.js';

test('extracts only named requirement sections and stops at other headings', () => {
  const result = extractJobRequirements('주요 업무\n- 업무 내용\n[자격 요건]\n- Java 경험\n- SQL 경험\n우대사항\n• 클라우드 경험\n복리후생\n- 식대');
  assert.deepEqual(result, { required: ['Java 경험', 'SQL 경험'], preferred: ['클라우드 경험'] });
});

test('does not classify unstructured content and limits extracted items', () => {
  assert.deepEqual(extractJobRequirements('경력 3년 이상의 개발자를 찾습니다.'), { required: [], preferred: [] });
  assert.deepEqual(extractJobRequirements('Requirements\n- one\n- two\n- three\n- four\nBenefits\n- holiday').required, ['one', 'two', 'three']);
});

test('extracts inline Kakao requirement sections without unrelated text', () => {
  const result = extractJobRequirements('◆ 조직소개 - 소개 내용 ◆ 지원자격 - 백엔드 개발 경력 3년 이상인 분 ◆ 우대사항 - 실무 경력 7년 이상인 분 ◆ 접수 방법 - 채용사이트 지원');
  assert.deepEqual(result, { required: ['백엔드 개발 경력 3년 이상인 분'], preferred: ['실무 경력 7년 이상인 분'] });
});

test('labels known sources and falls back when the source is missing', () => {
  assert.equal(jobSourceLabel({ sourcePlatform: 'COMPANY_PAGE', originalUrl: 'https://recruit.navercorp.com/jobs/1' }), '네이버 채용');
  assert.equal(jobSourceLabel({ sourcePlatform: 'GREENHOUSE' }), 'Greenhouse');
  assert.equal(jobSourceLabel({}), '출처 정보 없음');
});
