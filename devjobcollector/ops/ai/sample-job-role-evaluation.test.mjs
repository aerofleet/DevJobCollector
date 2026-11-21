import { test } from 'node:test';
import assert from 'node:assert/strict';
import { sampleEvaluation } from './sample-job-role-evaluation.mjs';

test('samples each group deterministically and removes duplicate IDs and URLs', () => {
  const jobs = [{ id: 1, title: 'Unknown title', originalUrl: 'https://example.test/1' },
    { id: 2, title: 'Another unknown title', originalUrl: 'https://example.test/2' },
    { id: 3, title: 'Backend Engineer' }, { id: 4, title: 'Frontend Engineer' },
    { id: 5, title: 'Unknown duplicate', originalUrl: 'https://example.test/1' }];
  const result = sampleEvaluation(jobs, 'seed', 1, 1);
  assert.deepEqual(result, sampleEvaluation(jobs, 'seed', 1, 1));
  assert.equal(result.population.duplicates, 1);
  assert.equal(result.unmatched.length, 1);
  assert.equal(result.control.length, 1);
  assert.equal(result.unmatched[0].verdict, null);
  assert.equal(JSON.stringify(result).includes('https://'), false);
  assert.throws(() => sampleEvaluation(jobs, 'seed', 3, 1));
});
