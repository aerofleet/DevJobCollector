import { test } from 'node:test';
import assert from 'node:assert/strict';
import { jobCardTags } from './jobCardTags.js';

test('개발 공고는 승인된 직무 분야 대신 중복 없는 기술 스택을 표시한다', () => {
  assert.deepEqual(jobCardTags({ title: '백엔드 개발자', approvedJobFields: ['전산직'],
    techStacks: [{ stackName: 'Java' }, { stackName: ' Spring ' }], mentionedTechStacks: ['java', 'MySQL'] }),
  { label: '기술 스택', tags: ['Java', 'Spring', 'MySQL'] });
});

test('알리오 개발 공고에 기술 스택이 없으면 확인 필요를 표시한다', () => {
  assert.deepEqual(jobCardTags({ sourcePlatform: 'PUBLIC_ALIO', title: '백엔드 개발자', jobCategory: '정보통신' }),
    { label: '기술 스택', tags: ['기술 스택 확인 필요'] });
});

test('웹 및 게임 개발자도 기술 스택을 표시한다', () => {
  for (const title of ['Web Developer', '게임 개발자', 'iOS Engineer']) {
    assert.deepEqual(jobCardTags({ title, techStacks: [{ stackName: 'Swift' }] }),
      { label: '기술 스택', tags: ['Swift'] });
  }
});

test('비개발 공고는 기술 언급이 있어도 승인된 직무 분야를 표시한다', () => {
  assert.deepEqual(jobCardTags({ sourcePlatform: 'PUBLIC_ALIO', title: '기간제 사무원', approvedJobFields: ['사무직', '계약직'],
    mentionedTechStacks: ['Excel'] }), { label: '채용 분야', tags: ['사무직', '계약직'] });
});
