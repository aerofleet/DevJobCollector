const HEADINGS = /^(?:#{1,6}\s*)?[[【]?(자격\s*(?:요건|조건)|지원\s*(?:자격|조건)|필수\s*(?:요건|사항|조건)|우대\s*(?:사항|요건|조건)|이런\s*분과\s*함께하고\s*싶어요|이런\s*경험이\s*있다면\s*더\s*좋아요|preferred (?:qualifications|requirements|skills)|basic qualifications|required skills(?:\s+and\s+experience)?|minimum requirements|nice to haves?|bonus points|while not required,?\s*it['’]s an added plus if you also have|we['’]d love to hear from you if you have|we(?:['’]re| are) looking for someone who|your expertise|what you(?:['’]ll| will)?\s+(?:bring|need)|what we(?:['’]re| are) looking for|what we look for in you(?:\s*\(i\.?e\.?\s*,?\s*job requirements\))?|qualifications(?:\s*(?:&|and)\s*experience\s+required)?|requirements)[\]】]?[\s:：-]*$/i;
// Some sources serialize a heading and bullet list into a single line.
function separateInlineBullets(text) {
  return text.replace(/\ufeff/g, '').replace(/^(\s*(?:what you(?:['’]ll| will) do|required skills|preferred skills|채용하고 싶은 사람|전형절차 및 기타사항|유의사항))\s+(?=·\s)/gim, '$1\n')
    .replace(/[ \t]+·(?=\s)/g, '\n·');
}
const OTHER_HEADING = /^(?:#{1,6}\s+.+|[[【].+[\]】]|(?:주요\s*업무|담당\s*업무|업무\s*내용|조직\s*소개|근무\s*조건|복리\s*후생|전형\s*절차|채용\s*절차|접수\s*방법|기타\s*사항|responsibilities|benefits|success measures|about the (?:role|team)|hiring process)\s*[:：]?)$/i;
const LABELED_REQUIREMENT_HEADING = /^[^\n•]{0,100}\((필수\s*(?:요건|사항|조건)|우대\s*(?:요건|사항|조건))\)\s*[:：]?$/;
const KRAFTON_PROCESS_HEADING = /^.{0,80}전형\s*과정이\s*필요합니다[.!]?$/;

// Recognize explicit sections only; unknown formats must not be presented as requirements.
export function extractJobRequirements(content) {
  const result = { required: [], preferred: [] };
  const decode = (value) => value.replace(/&amp;/gi, '&').replace(/&lt;/gi, '<').replace(/&gt;/gi, '>')
    .replace(/&nbsp;|&#160;/gi, ' ').replace(/&mdash;/gi, '—').replace(/&ndash;/gi, '–').replace(/&quot;/gi, '"').replace(/&#39;|&apos;/gi, "'");
  const text = separateInlineBullets(decode(decode(String(content || '')))).replace(/\r\n?/g, '\n')
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
    const heading = line.match(HEADINGS) || line.match(LABELED_REQUIREMENT_HEADING) || line.match(/^(how do i know if the role is right for me)\?\s*$/i);
    if (heading) {
      section = /우대|이런\s*경험이\s*있다면\s*더\s*좋아요|preferred|nice to have|added plus|bonus points/i.test(heading[1]) ? 'preferred' : 'required';
      continue;
    }
    if (OTHER_HEADING.test(line) || KRAFTON_PROCESS_HEADING.test(line) || /^(?:채용하고 싶은 사람|전형절차 및 기타사항|유의사항|전형\s*절차\s*및\s*안내\s*사항)\s*[:：]?$/i.test(line) || /^(?:#\s*[A-Z]?\d+|PID(?:\s*-\s*[A-Z]?\d+)?$)/i.test(line) || /^(?:position details|pay and benefits|compensation\s*&\s*benefits|pay range transparency|salary\s*[:：]|compensation\s*[:：]|the base salary range\b|at figma, one of our values|pay transparency|annual base salary|application limit|equal opportunity employer|accommodations|data privacy)/i.test(line) || /^[^:：]{1,80}[:：]$/.test(line)) { section = null; continue; }
    if (!section) continue;
    const item = line.replace(/^[-*•●▪◦‣·]\s*/, '').replace(/^\d+[.)]\s*/, '').trim();
    if (!item || /^https?:\/\//i.test(item)) continue;
    if (section === 'required' && /\bstrongly preferred\.?$/i.test(item) && !/\bnot\s+strongly preferred\b/i.test(item)) {
      const parts = item.split(';');
      if (parts.length === 2 && !/\bpreferred\b/i.test(parts[0])) {
        result.required.push(parts[0].trim());
        result.preferred.push(parts[1].trim());
      } else if (parts.length === 1) result.preferred.push(item);
      else result.required.push(item);
    } else result[section].push(item);
  }
  return result;
}

// Present the explicit duration without displaying an entire qualification sentence.
export function extractExperienceWording(requirements) {
  // An explicitly total engineering tenure may include a separate management minimum.
  const totalEngineering = requirements.required.flatMap(item => {
    const match = item.match(/^(\d{1,2})\+\s+total years? of engineering experience with (?:a )?(?:minimum|minimal|at least)\s+\d{1,2}\s+years? of management experience\.?$/i);
    return match ? [match[1]] : [];
  });
  const candidates = requirements.required.filter(item =>
    (/(?:\d{1,2}\s*년\s*이상[^.!?\n]{0,40}(?:경험|경력)|(?:경험|경력)[^.!?\n]{0,40}\d{1,2}\s*년\s*이상)/.test(item)
      || /\b\d{1,2}\+\s*years?\b[^.!?\n]{0,80}\bexperience\b/i.test(item)
      || /\b(?:minimum(?: of)?|at least)\s+\d{1,2}\s+years?\b[^.!?\n]{0,80}\bexperience\b/i.test(item)
      || /\b\d{1,2}\+\s+years?\s+in\s+(?!school\b|college\b|university\b|education\b)[a-z]/i.test(item))
      && !/우대|선호|prefer|(?:학위|석사|박사)[^.!?\n]{0,80}(?:또는|혹은)[^.!?\n]{0,80}\d{1,2}\s*년/i.test(item));
  if (candidates.length === 0 && totalEngineering.length === 1) return `${totalEngineering[0]}년 이상`;
  if (totalEngineering.length > 0) return null;
  if (candidates.length === 0 && requirements.required.some(item => /경력\s*구분\s*[:：]\s*무관/.test(item))) return '경력무관';
  if (candidates.length !== 1) return null;
  const durations = [...candidates[0].matchAll(/(\d{1,2})\s*년\s*이상/g)];
  const english = [...candidates[0].matchAll(/\b(\d{1,2})\+\s*years?\b/gi)];
  english.push(...candidates[0].matchAll(/\b(?:minimum(?: of)?|at least)\s+(\d{1,2})\s+years?\b(?=[^.!?\n]{0,80}\bexperience\b)/gi));
  if (durations.length + english.length !== 1) return null;
  return `${(durations[0] || english[0])[1]}년 이상`;
}

// Preserve explicitly labelled source technology sections as written, without inferring individual tags.
export function extractJobTechnologySections(content) {
  const lines = String(content || '').replace(/\r\n?/g, '\n').split('\n');
  const items = [];
  let inside = false;
  for (const raw of lines) {
    const line = raw.trim();
    if (/^#{1,6}\s*(?:토스가\s*사용하는\s*기술|기술\s*스택|사용\s*기술|tech(?:nology)?\s*stack)\s*[:：]?$/i.test(line)) { inside = true; continue; }
    if (/^#{1,6}\s+/.test(line)) { inside = false; continue; }
    if (inside && line) items.push(line.replace(/^[-*•●▪◦‣·]\s*/, '').trim());
  }
  return items;
}

export function extractJobDuties(content) {
  const lines = separateInlineBullets(String(content || '')).replace(/&nbsp;|&#160;/gi, ' ').replace(/&amp;/gi, '&')
    .replace(/<br\s*\/?>/gi, '\n').replace(/<li(?:\s[^>]*)?>/gi, '\n• ')
    .replace(/<\/(?:p|div|li|h[1-6])>/gi, '\n').replace(/<[^>]*>/g, '')
    .replace(/\r\n?/g, '\n').replace(/\s*◆\s*/g, '\n').replace(/[ \t]+-[ \t]+/g, '\n- ').split('\n');
  const heading = /^(?:#{1,6}\s*)?[[【]?(?:주요\s*업무|담당\s*업무|업무\s*내용|합류하면\s*함께\s*할\s*업무예요|합류하면\s*함께할\s*업무예요|responsibilities|what you(?:['’]ll| will) do)[\]】]?[\s:：-]*$/i;
  const items = [];
  let inside = false;
  for (const [index, raw] of lines.entries()) {
    const line = raw.trim();
    if (!line) continue;
    if (heading.test(line) || /^우리\s*팀과\s*함께할\s*미션을\s*소개합니다[.!]?$/i.test(line) || (/^the opportunity\s*:?$/i.test(line) && lines.slice(index + 1).some(next => /^how do i know if the role is right for me\?\s*$/i.test(next.trim())))) { inside = true; continue; }
    if (/^how do i know if the role is right for me\?\s*$/i.test(line)) { inside = false; continue; }
    if (HEADINGS.test(line) || LABELED_REQUIREMENT_HEADING.test(line) || OTHER_HEADING.test(line) || KRAFTON_PROCESS_HEADING.test(line) || /^#{1,6}\s+/.test(line)
      || /^(?:전형\s*절차|토스로의\s*합류\s*여정|PID\s*-\s*[A-Z]?\d+|pay transparency|annual base salary|salary\s*[:：]|compensation\s*[:：])/i.test(line)
      || /^[^:：]{1,80}[:：]$/.test(line)) { inside = false; continue; }
    if (inside) items.push(line.replace(/^[-*•●▪◦‣·]\s*/, '').replace(/^\d+[.)]\s*/, '').trim());
  }
  return items;
}

// Only return an amount when the source explicitly labels it as salary or pay.
export function extractJobSalary(content) {
  const text = String(content || '').replace(/&amp;/gi, '&').replace(/&mdash;/gi, '—')
    .replace(/&ndash;/gi, '–').replace(/&nbsp;|&#160;/gi, ' ')
    .replace(/<br\s*\/?\s*>|<\/(?:p|div|li|h[1-6])>/gi, '\n').replace(/<[^>]*>/g, '');
  const lines = text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  const amount = /(?:[$€£₹₩₱]\s*\d[\d,]*(?:\.\d+)?|\b(?:USD|AUD|CAD|INR|KRW|EUR|GBP|PHP)\s*(?:(?:from|up to)\s+)?\d[\d,]*(?:\.\d+)?|\d[\d,]*(?:\.\d+)?\s*(?:(?:만원|억원|원)|(?:USD|AUD|CAD|INR|KRW|EUR|GBP|PHP)\b))/i;
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
  const tools = ['Google apps', 'JIRA', 'Salesforce Service Cloud', 'Amazon Connect', 'LibreChat', 'Gemini', 'Glean', 'Java', 'Kotlin', 'JavaScript', 'TypeScript', 'Python', 'C++', 'ROS2', 'PyTorch', 'JAX', 'LeRobot', 'MuJoCo', 'Isaac Sim', 'React', 'Spring', 'iOS', 'SQL', 'Excel', 'SAP', 'RDBMS', 'NoSQL', 'PostgreSQL', 'MongoDB', 'Docker', 'Kubernetes', 'AWS', 'Azure', 'GCP', 'IaC', 'CI/CD'];
  const matches = tools.filter((name) => new RegExp(`(?<![\\w+])${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?![\\w+])`, 'i').test(text));
  if (/\b(?:large language models?|LLMs?)\b/i.test(text)) matches.push('LLM');
  return matches;
}

export function formatJobLocation(location) {
  const parts = String(location || '').split(/[;•]/).map((part) => part.trim()).filter(Boolean);
  return parts.filter((part, index) => parts.findIndex((candidate) => candidate.toLocaleLowerCase() === part.toLocaleLowerCase()) === index).join(' · ');
}

// Omit parenthetical details only from compact summary values, retaining source content elsewhere.
export function formatInfoGridValue(value) {
  let result = String(value ?? '');
  let previous;
  do {
    previous = result;
    result = result.replace(/\([^()]*\)|（[^（）]*）/g, '');
  } while (result !== previous);
  return result.replace(/\s+/g, ' ').trim();
}

// Use explicitly labelled source text when the structured field was not collected.
export function extractJobEmploymentType(content) {
  const match = String(content || '').match(/(?:^|\n)\s*고용\s*형태\s*[:：]?\s*\n\s*[-*•●▪◦‣·]?\s*([^\n]{1,100})/i);
  return match ? match[1].trim() : null;
}

export function extractRollingDeadline(content) {
  return /상시\s*채용으로\s*채용\s*완료\s*시\s*조기\s*마감될\s*수\s*있습니다/.test(String(content || ''))
    ? '상시 채용 (채용 완료 시 조기 마감 가능)' : null;
}
