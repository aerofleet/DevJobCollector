import test from 'node:test';
import assert from 'node:assert/strict';
import worker, { metadata, renderHtml } from './index.js';

const html = '<html><head><title>old</title><meta name="description" content="old" /></head><body><div id="root"><h1>home</h1><!--/prerender--></div></body></html>';
const env = { ASSETS: { fetch: async () => new Response(html) } };
const request = (path) => new Request(`https://djc.itsdev.kr${path}`);
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
  const body = await (await worker.fetch(request('/job/7?token=secret'), env)).text();
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
