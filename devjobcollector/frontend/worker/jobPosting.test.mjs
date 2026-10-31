import test from 'node:test';
import assert from 'node:assert/strict';
import { jobPosting, jsonLd } from './jobPosting.js';
import worker from './index.js';

const now = new Date('2026-10-31T12:00:00Z');
const job = { id: 9, sourcePlatform: 'COMPANY_DIRECT', active: true, title: 'Developer', companyName: 'Example',
  location: '서울', startDate: '2026-10-10', originalPostedDate: '2026-10-01', originalPostedDateVerified: true,
  completeDescriptionVerified: true, endDate: '2026-11-30', originalUrl: 'https://example.com/apply',
  applyQual: 'Build services\nRequired: Java' };

test('valid employer post uses truthful publication date, region and full description', () => {
  const data = jobPosting(job, now);
  assert.equal(data.datePosted, '2026-10-01');
  assert.equal(data.validThrough, '2026-11-30T23:59:59+09:00');
  assert.equal(data.jobLocation.address.addressCountry, 'KR');
  assert.equal(data.description, '<p>Build services<br>Required: Java</p><p>근무 지역: 서울</p>');
  assert.equal(data.hiringOrganization.name, 'Example');
});
test('excludes unverified, expired, inactive or incomplete source data', () => {
  for (const changes of [
    { sourcePlatform: 'GREENHOUSE' }, { active: false }, { location: 'Remote' }, { location: '서울 또는 미국' },
    { originalPostedDate: '2026-02-30' }, { originalPostedDateVerified: false }, { completeDescriptionVerified: false },
    { endDate: '2026-10-30' }, { applyQual: '' },
    { originalUrl: 'javascript:alert(1)' }, { companyName: '' },
  ]) assert.equal(jobPosting({ ...job, ...changes }, now), null);
  const currentApiShape = { ...job };
  delete currentApiShape.originalPostedDateVerified;
  delete currentApiShape.completeDescriptionVerified;
  assert.equal(jobPosting(currentApiShape, now), null);
});
test('JSON-LD cannot close script element with untrusted text', () => {
  const serialized = jsonLd(jobPosting({ ...job, title: '</script><script>alert(1)</script>' }, now));
  assert.ok(!serialized.includes('</script>'));
  assert.equal(JSON.parse(serialized).title, '</script><script>alert(1)</script>');
});
test('eligible detail returns JSON-LD in raw HTML and absent for other pages', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ ...job, originalPostedDate: '2020-01-01', endDate: '2090-01-01' }));
  const env = { ASSETS: { fetch: async () => new Response('<html><head><title>old</title><meta name="description" content="old" /></head><body><div id="root"></div></body></html>') } };
  const response = await worker.fetch(new Request('https://djc.itsdev.kr/job/9/developer'), env);
  const html = await response.text();
  assert.equal(response.status, 200);
  assert.match(html, /<script type="application\/ld\+json">/);
  const data = JSON.parse(html.match(/<script type="application\/ld\+json">(.*?)<\/script>/)[1]);
  assert.equal(data['@type'], 'JobPosting');
  const home = await worker.fetch(new Request('https://djc.itsdev.kr/'), env);
  assert.doesNotMatch(await home.text(), /application\/ld\+json/);
});
