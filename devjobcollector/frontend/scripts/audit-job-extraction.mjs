import { createInterface } from 'node:readline';
import { extractJobDuties, extractJobRequirements } from '../src/utils/jobRequirements.js';

// Input is a private JSONL snapshot on stdin. Output contains aggregate counts only.
const totals = { jobs: 0, bodyEmpty: 0, requiredUnrecognized: 0, preferredUnrecognized: 0, dutiesUnrecognized: 0, anyUnrecognized: 0, publicAlio: 0 };
const bySource = {};
const seen = new Set();
const lines = createInterface({ input: process.stdin, crlfDelay: Infinity });
for await (const line of lines) {
  if (!line.trim()) continue;
  let job;
  try { job = JSON.parse(line); } catch { throw new Error('Invalid JSONL input; no report generated'); }
  if (job.id == null || seen.has(String(job.id))) throw new Error('Missing or duplicate job ID; no report generated');
  seen.add(String(job.id));
  totals.jobs++;
  const source = typeof job.sourcePlatform === 'string' && /^[A-Z_]+$/.test(job.sourcePlatform) ? job.sourcePlatform : 'UNKNOWN';
  const group = bySource[source] ||= { jobs: 0, bodyEmpty: 0, anyUnrecognized: 0 };
  group.jobs++;
  if (job.sourcePlatform === 'PUBLIC_ALIO') { totals.publicAlio++; continue; }
  const body = typeof job.applyQual === 'string' ? job.applyQual.trim() : '';
  if (!body) { totals.bodyEmpty++; group.bodyEmpty++; continue; }
  const requirements = extractJobRequirements(body);
  const requiredMissing = requirements.required.length === 0;
  const preferredMissing = requirements.preferred.length === 0;
  const dutiesMissing = extractJobDuties(body).length === 0;
  totals.requiredUnrecognized += Number(requiredMissing);
  totals.preferredUnrecognized += Number(preferredMissing);
  totals.dutiesUnrecognized += Number(dutiesMissing);
  totals.anyUnrecognized += Number(requiredMissing || preferredMissing || dutiesMissing);
  group.anyUnrecognized += Number(requiredMissing || preferredMissing || dutiesMissing);
}
if (!totals.jobs) throw new Error('Empty snapshot; no report generated');
process.stdout.write(`${JSON.stringify({ scope: 'non-PUBLIC_ALIO jobs with collected body', ...totals, bySource, note: 'Unrecognized is not a confirmed omission; source may omit a section.' }, null, 2)}\n`);
