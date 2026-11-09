import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { Buffer } from 'node:buffer';

const job = { id: 12303, title: '정보통신 채용', companyName: '공공기관', sourcePlatform: 'PUBLIC_ALIO', location: '서울', experience: '신입', techStacks: [], applyQual: '공고 내용', files: [{ id: 11723, fileName: '공고문.hwp', fileUrl: 'https://opendata.alio.go.kr/recruit/downloadAtchFile?recrutAtchFileNo=3085641' }, { id: 3, fileName: '서류.pdf', fileUrl: 'https://example.com/file.pdf' }] };

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/jobs/12303?*', route => route.fulfill({ json: job }));
});

test('shows a lazy-loaded page and provides a source link', async ({ page }) => {
  await page.route('**/src/utils/hwpWorkerClient.js*', route => route.fulfill({ contentType: 'text/javascript', body: 'export async function openHwpWorker() { return { count: 1, renderPage: async () => `<svg viewBox="0 0 200 300"><text x="10" y="20">테스트</text></svg>`, close: () => {} }; }' }));
  let count = 0;
  await page.route('**/api/v1/job-files/11723/preview', route => { count++; return route.fulfill({ body: 'fake-bytes' }); });
  await page.goto('/job/12303');
  expect(count).toBe(0);
  await page.getByRole('button', { name: '미리보기' }).click();
  const dialog = page.getByRole('dialog', { name: '첨부문서 미리보기' });
  await expect(dialog.locator('iframe').first()).toBeVisible();
  await expect(dialog.locator('iframe').first()).toHaveAttribute('sandbox', '');
  expect(count).toBe(1);
});

test('loads an ALIO HWP on demand and closes the accessible preview', async ({ page }) => {
  const sample = path.join(os.tmpdir(), 'djc-alio-3085282.hwp');
  test.skip(!fs.existsSync(sample), 'Local public ALIO sample is not available');
  let requests = 0;
  await page.route('**/api/v1/job-files/11723/preview', route => { requests++; return route.fulfill({ body: fs.readFileSync(sample), contentType: 'application/octet-stream' }); });
  await page.goto('/job/12303');
  const trigger = page.getByRole('button', { name: '미리보기' });
  await expect(trigger).toHaveCount(1);
  expect(requests).toBe(0);
  await trigger.click();
  const dialog = page.getByRole('dialog', { name: '첨부문서 미리보기' });
  await expect(dialog).toBeVisible();
  await expect(dialog.locator('iframe').first()).toBeVisible({ timeout: 30000 });
  await expect(dialog.locator('iframe').first()).toHaveAttribute('sandbox', '');
  await expect(dialog.getByRole('link', { name: '원본 파일 열기 / 다운로드' })).toHaveAttribute('href', 'https://www.alio.go.kr/download/download.json?fileNo=3085641');
  expect(requests).toBe(1);
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
});

test('offers the source file when preview fails', async ({ page }) => {
  await page.route('**/api/v1/job-files/11723/preview', route => route.fulfill({ status: 502, body: 'unavailable' }));
  await page.goto('/job/12303');
  await page.getByRole('button', { name: '미리보기' }).click();
  const dialog = page.getByRole('dialog', { name: '첨부문서 미리보기' });
  await expect(dialog.getByRole('alert')).toContainText('원본 파일');
  await expect(dialog.getByRole('link', { name: '원본 파일 열기 / 다운로드' })).toBeVisible();
});

test('allows retry when the preview is busy', async ({ page }) => {
  await page.route('**/src/utils/hwpWorkerClient.js*', route => route.fulfill({ contentType: 'text/javascript', body: 'export async function openHwpWorker() { return { count: 1, renderPage: async () => `<svg viewBox="0 0 200 300"></svg>`, close: () => {} }; }' }));
  let requests = 0;
  await page.route('**/api/v1/job-files/11723/preview', route => ++requests === 1 ? route.fulfill({ status: 429 }) : route.fulfill({ body: 'fake-bytes' }));
  await page.goto('/job/12303');
  await page.getByRole('button', { name: '미리보기' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('alert')).toContainText('요청이 많습니다');
  await dialog.getByRole('button', { name: '다시 시도' }).click();
  await expect(dialog.locator('iframe')).toHaveCount(1);
  expect(requests).toBe(2);
});

test('renders the preview inside the mobile viewport', async ({ page }) => {
  const sample = path.join(os.tmpdir(), 'djc-alio-3085282.hwp');
  test.skip(!fs.existsSync(sample), 'Local public ALIO sample is not available');
  await page.setViewportSize({ width: 360, height: 800 });
  await page.route('**/api/v1/job-files/11723/preview', route => route.fulfill({ body: fs.readFileSync(sample), contentType: 'application/octet-stream' }));
  await page.goto('/job/12303');
  await page.getByRole('button', { name: '미리보기' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.locator('iframe').first()).toBeVisible({ timeout: 30000 });
  const box = await dialog.boundingBox();
  expect(box.width).toBeLessThanOrEqual(360);
  await expect(dialog.getByRole('button', { name: '미리보기 닫기' })).toBeVisible();
});

test('opens a synthetically padded HWP larger than 20 MiB', async ({ page }) => {
  test.setTimeout(90000);
  const sample = path.join(os.tmpdir(), 'djc-alio-3085282.hwp');
  test.skip(!fs.existsSync(sample), 'Local public ALIO sample is not available');
  const original = fs.readFileSync(sample);
  const padded = Buffer.concat([original, Buffer.alloc(21 * 1024 * 1024 - original.length)]);
  await page.route('**/api/v1/job-files/11723/preview', route => route.fulfill({ body: padded, contentType: 'application/octet-stream' }));
  await page.goto('/job/12303');
  await page.getByRole('button', { name: '미리보기' }).click();
  await expect(page.getByRole('dialog').locator('iframe').first()).toBeVisible({ timeout: 60000 });
});

test('opens a real image-heavy HWP larger than 20 MiB on mobile', async ({ page }) => {
  test.setTimeout(120000);
  const sample = path.join(os.tmpdir(), 'djc-real-large.hwp');
  test.skip(!fs.existsSync(sample), 'Public large HWP reference is not available');
  const bytes = fs.readFileSync(sample);
  expect(bytes.length).toBeGreaterThan(20 * 1024 * 1024);
  await page.setViewportSize({ width: 360, height: 800 });
  await page.route('**/api/v1/job-files/11723/preview', route => route.fulfill({ body: bytes, contentType: 'application/octet-stream' }));
  await page.goto('/job/12303');
  const started = Date.now();
  await page.getByRole('button', { name: '미리보기' }).click();
  const pages = page.locator('.hwp-page');
  await expect(pages).toHaveCount(7, { timeout: 60000 });
  await expect(pages.first().locator('iframe')).toBeVisible({ timeout: 60000 });
  console.log(`Real HWP ${bytes.length} bytes, first page ${Date.now() - started}ms`);
  await pages.last().scrollIntoViewIfNeeded();
  await expect(pages.last().locator('iframe')).toBeVisible({ timeout: 60000 });
  await expect.poll(() => page.locator('.hwp-page iframe').count()).toBeLessThanOrEqual(6);
});

test('renders a real long HWPX lazily', async ({ page }) => {
  test.setTimeout(120000);
  const sample = path.join(os.tmpdir(), 'djc-real-long.hwpx');
  test.skip(!fs.existsSync(sample), 'Public long HWPX reference is not available');
  await page.route('**/api/v1/jobs/12303?*', route => route.fulfill({ json: { ...job, files: [{ ...job.files[0], fileName: '지침.hwpx' }] } }));
  await page.route('**/api/v1/job-files/11723/preview', route => route.fulfill({ body: fs.readFileSync(sample), contentType: 'application/octet-stream' }));
  await page.goto('/job/12303');
  await page.getByRole('button', { name: '미리보기' }).click();
  const pages = page.locator('.hwp-page');
  await expect.poll(() => pages.count(), { timeout: 60000 }).toBeGreaterThanOrEqual(50);
  const count = await pages.count();
  console.log(`Real HWPX pages: ${count}`);
  await expect(pages.first().locator('iframe')).toBeVisible({ timeout: 60000 });
  await pages.last().scrollIntoViewIfNeeded();
  await expect(pages.last().locator('iframe')).toBeVisible({ timeout: 60000 });
  await expect.poll(() => page.locator('.hwp-page iframe').count()).toBeLessThanOrEqual(6);
});

test('renders a long document lazily when scrolling to the last page', async ({ page }) => {
  test.setTimeout(90000);
  const sample = path.join(os.tmpdir(), 'djc-hwp-corpus', 'synthetic-60-pages.hwpx');
  test.skip(!fs.existsSync(sample), 'Synthetic long document is not available');
  await page.route('**/api/v1/job-files/11723/preview', route => route.fulfill({ body: fs.readFileSync(sample), contentType: 'application/octet-stream' }));
  await page.goto('/job/12303');
  await page.getByRole('button', { name: '미리보기' }).click();
  const pages = page.locator('.hwp-page');
  await expect(pages).toHaveCount(62, { timeout: 30000 });
  await expect(pages.first().locator('iframe')).toBeVisible();
  await pages.last().scrollIntoViewIfNeeded();
  await expect(pages.last().locator('iframe')).toBeVisible({ timeout: 30000 });
  await expect.poll(() => page.locator('.hwp-page iframe').count()).toBeLessThanOrEqual(6);
});
