// Pipe a public job snapshot to this command. It creates review proposals only.
import { pathToFileURL } from 'node:url';
import { createHash } from 'node:crypto';
import { previewJobRoleCandidates } from '../../frontend/src/utils/jobRoleCandidates.js';

export async function analyzeCandidates(jobs, analyze, limit = 20, offset = 0) {
  if (!Number.isInteger(limit) || limit < 1 || limit > 20) throw new Error('Invalid batch limit');
  if (!Number.isSafeInteger(offset) || offset < 0) throw new Error('Invalid candidate offset');
  const { candidates, counts } = previewJobRoleCandidates(jobs);
  const outcomes = {};
  let attempted = 0;
  let retryLast = false;
  for (const { id } of candidates.slice(offset)) {
    // Previously processed or ineligible entries must not starve later candidates.
    if ((outcomes.STORED || 0) >= limit || attempted >= 1000) break;
    const status = await analyze(id);
    attempted++;
    outcomes[status] = (outcomes[status] || 0) + 1;
    if (status === 'DAILY_LIMIT' || status === 'DISABLED') { retryLast = true; break; }
  }
  const nextOffset = Math.min(offset + attempted - Number(retryLast), candidates.length);
  return { counts, attempted, outcomes, nextOffset, remaining: candidates.length - nextOffset };
}

async function main() {
  const base = new URL(process.env.DJC_ADMIN_API_BASE || '');
  if (base.protocol !== 'https:' || base.username || base.password || base.search || base.hash
      || base.pathname.replace(/\/$/, '') !== '/api/v1/admin') throw new Error('Invalid HTTPS admin API base');
  const cookie = process.env.DJC_ADMIN_COOKIE;
  const csrf = process.env.DJC_ADMIN_CSRF;
  if (!cookie || !csrf || /[\r\n]/.test(cookie + csrf)) throw new Error('Admin session and CSRF are required');
  let text = '';
  process.stdin.setEncoding('utf8');
  for await (const chunk of process.stdin) {
    text += chunk;
    if (text.length > 10_000_000) throw new Error('Input exceeds 10 MB');
  }
  const input = JSON.parse(text.replace(/^\uFEFF/, ''));
  const jobs = Array.isArray(input) ? input : input?.content;
  if (!Array.isArray(jobs)) throw new Error('Expected a job snapshot');
  const snapshotHash = createHash('sha256').update(JSON.stringify(jobs)).digest('hex');
  const offset = Number(process.env.DJC_ROLE_CANDIDATE_OFFSET || 0);
  if (offset > 0 && process.env.DJC_ROLE_SNAPSHOT_HASH !== snapshotHash) throw new Error('Snapshot changed');
  const result = await analyzeCandidates(jobs, async id => {
    const url = new URL(`${base.pathname.replace(/\/$/, '')}/jobs/${id}/ai-role/analyze`, base.origin);
    const response = await fetch(url, { method: 'POST', redirect: 'error',
      headers: { Accept: 'application/json', Cookie: cookie, 'X-CSRF-TOKEN': csrf }, signal: AbortSignal.timeout(60_000) });
    if (!response.ok) throw new Error(`Admin API returned HTTP ${response.status}`);
    const body = await response.json();
    if (!['DISABLED', 'NOT_ELIGIBLE', 'SOURCE_NOT_ALLOWED', 'INPUT_UNUSABLE', 'INPUT_TOO_LARGE', 'JOB_CHANGED', 'ALREADY_CLAIMED', 'DAILY_LIMIT', 'STORED'].includes(body.status))
      throw new Error('Unexpected admin API response');
    return body.status;
  }, 20, offset);
  process.stdout.write(`${JSON.stringify({ snapshotHash, ...result }, null, 2)}\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(() => { process.stderr.write('Candidate analysis failed; check the admin session, API settings and input.\n'); process.exitCode = 1; });
}
