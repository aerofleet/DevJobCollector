import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const script = fileURLToPath(new URL('./audit-job-extraction.mjs', import.meta.url));
const run = (jobs) => spawnSync(process.execPath, [script], { input: jobs.map(job => JSON.stringify(job)).join('\n'), encoding: 'utf8' });

test('reports unknown sections separately from empty bodies and public ALIO', () => {
  const result = run([
    { id: 1, sourcePlatform: 'LEVER', applyQual: '자격 요건은?\n• Java\n우대 사항은?\n• AWS\n주요 업무는?\n• 개발' },
    { id: 2, sourcePlatform: 'LEVER', applyQual: '자격 요건\n• Java' },
    { id: 3, sourcePlatform: 'LEVER', applyQual: null },
    { id: 4, sourcePlatform: 'PUBLIC_ALIO', applyQual: null },
  ]);
  assert.equal(result.status, 0);
  const report = JSON.parse(result.stdout);
  assert.deepEqual([report.jobs, report.bodyEmpty, report.publicAlio, report.requiredUnrecognized, report.preferredUnrecognized, report.dutiesUnrecognized, report.anyUnrecognized], [4, 1, 1, 0, 1, 1, 1]);
});

test('rejects duplicated input instead of emitting misleading totals', () => {
  const result = run([{ id: 1 }, { id: 1 }]);
  assert.notEqual(result.status, 0);
  assert.equal(result.stdout, '');
});
