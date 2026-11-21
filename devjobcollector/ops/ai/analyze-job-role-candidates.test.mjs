import { test } from 'node:test';
import assert from 'node:assert/strict';
import { analyzeCandidates } from './analyze-job-role-candidates.mjs';

test('only unmatched, distinct public-card candidates reach the processor', async () => {
  const calls = [];
  const jobs = [{ id: 1, title: 'Customer Engineering Intern' }, { id: 1, title: 'Customer Engineering Intern' },
    { id: 2, title: 'Backend Engineer' }, { id: 3, title: 'Unknown specialist', approvedJobRole: '재무·자금' }];
  const result = await analyzeCandidates(jobs, async id => { calls.push(id); return 'STORED'; });
  assert.deepEqual(calls, [1]);
  assert.deepEqual(result.outcomes, { STORED: 1 });
});

test('daily limit stops the batch and a later run can retry remaining candidates', async () => {
  const jobs = [{ id: 1, title: 'Unknown one' }, { id: 2, title: 'Unknown two' }];
  const result = await analyzeCandidates(jobs, async () => 'DAILY_LIMIT');
  assert.equal(result.attempted, 1);
  assert.equal(result.remaining, 2);
  assert.equal(result.nextOffset, 0);
  await assert.rejects(() => analyzeCandidates(jobs, async () => 'STORED', 21));
});

test('offset resumes within the same snapshot', async () => {
  const jobs = [{ id: 1, title: 'Unknown one' }, { id: 2, title: 'Unknown two' }];
  const calls = [];
  const result = await analyzeCandidates(jobs, async id => { calls.push(id); return 'STORED'; }, 1, 1);
  assert.deepEqual(calls, [2]);
  assert.equal(result.nextOffset, 2);
  assert.equal(result.remaining, 0);
});

test('already claimed entries do not consume the proposal batch limit', async () => {
  const jobs = [{ id: 1, title: 'Unknown one' }, { id: 2, title: 'Unknown two' }];
  const calls = [];
  const result = await analyzeCandidates(jobs, async id => {
    calls.push(id);
    return id === 1 ? 'ALREADY_CLAIMED' : 'STORED';
  }, 1);
  assert.deepEqual(calls, [1, 2]);
  assert.equal(result.outcomes.STORED, 1);
});
