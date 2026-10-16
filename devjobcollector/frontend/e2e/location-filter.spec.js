import { test, expect } from '@playwright/test';
import { LOCATION_GROUPS } from '../src/data/locationFilters.js';

async function openFilters(page) {
  await expect(page.getByRole('heading', { name: '개발 직무 탐색' })).toBeVisible();
  const toggle = page.getByRole('button', { name: /^필터/ });
  if (await toggle.isVisible()) await toggle.click();
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/jobs/search**', route => route.fulfill({ json: { content: [], totalElements: 0, totalPages: 0 } }));
});

test('domestic and overseas regions preserve other filters, URL and history', async ({ page }) => {
  await page.goto('/jobs?jobCategory=backend&techStack=Java');
  await openFilters(page);
  const region = page.getByLabel('지역', { exact: true });
  await expect(region.locator('option')).toHaveCount(LOCATION_GROUPS.flatMap(group => group.options).length + 1);
  await expect(region.locator('optgroup[label="국내"] option')).toHaveCount(19);
  for (const value of ['충북', '일본', '미국', 'overseas', 'domestic', 'Remote']) {
    const request = page.waitForRequest(req => {
      const url = new URL(req.url());
      return url.pathname.endsWith('/jobs/search') && url.searchParams.get('location') === value && url.searchParams.get('page') === '0';
    });
    await region.selectOption(value);
    await request;
    await expect(region).toHaveValue(value);
    expect(new URL(page.url()).searchParams.get('jobCategory')).toBe('backend');
    expect(new URL(page.url()).searchParams.get('techStack')).toBe('Java');
  }
  await expect(page.getByRole('button', { name: '원격·재택 해제' })).toBeVisible();
  await page.reload();
  await openFilters(page);
  await expect(region).toHaveValue('Remote');
  await page.goBack();
  await expect(region).toHaveValue('domestic');
  await page.getByRole('button', { name: '국내 전체 해제' }).click();
  await expect(region).toHaveValue('');
  expect(new URL(page.url()).searchParams.get('techStack')).toBe('Java');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('legacy custom location URLs still display the selected location', async ({ page }) => {
  await page.goto('/jobs?location=춘천');
  await openFilters(page);
  await expect(page.getByLabel('지역', { exact: true })).toHaveValue('춘천');
  await expect(page.getByRole('button', { name: '춘천 해제' })).toBeVisible();
});
