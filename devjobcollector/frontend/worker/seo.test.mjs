import test from 'node:test';
import assert from 'node:assert/strict';
import worker, { metadata, renderHtml } from './index.js';

const html = '<html><head><title>old</title><meta name="description" content="old" /></head><body><div id="root"><h1>home</h1><!--/prerender--></div></body></html>';
const env = { ASSETS: { fetch: async () => new Response(html) } };
const request = (path) => new Request(`https://djc.itsdev.kr${path}`);
globalThis.caches ??= undefined;
test('metadata escapes untrusted job content and strips query from canonical', () => {
  const result = renderHtml(html, '/job/7', metadata('/job/7', { title: '개발자 " onload="x <script>bad</script>', companyName: 'A&B' }));
  assert.ok(result.includes('&quot;'));
  assert.ok(!result.includes('<script>bad'));
  assert.ok(result.includes('https://djc.itsdev.kr/job/7'));
  assert.ok(!result.includes('<h1>home</h1>'));
});
test('home preserves prerendered body', () => assert.ok(renderHtml(html, '/', metadata('/')).includes('<h1>home</h1>')));
test('private routes are noindex', () => assert.ok(renderHtml(html, '/member', metadata('/member')).includes('noindex,follow')));
test('robots is real text with sitemap', async () => {
  const response = await worker.fetch(request('/robots.txt'), env);
  assert.equal(response.status, 200);
  assert.ok(response.headers.get('Content-Type').startsWith('text/plain'));
  assert.ok((await response.text()).includes('Sitemap: https://djc.itsdev.kr/sitemap.xml'));
});
test('sitemap index includes all paginated job sets', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ totalElements: 1001 }));
  const response = await worker.fetch(request('/sitemap.xml'), env);
  assert.equal(response.status, 200);
  assert.ok(response.headers.get('Content-Type').includes('xml'));
  const body = await response.text();
  assert.equal((body.match(/<sitemap>/g) || []).length, 4);
  assert.ok(body.includes('page=2'));
});
test('job sitemap only contains valid public job IDs', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ content: [{ id: 3 }, { id: '<script>' }] }));
  const body = await (await worker.fetch(request('/sitemap-jobs.xml?page=0'), env)).text();
  assert.ok(body.includes('/job/3'));
  assert.ok(!body.includes('<script>'));
});
test('job metadata is in raw HTML and query credentials never enter canonical', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ id: 7, title: 'Java 개발자', companyName: '기업' }));
  const body = await (await worker.fetch(request('/job/7/java-%EA%B0%9C%EB%B0%9C%EC%9E%90?token=secret'), env)).text();
  assert.ok(body.includes('Java 개발자'));
  assert.ok(body.includes('og:image'));
  assert.ok(!body.includes('secret'));
});
test('hidden or deleted job is 404 and noindex', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response('', { status: 404 }));
  const response = await worker.fetch(request('/job/7'), env);
  assert.equal(response.status, 404);
  assert.ok((await response.text()).includes('noindex,follow'));
});
test('upstream outage does not publish an empty successful sitemap', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => { throw new Error('timeout'); });
  const response = await worker.fetch(request('/sitemap.xml'), env);
  assert.equal(response.status, 503);
  assert.equal(response.headers.get('Retry-After'), '60');
});

test('legacy and outdated job URLs redirect to the title URL', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ id: 7, title: 'Java \uac1c\ubc1c\uc790', companyName: 'Company' }));
  for (const path of ['/job/7', '/job/7/old-title']) {
    const response = await worker.fetch(request(path), env);
    assert.equal(response.status, 301);
    assert.equal(response.headers.get('Location'), 'https://djc.itsdev.kr/job/7/java-%EA%B0%9C%EB%B0%9C%EC%9E%90');
  }
});
test('sitemap and canonical use the same title URL', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url) => Response.json(url.includes('/active?') ? { content: [{ id: 7, title: 'Java \uac1c\ubc1c\uc790' }] } : { id: 7, title: 'Java \uac1c\ubc1c\uc790' }));
  const path = '/job/7/java-%EA%B0%9C%EB%B0%9C%EC%9E%90';
  assert.ok((await (await worker.fetch(request('/sitemap-jobs.xml'), env)).text()).includes(path));
  const response = await worker.fetch(request(path), env);
  assert.equal(response.status, 200);
  assert.ok((await response.text()).includes(`rel="canonical" href="https://djc.itsdev.kr${path}"`));
});

test('sitemap emits only valid past modification dates, not publication or collection dates', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ content: [
    { id: 1, title: 'A', updatedAt: '2025-03-04T10:00:00+09:00' },
    { id: 2, title: 'B', modifiedAt: '2025-03-04' },
    { id: 3, title: 'C', updatedAt: '2025-02-30', startDate: '2025-01-01' },
    { id: 4, title: 'D', updatedAt: '2999-01-01', collectedAt: '2025-01-01' },
    { id: 5, title: 'E', updatedAt: '2025-03-04T10:00:00', lastSeenAt: '2025-01-01' },
  ] }));
  const response = await worker.fetch(request('/sitemap-jobs.xml?page=0'), env);
  const body = await response.text();
  assert.ok(body.includes('<lastmod>2025-03-04T01:00:00.000Z</lastmod>'));
  assert.ok(body.includes('<lastmod>2025-03-04</lastmod>'));
  assert.equal((body.match(/<lastmod>/g) || []).length, 2);
  assert.equal(response.headers.get('Cache-Control'), 'public, max-age=300, s-maxage=1800');
});

test('sitemap cache stores successful GET responses and serves later hits', async (t) => {
  const stored = new Map();
  let calls = 0;
  t.mock.method(globalThis, 'fetch', async () => { calls++; return Response.json({ totalElements: 1 }); });
  t.mock.property(globalThis, 'caches', { default: {
    match: async (cacheRequest) => stored.get(cacheRequest.url)?.clone(),
    put: async (cacheRequest, response) => { stored.set(cacheRequest.url, response.clone()); },
  } });
  const pending = [];
  const ctx = { waitUntil: (promise) => pending.push(promise) };
  const first = await worker.fetch(request('/sitemap.xml'), env, ctx);
  await Promise.all(pending);
  const second = await worker.fetch(request('/sitemap.xml'), env, ctx);
  assert.equal(calls, 1);
  assert.equal(await first.text(), await second.text());
  assert.equal((await worker.fetch(request('/sitemap-pages.xml'), env)).headers.get('Cache-Control'), 'public, max-age=3600, s-maxage=86400');
});

test('upstream errors remain no-store and are never cached', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => { throw new Error('offline'); });
  let writes = 0;
  t.mock.property(globalThis, 'caches', { default: { match: async () => undefined, put: async () => { writes++; } } });
  const response = await worker.fetch(request('/sitemap-jobs.xml?page=0'), env);
  assert.equal(response.status, 503);
  assert.equal(response.headers.get('Cache-Control'), 'no-store');
  assert.equal(writes, 0);
});

