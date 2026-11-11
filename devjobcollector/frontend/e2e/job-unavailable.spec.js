import { expect, test } from '@playwright/test';

test('없는 공고에 안내와 목록 이동 링크를 표시한다', async ({ page }) => {
  await page.route('**/api/v1/jobs/10179**', route => route.fulfill({ status: 404, json: {} }));
  await page.goto('/job/10179/snow');
  await expect(page.getByRole('heading', { name: '공고를 찾을 수 없거나 종료됐어요' })).toBeVisible();
  const link = page.getByRole('link', { name: '채용 공고 목록 보기' });
  await link.focus();
  await expect(link).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/jobs$/);
});

test('서버 오류를 종료된 공고로 표시하지 않는다', async ({ page }) => {
  await page.route('**/api/v1/jobs/10179**', route => route.fulfill({ status: 500, json: {} }));
  await page.goto('/job/10179/snow');
  await expect(page.getByRole('heading', { name: '공고를 불러오지 못했어요' })).toBeVisible();
  await expect(page.getByText('잠시 후 다시 시도해 주세요.')).toBeVisible();
});
