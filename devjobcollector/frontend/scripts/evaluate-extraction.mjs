// Pilot regression set based on examples supplied by the user. This is not a representative quality estimate.
import { extractJobRequirements, extractJobSalary, extractMentionedTools, formatJobLocation } from '../src/utils/jobRequirements.js';

const cases = [
  { id: 9413, checks: { required: '객체지향', preferred: 'PostgreSQL', tools: 'PostgreSQL' } },
  { id: 9415, checks: { required: 'Java/Kotlin', tools: 'Kotlin' } },
  { id: 10383, checks: { required: 'Native iOS', preferred: 'E-commerce', tools: 'iOS' } },
  { id: 10981, checks: { required: '2-3 years', preferred: 'Salesforce', salary: '₹700,000', experience: '2~3년', location: 'Hyderabad' } },
  { id: 10988, checks: { required: '10 years', tools: 'CI/CD', salary: '$177,185', experience: '10년 이상', location: 'Remote' } },
  { id: 10999, checks: { required: '3+ years', preferred: 'design systems', tools: 'LLM', salary: '$258,000', experience: '3년 이상', location: 'New York' } },
  { id: 11002, checks: { required: '4+ years', preferred: 'SQL', tools: 'SQL', salary: '$106,000', experience: '4년 이상', location: 'United States' } },
  { id: 11005, checks: { required: 'multiple senior leaders', salary: '$95,200', experience: '확인 필요', location: 'Remote' } },
];

const base = process.env.DJC_EVAL_API || 'https://djc-api.itsdev.kr/api/v1';
const totals = {};
let fetchFailures = 0;
for (const entry of cases) {
  try {
    const response = await fetch(`${base}/jobs/${entry.id}?evaluation=1`, { signal: AbortSignal.timeout(15000) });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const job = await response.json();
    const sections = extractJobRequirements(job.applyQual);
    const values = { required: sections.required.join('\n'), preferred: sections.preferred.join('\n'),
      salary: extractJobSalary(job.applyQual) || '', tools: extractMentionedTools(sections).join(', '),
      experience: job.experience || '', location: formatJobLocation(job.location) };
    for (const [field, expected] of Object.entries(entry.checks)) {
      totals[field] ??= { passed: 0, total: 0 };
      totals[field].total++;
      const matched = values[field].toLowerCase().includes(expected.toLowerCase());
      if (matched) totals[field].passed++;
      else process.stdout.write(`누락: 공고 ${entry.id}, ${field}, 기준 ${expected}\n`);
    }
  } catch (error) {
    fetchFailures++;
    process.stdout.write(`조회 실패: 공고 ${entry.id} (${error.message})\n`);
  }
}
process.stdout.write(`표본: 사용자 제보 공고 ${cases.length}건 (대표 표본 아님), 조회 실패 ${fetchFailures}건\n`);
for (const [field, { passed, total }] of Object.entries(totals)) {
  process.stdout.write(`${field}: 확인된 기준 ${passed}/${total} (${(100 * passed / total).toFixed(1)}%)\n`);
}
if (fetchFailures) process.exitCode = 1;
