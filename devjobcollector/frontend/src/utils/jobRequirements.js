const HEADINGS = /^(?:#{1,6}\s*)?[[【]?(자격\s*(?:요건|조건)|지원\s*자격|필수\s*요건|우대\s*사항|우대\s*요건|preferred qualifications|preferred requirements|minimum requirements|nice to haves?|bonus points|while not required,?\s*it['’]s an added plus if you also have|we['’]d love to hear from you if you have|your expertise|what you(?:['’]ll| will)?\s+(?:bring|need)|what we(?:['’]re| are) looking for|what we look for in you(?:\s*\(i\.?e\.?\s*,?\s*job requirements\))?|qualifications|requirements)[\]】]?[\s:：-]*$/i;
const OTHER_HEADING = /^(?:#{1,6}\s+.+|[[【].+[\]】]|(?:주요\s*업무|담당\s*업무|업무\s*내용|조직\s*소개|근무\s*조건|복리\s*후생|전형\s*절차|채용\s*절차|접수\s*방법|기타\s*사항|responsibilities|benefits|about the (?:role|team)|hiring process)\s*[:：]?)$/i;

// Recognize explicit sections only; unknown formats must not be presented as requirements.
export function extractJobRequirements(content) {
  const result = { required: [], preferred: [] };
  const decode = (value) => value.replace(/&amp;/gi, '&').replace(/&lt;/gi, '<').replace(/&gt;/gi, '>')
    .replace(/&nbsp;|&#160;/gi, ' ').replace(/&mdash;/gi, '—').replace(/&ndash;/gi, '–').replace(/&quot;/gi, '"').replace(/&#39;|&apos;/gi, "'");
  const text = decode(decode(String(content || ''))).replace(/\r\n?/g, '\n')
    .replace(/<br\s*\/?>/gi, '\n').replace(/<li(?:\s[^>]*)?>/gi, '\n• ')
    .replace(/<\/(?:p|div|li|h[1-6])>/gi, '\n').replace(/<[^>]*>/g, '')
    .replace(/\u200b/g, '').replace(/\s*◆\s*/g, '\n').replace(/[ \t]+-[ \t]+/g, '\n- ');
  let section = null;
  for (const raw of text.split('\n')) {
    const line = raw.trim();
    if (!line) continue;
    const preferredItem = line.match(/^(?:[-*•●▪◦‣·]\s*)?preferred\s*[:：]\s*(.+)$/i);
    if (section && preferredItem) {
      result.preferred.push(preferredItem[1].trim());
      continue;
    }
    const heading = line.match(HEADINGS);
    if (heading) {
      section = /우대|preferred|nice to have|added plus|bonus points/i.test(heading[1]) ? 'preferred' : 'required';
      continue;
    }
    if (OTHER_HEADING.test(line) || /^#\s*[A-Z]?\d+\b/i.test(line) || /^(?:position details|pay and benefits|at figma, one of our values|pay transparency|annual base salary|application limit|equal opportunity employer|accommodations|data privacy)\b/i.test(line) || /^[^:：]{1,80}[:：]$/.test(line)) { section = null; continue; }
    if (!section) continue;
    const item = line.replace(/^[-*•●▪◦‣·]\s*/, '').replace(/^\d+[.)]\s*/, '').trim();
    if (item && !/^https?:\/\//i.test(item)) result[section].push(item);
  }
  return result;
}

// Only return an amount when the source explicitly labels it as salary or pay.
export function extractJobSalary(content) {
  const text = String(content || '').replace(/&amp;/gi, '&').replace(/&mdash;/gi, '—')
    .replace(/&ndash;/gi, '–').replace(/&nbsp;|&#160;/gi, ' ')
    .replace(/<br\s*\/?\s*>|<\/(?:p|div|li|h[1-6])>/gi, '\n').replace(/<[^>]*>/g, '');
  const lines = text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  const amount = /(?:[$€£₹₩]\s*[\d,]+(?:\.\d+)?|\b(?:USD|INR|KRW|EUR|GBP)\s*[\d,]+|[\d,]+\s*(?:원|만원|억원|USD|INR|KRW|EUR|GBP))/i;
  for (let i = 0; i < lines.length; i++) {
    if (!/(?:salary|compensation|pay\s+range|급여|연봉|월급|시급)/i.test(lines[i])) continue;
    for (let j = i; j < Math.min(i + 5, lines.length); j++) {
      if (j > i && /^(?:application limit|equal opportunity|our commitment|우대|자격)/i.test(lines[j])) break;
      if (amount.test(lines[j])) {
        const label = j > i && /(?:salary|compensation|pay\s+range|급여|연봉|월급|시급)/i.test(lines[j - 1]) && lines[j - 1].length <= 100 ? lines[j - 1] : lines[i].length <= 100 ? lines[i] : /base salary/i.test(lines[i]) ? '기본급' : '급여';
        const scope = j > i && /^(?:US based applicants only)$/i.test(lines[j - 1]) ? lines[j - 1] : null;
        return [label, scope, lines[j]].filter((line, k, arr) => line && arr.indexOf(line) === k).join(' ');
      }
    }
  }
  return null;
}

// Show only recognizable tool names explicitly mentioned in qualification sections.
export function extractMentionedTools(requirements) {
  const text = [...requirements.required, ...requirements.preferred].join(' ');
  const tools = ['Google apps', 'JIRA', 'Salesforce Service Cloud', 'Amazon Connect', 'LibreChat', 'Gemini', 'Glean', 'Java', 'Kotlin', 'JavaScript', 'TypeScript', 'Python', 'React', 'Spring', 'iOS', 'SQL', 'Excel', 'RDBMS', 'NoSQL', 'PostgreSQL', 'MongoDB', 'Docker', 'Kubernetes', 'AWS', 'IaC', 'CI/CD'];
  const matches = tools.filter((name) => new RegExp(`\\b${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'i').test(text));
  if (/\b(?:large language models?|LLMs?)\b/i.test(text)) matches.push('LLM');
  return matches;
}

export function formatJobLocation(location) {
  const parts = String(location || '').split(/[;•]/).map((part) => part.trim()).filter(Boolean);
  return parts.filter((part, index) => parts.findIndex((candidate) => candidate.toLocaleLowerCase() === part.toLocaleLowerCase()) === index).join(' · ');
}
