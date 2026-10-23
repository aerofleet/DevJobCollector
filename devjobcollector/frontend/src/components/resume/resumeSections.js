export const resumeSections = [
  { id: 'basicInfo', label: '기본정보', requirement: '필수' },
  { id: 'photo', label: '사진', optional: true },
  { id: 'introduction', label: '간단소개', optional: true },
  { id: 'careerProfile', label: '개발 직무·지원 방향', requirement: '필수' },
  { id: 'techStack', label: '기술스택', requirement: '필수' },
  { id: 'links', label: '링크', optional: true },
  { id: 'education', label: '학력', requirement: '필수' },
  { id: 'experience', label: '경력', requirement: '택 1' },
  { id: 'projects', label: '프로젝트', requirement: '택 1' },
  { id: 'training', label: '교육이력', optional: true },
  { id: 'activities', label: '기타사항', optional: true },
  { id: 'coverLetter', label: '자기소개서', optional: true },
  { id: 'attachments', label: '첨부파일' },
];

export function safeResumeUrl(value = '') {
  try { const url = new URL(value); return ['https:', 'http:'].includes(url.protocol) ? url.href : ''; }
  catch { return ''; }
}

export function resumeCompletion(data) {
  const category = data.careerProfile?.category;
  return [
    { label: '기본정보', complete: Boolean(data.basicInfo?.name?.trim() && data.basicInfo?.email?.trim()) },
    { label: '개발 직무', complete: Array.isArray(category) ? category.length > 0 : Boolean(category) },
    { label: '기술스택', complete: data.techStack?.some(item => item.name?.trim()) },
    { label: '학력', complete: data.education?.some(item => item.school?.trim()) },
    { label: '경력 또는 프로젝트', complete: data.experience?.some(item => item.company?.trim() && item.description?.trim())
      || data.projects?.some(item => item.title?.trim() && item.description?.trim()) },
  ];
}
