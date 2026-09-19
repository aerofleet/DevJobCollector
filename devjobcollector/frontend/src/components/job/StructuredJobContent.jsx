import React from 'react';

const SECTION_TITLES = [
  '주요 업무', '주요업무', '담당 업무', '담당업무', '업무 내용', '업무내용',
  '자격 요건', '자격요건', '지원 자격', '지원자격', '우대 사항', '우대사항',
  '필수 요건', '필수요건', '근무 조건', '근무조건', '복리 후생', '복리후생',
  '채용 절차', '채용절차', '전형 절차', '전형절차', '접수 방법', '접수방법',
  '기타 사항', '기타사항',
  'About the role', 'About the team', 'What you will do', "What you'll do",
  'Responsibilities', 'Requirements', 'Qualifications', 'Preferred qualifications',
  'Benefits', 'Hiring process', 'How we hire',
];

const SECTION_PATTERN = SECTION_TITLES.join('|');
const HEADING_PATTERN = new RegExp(`^(?:#{1,6}\\s*)?(?:\\[)?(${SECTION_PATTERN})(?:\\])?(?:\\s*[:：-]\\s*(.*))?$`, 'i');
const INLINE_HEADING_PATTERN = new RegExp(`[ \\t]+(${SECTION_PATTERN})[ \\t]*[:：][ \\t]*`, 'gi');
const MARKDOWN_HEADING_PATTERN = /^#{1,6}\s+(.+)$/;
const GENERIC_HEADING_PATTERN = /^([^:：/]{1,30})[:：]$/;
const BULLET_PATTERN = /^[-*•●▪◦‣·]\s*(.+)$/;
const ORDERED_PATTERN = /^(\d+)[.)]\s*(.+)$/;
const CALLOUT_PATTERN = /^(?:※|\*\s*주의)\s*(.+)$/;
const URL_PATTERN = /(https?:\/\/[^\s<]+)/gi;

const decodeCodePoint = (code, radix = 10) => {
  const codePoint = Number.parseInt(code, radix);
  return Number.isInteger(codePoint) && codePoint <= 0x10ffff
    ? String.fromCodePoint(codePoint)
    : '';
};

const decodeEntities = (value) => value
  .replace(/&nbsp;/gi, ' ')
  .replace(/&amp;/gi, '&')
  .replace(/&lt;/gi, '<')
  .replace(/&gt;/gi, '>')
  .replace(/&quot;/gi, '"')
  .replace(/&#39;|&apos;/gi, "'")
  .replace(/&#(\d+);/g, (_, code) => decodeCodePoint(code))
  .replace(/&#x([0-9a-f]+);/gi, (_, code) => decodeCodePoint(code, 16));

const normalizeContent = (content) => decodeEntities(String(content ?? ''))
  .replace(/\r\n?/g, '\n')
  .replace(/<br\s*\/?>/gi, '\n')
  .replace(/<li(?:\s[^>]*)?>/gi, '\n• ')
  .replace(/<\/(?:p|div|li|h[1-6])>/gi, '\n')
  .replace(/<[^>]*>/g, '')
  .replace(INLINE_HEADING_PATTERN, '\n$1: ')
  .replace(/[\t\f\v]+/g, ' ')
  .replace(/ *\n */g, '\n')
  .replace(/\n{3,}/g, '\n\n')
  .trim();

const createBlock = (type, value, index) => ({ type, value, key: `${type}-${index}` });
const formatHeading = (value) => /[a-z]/i.test(value) ? value.trim() : value.replace(/\s+/g, '');

const splitLongParagraph = (value, maxLength = 320) => {
  if (value.length <= maxLength) return [value];
  const sentences = value.match(/[^.!?。！？]+[.!?。！？]+["'”’)]*|[^.!?。！？]+$/g) ?? [value];
  const chunks = [];
  let chunk = '';

  sentences.map((sentence) => sentence.trim()).filter(Boolean).forEach((sentence) => {
    if (chunk && chunk.length + sentence.length + 1 > maxLength) {
      chunks.push(chunk);
      chunk = sentence;
      return;
    }
    chunk = chunk ? `${chunk} ${sentence}` : sentence;
  });
  if (chunk) chunks.push(chunk);
  return chunks;
};

const parseJobContent = (content) => {
  const normalized = normalizeContent(content);
  if (!normalized) return [];

  const blocks = [];
  let paragraph = [];
  let list = null;

  const flushParagraph = () => {
    if (!paragraph.length) return;
    splitLongParagraph(paragraph.join(' ')).forEach((value) => {
      blocks.push(createBlock('paragraph', value, blocks.length));
    });
    paragraph = [];
  };
  const flushList = () => {
    if (!list) return;
    blocks.push(createBlock(list.type, list.items, blocks.length));
    list = null;
  };

  normalized.split('\n').forEach((rawLine) => {
    const line = rawLine.trim();
    if (!line) {
      flushParagraph();
      flushList();
      return;
    }

    const heading = line.match(HEADING_PATTERN);
    const markdownHeading = line.match(MARKDOWN_HEADING_PATTERN);
    const genericHeading = line.match(GENERIC_HEADING_PATTERN);
    if (heading) {
      flushParagraph();
      flushList();
      blocks.push(createBlock('heading', formatHeading(heading[1]), blocks.length));
      if (heading[2]) paragraph.push(heading[2]);
      return;
    }
    if (markdownHeading || genericHeading) {
      flushParagraph();
      flushList();
      blocks.push(createBlock('heading', (markdownHeading ?? genericHeading)[1].trim(), blocks.length));
      return;
    }

    const ordered = line.match(ORDERED_PATTERN);
    const bullet = line.match(BULLET_PATTERN);
    if (ordered || bullet) {
      flushParagraph();
      const type = ordered ? 'ordered-list' : 'unordered-list';
      const item = ordered ? ordered[2] : bullet[1];
      if (list?.type !== type) flushList();
      list ??= { type, items: [] };
      list.items.push(item);
      return;
    }

    const callout = line.match(CALLOUT_PATTERN);
    if (callout) {
      flushParagraph();
      flushList();
      blocks.push(createBlock('callout', callout[1], blocks.length));
      return;
    }

    flushList();
    paragraph.push(line);
  });

  flushParagraph();
  flushList();
  return blocks;
};

const RichText = ({ children }) => String(children).split(URL_PATTERN).map((part, index) => {
  if (!/^https?:\/\//i.test(part)) return <React.Fragment key={`${part}-${index}`}>{part}</React.Fragment>;
  const trailingPunctuation = part.match(/[),.;!?]+$/)?.[0] ?? '';
  const href = trailingPunctuation ? part.slice(0, -trailingPunctuation.length) : part;
  return (
    <React.Fragment key={`${part}-${index}`}>
      <a href={href} target="_blank" rel="noopener noreferrer">{href}</a>
      {trailingPunctuation}
    </React.Fragment>
  );
});

const StructuredJobContent = ({ content, emptyMessage = '등록된 상세 내용이 없습니다.' }) => {
  const blocks = parseJobContent(content);
  if (!blocks.length) return <p className="job-content-empty">{emptyMessage}</p>;

  return (
    <div className="structured-job-content">
      {blocks.map((block) => {
        if (block.type === 'heading') return <h4 key={block.key}>{block.value}</h4>;
        if (block.type === 'callout') return <aside key={block.key}><RichText>{block.value}</RichText></aside>;
        if (block.type === 'ordered-list') {
          return <ol key={block.key}>{block.value.map((item, index) => <li key={`${item}-${index}`}><RichText>{item}</RichText></li>)}</ol>;
        }
        if (block.type === 'unordered-list') {
          return <ul key={block.key}>{block.value.map((item, index) => <li key={`${item}-${index}`}><RichText>{item}</RichText></li>)}</ul>;
        }
        return <p key={block.key}><RichText>{block.value}</RichText></p>;
      })}
    </div>
  );
};

export default StructuredJobContent;
