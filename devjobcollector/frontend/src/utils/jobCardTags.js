import { isDeveloperJob } from './developerJob.js';
import { jobCardRoles } from './jobCardRoles.js';

export function jobCardTags(job) {
  const title = typeof job.title === 'string' ? job.title : '';
  const explicitDeveloper = /(?:게임|임베디드|펌웨어)\s*(?:개발|프로그래머)|\b(?:web|game|android|ios|embedded|firmware)\s+(?:software\s+)?(?:developer|engineer)\b/i.test(title);
  if (!isDeveloperJob(job) && !explicitDeveloper) {
    return { label: job.sourcePlatform === 'PUBLIC_ALIO' ? '채용 분야' : '직무', tags: jobCardRoles(job) };
  }

  const registered = Array.isArray(job.techStacks) ? job.techStacks.map(stack => stack?.stackName) : [];
  const mentioned = Array.isArray(job.mentionedTechStacks) ? job.mentionedTechStacks : [];
  const seen = new Set();
  const tags = [...registered, ...mentioned].filter(name => {
    if (typeof name !== 'string' || !name.trim()) return false;
    const key = name.trim().toLowerCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  }).map(name => name.trim());
  return { label: '기술 스택', tags: tags.length ? tags : ['기술 스택 확인 필요'] };
}
