import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { readFile } from 'node:fs/promises';
import { resolveJobCardRoles } from '../../frontend/src/utils/jobCardRoles.js';

const digest = value => createHash('sha256').update(value).digest('hex');

// Deterministic uniform sample from a single snapshot; URLs are deduplicated, not stored.
export function sampleEvaluation(jobs, seed, unmatchedSize = 200, controlSize = 100) {
  if (!Array.isArray(jobs) || typeof seed !== 'string' || !seed.trim()) throw new Error('Invalid input');
  if (![unmatchedSize, controlSize].every(n => Number.isSafeInteger(n) && n >= 0)) throw new Error('Invalid sizes');
  const ids = new Set(), urls = new Set(), groups = { unmatched: [], control: [] };
  let invalid = 0, duplicates = 0;
  for (const job of jobs) {
    if (!job || !Number.isSafeInteger(job.id) || job.id <= 0 || typeof job.title !== 'string' || !job.title.trim()) {
      invalid++; continue;
    }
    const url = typeof job.originalUrl === 'string' ? job.originalUrl.trim() : '';
    if (ids.has(job.id) || (url && urls.has(url))) { duplicates++; continue; }
    ids.add(job.id); if (url) urls.add(url);
    const group = resolveJobCardRoles(job).unmatched ? 'unmatched' : 'control';
    groups[group].push({ id: job.id, sourcePlatform: job.sourcePlatform || null,
      rank: digest(`${seed}\n${group}\n${job.id}`) });
  }
  if (groups.unmatched.length < unmatchedSize || groups.control.length < controlSize)
    throw new Error('Insufficient candidates');
  const choose = (group, size) => group.sort((a, b) => a.rank.localeCompare(b.rank) || a.id - b.id)
    .slice(0, size).map(({ id, sourcePlatform }) => ({ id, sourcePlatform, verdict: null, expectedRole: null }));
  return { snapshotHash: digest(JSON.stringify(jobs)), seed,
    population: { unmatched: groups.unmatched.length, control: groups.control.length, invalid, duplicates },
    unmatched: choose(groups.unmatched, unmatchedSize), control: choose(groups.control, controlSize) };
}

async function main() {
  const seed = process.env.DJC_ROLE_EVALUATION_SEED;
  let text = '';
  process.stdin.setEncoding('utf8');
  for await (const chunk of process.stdin) {
    text += chunk;
    if (text.length > 10_000_000) throw new Error('Input exceeds 10 MB');
  }
  const input = JSON.parse(text.replace(/^\uFEFF/, ''));
  const rulesHash = digest(await readFile(new URL('../../frontend/src/utils/jobCardRoles.js', import.meta.url)));
  process.stdout.write(`${JSON.stringify({ rulesHash, ...sampleEvaluation(Array.isArray(input) ? input : input?.content, seed) }, null, 2)}\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href)
  main().catch(() => { process.stderr.write('Could not prepare evaluation sample. Check the input, seed and sample counts.\n'); process.exitCode = 1; });
