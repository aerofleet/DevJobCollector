const HEADINGS = /^(?:#{1,6}\s*)?[[【]?(자격\s*요건|지원\s*자격|필수\s*요건|우대\s*사항|우대\s*요건|preferred qualifications|preferred requirements|qualifications|requirements)[\]】]?[\s:：-]*$/i;
const OTHER_HEADING = /^(?:#{1,6}\s+.+|[[【].+[\]】]|(?:주요\s*업무|담당\s*업무|근무\s*조건|복리\s*후생|전형\s*절차|채용\s*절차|기타\s*사항|responsibilities|benefits|about the role|hiring process)\s*[:：]?)$/i;

// Recognize explicit sections only; unknown formats must not be presented as requirements.
export function extractJobRequirements(content) {
  const result = { required: [], preferred: [] };
  const text = String(content || '').replace(/\r\n?/g, '\n')
    .replace(/<br\s*\/?>/gi, '\n').replace(/<li(?:\s[^>]*)?>/gi, '\n• ')
    .replace(/<\/(?:p|div|li|h[1-6])>/gi, '\n').replace(/<[^>]*>/g, '');
  let section = null;
  for (const raw of text.split('\n')) {
    const line = raw.trim();
    if (!line) continue;
    const heading = line.match(HEADINGS);
    if (heading) {
      section = /우대|preferred/i.test(heading[1]) ? 'preferred' : 'required';
      continue;
    }
    if (OTHER_HEADING.test(line) || /^[^:：]{1,30}[:：]$/.test(line)) { section = null; continue; }
    if (!section || result[section].length >= 3) continue;
    const item = line.replace(/^[-*•●▪◦‣·]\s*/, '').replace(/^\d+[.)]\s*/, '').trim();
    if (item && item.length <= 180 && !/^https?:\/\//i.test(item)) result[section].push(item);
  }
  return result;
}
