import { test, expect } from '@playwright/test';
import { Buffer } from 'node:buffer';

test('기업 원문 이미지 표시와 실패 시 원문 링크를 제공한다', async ({ page }) => {
  await page.route('**/api/v1/jobs/12303?*', route => route.fulfill({ json: {
    id: 12303, title: '개발자 채용', companyName: '기업', sourcePlatform: 'COMPANY_PAGE',
    originalUrl: 'https://company.example/bbs/board.php?bo_table=recruit&wr_id=77',
    contentImageUrls: ['https://company.example/data/post.jpg', 'https://evil.example/tracker.png',
      'https://company.example/vector.svg'], techStacks: [],
  } }));
  await page.route('https://company.example/data/post.jpg', route => route.fulfill({ status: 404 }));
  await page.goto('/job/12303');
  const section = page.locator('.job-content-images');
  await expect(section.getByRole('heading', { name: '공고 원문 이미지' })).toBeVisible();
  await expect(section.getByText('이미지를 불러오지 못했습니다.')).toBeVisible();
  await expect(section.getByRole('link', { name: '원문에서 확인하기' })).toHaveAttribute('href', /company\.example/);
  await expect(section.locator('img')).toHaveCount(0);
});

test('허용된 원문 이미지만 반응형으로 표시한다', async ({ page }) => {
  const data = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRk0AAAAASUVORK5CYII=', 'base64');
  await page.route('**/api/v1/jobs/12303?*', route => route.fulfill({ json: {
    id: 12303, title: '개발자 채용', companyName: '기업', sourcePlatform: 'COMPANY_PAGE',
    originalUrl: 'https://company.example/bbs/board.php?bo_table=recruit&wr_id=77',
    contentImageUrls: ['https://company.example/data/post.png', 'https://evil.example/tracker.png'], techStacks: [],
  } }));
  await page.route('https://company.example/data/post.png', route => route.fulfill({ contentType: 'image/png', body: data }));
  await page.goto('/job/12303');
  const image = page.getByRole('img', { name: '채용 공고 원문 이미지 1' });
  await expect(image).toBeVisible();
  await expect(image).toHaveAttribute('referrerpolicy', 'no-referrer');
  expect((await image.boundingBox()).width).toBeLessThanOrEqual(page.viewportSize().width);
  await expect(page.locator('.job-content-images img')).toHaveCount(1);
});
