import { test, expect } from '@playwright/test';
import { Buffer } from 'node:buffer';

function pdfFixture() {
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Resources << >> /Contents 4 0 R >>',
    '<< /Length 0 >>\nstream\n\nendstream',
  ];
  let body = '%PDF-1.4\n';
  const offsets = [0];
  objects.forEach((object, index) => { offsets.push(Buffer.byteLength(body)); body += `${index + 1} 0 obj\n${object}\nendobj\n`; });
  const xref = Buffer.byteLength(body);
  body += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  offsets.slice(1).forEach(offset => { body += `${String(offset).padStart(10, '0')} 00000 n \n`; });
  body += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`;
  return Buffer.from(body);
}

async function openJob(page, filename, handler) {
  await page.route('**/api/v1/jobs/12303?*', route => route.fulfill({ json: {
    id: 12303, title: '채용', companyName: '기관', sourcePlatform: 'PUBLIC_ALIO', location: '서울',
    experience: '신입', techStacks: [], files: [{ id: 11, fileName: filename, fileUrl: 'https://www.alio.go.kr/download/file' }],
  } }));
  await page.route('**/api/v1/job-files/11/preview', handler);
  await page.goto('/job/12303');
}

test('PDF is loaded on demand, rendered and restores focus', async ({ page }) => {
  let requests = 0;
  await openJob(page, '공고.pdf', route => { requests++; return route.fulfill({ body: pdfFixture() }); });
  const trigger = page.getByRole('button', { name: '미리보기' });
  expect(requests).toBe(0);
  await trigger.click();
  const dialog = page.getByRole('dialog', { name: '첨부문서 미리보기' });
  await expect(dialog.locator('canvas')).toHaveJSProperty('width', 800, { timeout: 30000 });
  await dialog.getByRole('button', { name: '확대' }).click();
  await expect(dialog.getByText('125%')).toBeVisible();
  expect(requests).toBe(1);
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
});

for (const ext of ['png', 'jpg', 'jpeg', 'webp']) {
  test(`${ext} image preview renders and zooms`, async ({ page }) => {
    const mime = ext === 'jpg' || ext === 'jpeg' ? 'image/jpeg' : `image/${ext}`;
    const data = await page.evaluate(type => {
      const canvas = document.createElement('canvas'); canvas.width = 4; canvas.height = 4;
      canvas.getContext('2d').fillRect(0, 0, 4, 4);
      return canvas.toDataURL(type).split(',')[1];
    }, mime);
    await openJob(page, `사진.${ext}`, route => route.fulfill({ body: Buffer.from(data, 'base64') }));
    await page.getByRole('button', { name: '미리보기' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('img')).toBeVisible();
    await expect(dialog.getByRole('img')).toHaveJSProperty('naturalWidth', 4);
    await dialog.getByRole('button', { name: '확대' }).click();
    await expect(dialog.getByText('125%')).toBeVisible();
    await dialog.getByRole('button', { name: '원본 크기' }).click();
    await expect(dialog.getByText('원본 크기', { exact: true }).first()).toBeVisible();
  });
}

test('retry and fallback remain available on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 });
  let requests = 0;
  await openJob(page, '공고.pdf', route => ++requests === 1 ? route.fulfill({ status: 429 }) : route.fulfill({ body: 'invalid' }));
  await page.getByRole('button', { name: '미리보기' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('alert')).toContainText('요청이 많습니다');
  await dialog.getByRole('button', { name: '다시 시도' }).click();
  await expect(dialog.getByRole('alert')).toContainText('원본 파일');
  await expect(dialog.getByRole('link', { name: '원본 파일 열기 / 다운로드' })).toBeVisible();
  expect((await dialog.boundingBox()).width).toBeLessThanOrEqual(360);
});

test('releases image Blob URL and hides unsupported formats', async ({ page }) => {
  await page.addInitScript(() => {
    window.__previewUrls = { created: [], revoked: [] };
    const create = URL.createObjectURL.bind(URL), revoke = URL.revokeObjectURL.bind(URL);
    URL.createObjectURL = value => { const url = create(value); window.__previewUrls.created.push(url); return url; };
    URL.revokeObjectURL = url => { window.__previewUrls.revoked.push(url); revoke(url); };
  });
  const data = await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 2; canvas.height = 2;
    return canvas.toDataURL('image/png').split(',')[1];
  });
  await openJob(page, '사진.png', route => route.fulfill({ body: Buffer.from(data, 'base64') }));
  const trigger = page.getByRole('button', { name: '미리보기' });
  await trigger.click();
  await expect(page.getByRole('dialog').getByRole('img')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(trigger).toBeFocused();
  const urls = await page.evaluate(() => window.__previewUrls);
  expect(urls.created).toHaveLength(1);
  expect(urls.revoked).toEqual(urls.created);
});

test('does not expose a preview for SVG or an untrusted host', async ({ page }) => {
  await page.route('**/api/v1/jobs/12303?*', route => route.fulfill({ json: {
    id: 12303, title: '채용', companyName: '기관', sourcePlatform: 'PUBLIC_ALIO', techStacks: [],
    files: [{ id: 11, fileName: 'image.svg', fileUrl: 'https://www.alio.go.kr/file' },
      { id: 12, fileName: 'file.pdf', fileUrl: 'https://evil.example/file' }],
  } }));
  await page.goto('/job/12303');
  await expect(page.getByRole('button', { name: '미리보기' })).toHaveCount(0);
});
