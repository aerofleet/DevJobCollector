// Usage: Get-Content -Raw jobs.json | node ops/ai/preview-job-role-candidates.mjs
// This only evaluates supplied data; it does not fetch jobs or call an AI service.
import { previewJobRoleCandidates } from '../../frontend/src/utils/jobRoleCandidates.js';

try {
  let text = '';
  process.stdin.setEncoding('utf8');
  for await (const chunk of process.stdin) {
    text += chunk;
    if (text.length > 10_000_000) throw new Error('Input exceeds 10 MB');
  }
  const input = JSON.parse(text.replace(/^\uFEFF/, ''));
  const jobs = Array.isArray(input) ? input : input?.content;
  const result = previewJobRoleCandidates(jobs);
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
} catch (error) {
  process.stderr.write(`Candidate preview failed: ${error.message}\n`);
  process.exitCode = 1;
}
