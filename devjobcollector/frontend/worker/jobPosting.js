const regions = new Set(['서울', '서울특별시', '부산', '부산광역시', '대구', '대구광역시', '인천', '인천광역시', '광주', '광주광역시', '대전', '대전광역시', '울산', '울산광역시', '세종', '세종특별자치시', '경기', '경기도', '강원', '강원특별자치도', '충북', '충청북도', '충남', '충청남도', '전북', '전북특별자치도', '전남', '전라남도', '경북', '경상북도', '경남', '경상남도', '제주', '제주특별자치도']);
const htmlEscape = (value) => value.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
function date(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

export function jobPosting(job, now = new Date()) {
  // DJC approval date is not necessarily the employer's original posting date.
  if (!job || job.sourcePlatform !== 'COMPANY_DIRECT' || job.active === false || job.isActive === false
      || (job.active !== true && job.isActive !== true)) return null;
  if (job.originalPostedDateVerified !== true || job.completeDescriptionVerified !== true) return null;
  const title = typeof job.title === 'string' ? job.title.trim() : '';
  const company = typeof job.companyName === 'string' ? job.companyName.trim() : '';
  const description = typeof job.applyQual === 'string' ? job.applyQual.trim() : '';
  const region = typeof job.location === 'string' ? job.location.trim() : '';
  if (!title || !company || !description || !regions.has(region) || !date(job.originalPostedDate) || !date(job.endDate)) return null;
  if (job.originalPostedDate > now.toISOString().slice(0, 10) || job.endDate < job.originalPostedDate || new Date(`${job.endDate}T23:59:59+09:00`) < now) return null;
  try { const url = new URL(job.originalUrl); if (url.protocol !== 'https:' || !url.hostname || url.username || url.password) return null; }
  catch { return null; }
  const details = [['근무 지역', region], ['경력', job.experience], ['고용 형태', job.hireType]]
    .filter(([, value]) => typeof value === 'string' && value.trim())
    .map(([label, value]) => `<p>${label}: ${htmlEscape(value.trim())}</p>`).join('');
  return {
    '@context': 'https://schema.org/', '@type': 'JobPosting', title,
    description: `<p>${htmlEscape(description).replace(/\r\n?|\n/g, '<br>')}</p>${details}`,
    datePosted: job.originalPostedDate, validThrough: `${job.endDate}T23:59:59+09:00`,
    hiringOrganization: { '@type': 'Organization', name: company },
    jobLocation: { '@type': 'Place', address: { '@type': 'PostalAddress', addressRegion: region, addressCountry: 'KR' } },
  };
}

export function jsonLd(value) {
  return JSON.stringify(value).replace(/</g, '\\u003c').replace(/>/g, '\\u003e').replace(/&/g, '\\u0026');
}
