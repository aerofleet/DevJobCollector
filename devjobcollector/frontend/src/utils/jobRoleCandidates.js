import { resolveJobCardRoles } from './jobCardRoles.js';

// Read-only preview using exactly the current public card policy. It does not call AI.
export function previewJobRoleCandidates(jobs) {
  if (!Array.isArray(jobs)) throw new TypeError('Expected an array of job posts');
  const seenIds = new Set();
  const seenUrls = new Set();
  const candidates = [];
  const counts = { input: jobs.length, candidates: 0, duplicate: 0, invalid: 0, alreadyHandled: 0 };
  for (const job of jobs) {
    if (!job || !Number.isSafeInteger(job.id) || job.id <= 0 || typeof job.title !== 'string') {
      counts.invalid++;
      continue;
    }
    const url = typeof job.originalUrl === 'string' ? job.originalUrl.trim() : '';
    if (seenIds.has(job.id) || (url && seenUrls.has(url))) {
      counts.duplicate++;
      continue;
    }
    seenIds.add(job.id);
    if (url) seenUrls.add(url);
    if (!resolveJobCardRoles(job).unmatched) {
      counts.alreadyHandled++;
      continue;
    }
    candidates.push({ id: job.id, title: job.title, sourcePlatform: job.sourcePlatform || null,
      originalUrl: url || null });
  }
  counts.candidates = candidates.length;
  return { policy: 'public-card-unmatched', counts, candidates };
}
