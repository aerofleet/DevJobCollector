import { test, expect } from '@playwright/test';

test('loading and API failure do not claim zero jobs; retry restores the real count', async ({ page }) => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  let failing = true;
  await page.route('**/api/v1/jobs/search**', async route => {
    await gate;
    if (failing) return route.fulfill({ status: 526, body: 'error code: 526' });
    return route.fulfill({ json: { content: [{ id: 42, title: '백엔드 개발자', companyName: '테스트', techStacks: [] }], totalElements: 1, totalPages: 1 } });
  });
  await page.goto('/jobs');
  await expect(page.locator('.results-toolbar')).toContainText('공고를 확인하고 있습니다.');
  await expect(page.locator('.results-toolbar strong')).toHaveCount(0);
  release();
  await expect(page.getByRole('alert')).toContainText('공고를 불러오지 못했습니다.');
  await expect(page.locator('.results-toolbar')).toContainText('공고 수를 확인하지 못했습니다.');
  await expect(page.locator('.results-toolbar strong')).toHaveCount(0);
  await expect(page.getByText('조건에 맞는 공고가 없어요.')).toHaveCount(0);
  failing = false;
  await page.getByRole('button', { name: '다시 불러오기' }).click();
  await expect(page.locator('.results-toolbar')).toContainText('조건에 맞는 공고 1개');
  await expect(page.locator('.job-card')).toHaveCount(1);
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('a successful empty response explicitly shows zero jobs', async ({ page }) => {
  await page.route('**/api/v1/jobs/search**', route => route.fulfill({ json: { content: [], page: { totalElements: 0, totalPages: 0 } } }));
  await page.goto('/jobs');
  await expect(page.locator('.results-toolbar')).toContainText('조건에 맞는 공고 0개');
  await expect(page.getByText('조건에 맞는 공고가 없어요.')).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
});
