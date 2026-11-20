import { test } from 'node:test';
import assert from 'node:assert/strict';
import { previewJobRoleCandidates } from './jobRoleCandidates.js';

test('공개 카드 정책을 그대로 사용하고 중복 및 승인된 공고를 제외한다', () => {
  const title = 'Manager, Selection Operations (Product Lifecycle Selection)';
  const result = previewJobRoleCandidates([
    { id: 13758, title, sourcePlatform: 'GREENHOUSE', originalUrl: 'https://example.com/job/1' },
    { id: 13758, title },
    { id: 2, title, originalUrl: 'https://example.com/job/1' },
    { id: 3, title: 'Senior Treasury Specialist, Bridge Capital & Liquidity Risk – Digital Assets' },
    { id: 4, title, approvedJobRole: '상품 운영 매니저' },
    { id: 5, title, sourcePlatform: 'PUBLIC_ALIO' },
    { id: 0, title },
  ]);
  assert.deepEqual(result.candidates.map(job => job.id), [13758]);
  assert.deepEqual(result.counts, { input: 7, candidates: 1, duplicate: 2, invalid: 1, alreadyHandled: 3 });
});

test('후보 미리보기는 입력 순서와 원본 객체를 보존한다', () => {
  const jobs = [{ id: 9, title: 'A completely unknown and long job title for role review' },
    { id: 1, title: 'Another completely unknown and long title for review' }];
  const before = structuredClone(jobs);
  assert.deepEqual(previewJobRoleCandidates(jobs).candidates.map(job => job.id), [9, 1]);
  assert.deepEqual(jobs, before);
  assert.throws(() => previewJobRoleCandidates({}), TypeError);
});
