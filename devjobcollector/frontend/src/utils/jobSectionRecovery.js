// Only explicit standalone titles are eligible for automatic recovery.
export function recoverSectionHeading(value) {
  const title = String(value || '').trim().replace(/^(?:#{1,6}\s*)/, '').replace(/^\[|\]$/g, '').trim();
  if (/^(?:지원\s*자격|자격\s*(?:요건|조건)|필수\s*(?:요건|사항|조건))\s*(?:은|이|이란|에는)?\s*[:：?？]?$/.test(title)) return 'required';
  if (/^(?:우대\s*(?:사항|요건|조건))\s*(?:은|이|이란|에는)?\s*[:：?？]?$/.test(title)) return 'preferred';
  if (/^(?:주요\s*업무|담당\s*업무|업무\s*내용)\s*(?:는|란|은)?\s*[:：?？]?$/.test(title)) return 'duties';
  if (/^role requirements\s*[:：]?$/i.test(title)) return 'required';
  if (/^role responsibilities\s*[:：]?$/i.test(title)) return 'duties';
  return null;
}
