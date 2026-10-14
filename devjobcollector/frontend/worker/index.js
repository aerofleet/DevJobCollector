const SITE = 'https://djc.itsdev.kr';
const API = 'https://djc-api.itsdev.kr/api/v1';
const publicPages = ['/', '/jobs', '/terms', '/privacy'];
const appPages = [...publicPages, '/login', '/signup', '/oauth/callback', '/member', '/my-devjobs', '/resumes', '/resume', '/company'];
export const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const text = (value) => String(value ?? '').replace(/<[^>]*>/g, '').replace(/\s+/g, ' ').trim();
const xml = (body) => new Response(`<?xml version="1.0" encoding="UTF-8"?>${body}`, { headers: { 'Content-Type': 'application/xml; charset=utf-8', 'Cache-Control': 'no-store' } });

async function api(path) {
  return fetch(`${API}${path}`, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(5000) });
}

export function metadata(path, job) {
  const titles = { '/': 'DevJobs | 데브잡스', '/jobs': '개발자 채용공고 검색 | 데브잡스', '/terms': '이용약관 | 데브잡스', '/privacy': '개인정보 처리방침 | 데브잡스' };
  return {
    title: job ? `${text(job.title).slice(0, 110)} — ${text(job.companyName).slice(0, 50)} | 데브잡스` : titles[path] || '데브잡스',
    description: job ? [job.companyName, job.location, job.experience, job.hireType, job.title].map(text).filter(Boolean).join(' · ').slice(0, 220) : '흩어진 개발자 채용공고를 한곳에서. 직무와 기술 스택으로 검색하고 관심 공고를 저장하세요.',
    canonical: `${SITE}${path}`,
    index: Boolean(job) || publicPages.includes(path),
  };
}

export function renderHtml(html, path, meta) {
  const e = escapeHtml;
  let result = html.replace(/<title>[\s\S]*?<\/title>/, `<title>${e(meta.title)}</title>`)
    .replace(/<meta name="description"[^>]*>/, `<meta name="description" content="${e(meta.description)}" />`);
  if (path !== '/') result = result.replace(/<div id="root">[\s\S]*?<!--\/prerender--><\/div>/, '<div id="root"></div>');
  return result.replace('</head>', `
    <link rel="canonical" href="${e(meta.canonical)}" />
    <meta name="robots" content="${meta.index ? 'index,follow' : 'noindex,follow'}" />
    <meta property="og:type" content="website" /><meta property="og:locale" content="ko_KR" />
    <meta property="og:site_name" content="데브잡스" />
    <meta property="og:title" content="${e(meta.title)}" />
    <meta property="og:description" content="${e(meta.description)}" />
    <meta property="og:url" content="${e(meta.canonical)}" />
    <meta property="og:image" content="${SITE}/share-card.png" />
    <meta name="twitter:card" content="summary_large_image" />
    <meta name="twitter:title" content="${e(meta.title)}" />
    <meta name="twitter:description" content="${e(meta.description)}" />
    <meta name="twitter:image" content="${SITE}/share-card.png" />
  </head>`);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;
    if (!['GET', 'HEAD'].includes(request.method)) return new Response('Method not allowed', { status: 405 });
    if (path === '/robots.txt') return new Response(`User-agent: *\nAllow: /\nSitemap: ${SITE}/sitemap.xml\n`, { headers: { 'Content-Type': 'text/plain; charset=utf-8' } });
    try {
      if (path === '/sitemap-pages.xml') return xml(`<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${publicPages.map((p) => `<url><loc>${SITE}${p}</loc></url>`).join('')}</urlset>`);
      if (path === '/sitemap.xml') {
        const response = await api('/jobs/active?size=1&page=0');
        if (!response.ok) throw new Error('Job index unavailable');
        const data = await response.json();
        const total = data.totalElements ?? data.page?.totalElements;
        if (!Number.isSafeInteger(total) || total < 0 || total > 25000000) throw new Error('Invalid job count');
        return xml(`<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><sitemap><loc>${SITE}/sitemap-pages.xml</loc></sitemap>${Array.from({ length: Math.ceil(total / 500) }, (_, page) => `<sitemap><loc>${SITE}/sitemap-jobs.xml?page=${page}</loc></sitemap>`).join('')}</sitemapindex>`);
      }
      if (path === '/sitemap-jobs.xml') {
        const page = url.searchParams.get('page') || '0';
        if (!/^\d{1,5}$/.test(page)) return new Response('Invalid page', { status: 400 });
        const response = await api(`/jobs/active?size=500&page=${page}&sort=id,asc`);
        if (!response.ok) throw new Error('Job sitemap unavailable');
        const data = await response.json();
        if (!Array.isArray(data.content)) throw new Error('Invalid job page');
        return xml(`<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${data.content.filter((job) => Number.isSafeInteger(job.id) && job.id > 0).map((job) => `<url><loc>${SITE}/job/${job.id}</loc></url>`).join('')}</urlset>`);
      }
      let job;
      let status = 200;
      if (/^\/job\/\d+$/.test(path)) {
        const response = await api(`/jobs/${path.split('/').pop()}`);
        if (response.status === 404) status = 404;
        else if (!response.ok) throw new Error('Job unavailable');
        else job = await response.json();
      } else if (!appPages.includes(path)) status = 404;
      const asset = await env.ASSETS.fetch(new Request(new URL('/', request.url), request));
      const html = await asset.text();
      const meta = metadata(path, job);
      if (status === 404) meta.index = false;
      return new Response(request.method === 'HEAD' ? null : renderHtml(html, path, meta), { status, headers: {
        'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store',
        'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'strict-origin-when-cross-origin',
      } });
    } catch {
      return new Response('일시적으로 공고 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요.', { status: 503, headers: { 'Retry-After': '60', 'X-Robots-Tag': 'noindex', 'Cache-Control': 'no-store' } });
    }
  },
};
