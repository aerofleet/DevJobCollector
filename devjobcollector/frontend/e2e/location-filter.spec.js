import { test, expect } from '@playwright/test';
import { LOCATION_GROUPS } from '../src/data/locationFilters.js';

async function openFilters(page) {
  await expect(page.getByRole('heading', { name: '개발 직무 탐색' })).toBeVisible();
  const toggle = page.getByRole('button', { name: /^필터/ });
  if (await toggle.isVisible() && await toggle.getAttribute('aria-expanded') === 'false') await toggle.click();
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/jobs/search**', route => route.fulfill({ json: { content: [], totalElements: 0, totalPages: 0 } }));
});

test('multiple domestic overseas and remote selections preserve URL, API and history', async ({ page }) => {
  await page.goto('/jobs?jobCategory=backend&techStack=Java');
  await openFilters(page);
  await page.getByLabel('지역', { exact: true }).click();
  const region = page.getByRole('group', { name: '지역 선택', exact: true });
  await expect(region.getByRole('checkbox')).toHaveCount(LOCATION_GROUPS.flatMap(group => group.options).length);
  const selected = [];
  for (const [value, label] of [['서울', '서울'], ['일본', '일본'], ['Remote', '원격·재택']]) {
    selected.push(value);
    const expected = selected.join(',');
    const request = page.waitForRequest(req => {
      const url = new URL(req.url());
      return url.pathname.endsWith('/jobs/search') && url.searchParams.get('location') === expected && url.searchParams.get('page') === '0';
    });
    await region.getByLabel('지역 검색').fill(label);
    await region.getByLabel(label, { exact: true }).click();
    await expect(region.getByLabel(label, { exact: true })).toBeChecked();
    await request;
    expect(new URL(page.url()).searchParams.get('jobCategory')).toBe('backend');
    expect(new URL(page.url()).searchParams.get('techStack')).toBe('Java');
  }
  await page.reload();
  await openFilters(page);
  await page.getByLabel('지역', { exact: true }).click();
  for (const label of ['서울', '일본', '원격·재택']) {
    await expect(region.getByLabel(label, { exact: true })).toBeChecked();
  }
  await region.getByLabel('지역 검색').press('Escape');
  await page.getByRole('button', { name: '일본 해제', exact: true }).click();
  await expect(page.getByRole('button', { name: '일본 해제', exact: true })).toHaveCount(0);
  expect(new URL(page.url()).searchParams.get('location')).toBe('서울,Remote');
  await page.goBack();
  await expect(page.getByRole('button', { name: '일본 해제', exact: true })).toBeVisible();
  await page.getByLabel('지역', { exact: true }).click();
  await region.getByRole('button', { name: '선택 해제', exact: true }).click();
  await expect(region.getByRole('checkbox', { checked: true })).toHaveCount(0);
  expect(new URL(page.url()).searchParams.has('location')).toBe(false);
  expect(new URL(page.url()).searchParams.get('techStack')).toBe('Java');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('legacy custom location can be combined with a country and removed individually', async ({ page }) => {
  await page.goto('/jobs?location=춘천');
  await openFilters(page);
  await page.getByLabel('지역', { exact: true }).click();
  const region = page.getByRole('group', { name: '지역 선택', exact: true });
  await expect(region.getByLabel('춘천', { exact: true })).toBeChecked();
  await region.getByLabel('지역 검색').fill('일본');
  await region.getByLabel('일본', { exact: true }).click();
  await expect(region.getByLabel('일본', { exact: true })).toBeChecked();
  expect(new URL(page.url()).searchParams.get('location')).toBe('춘천,일본');
  await region.getByLabel('지역 검색').press('Escape');
  await page.getByRole('button', { name: '춘천 해제', exact: true }).click();
  await expect(page.getByRole('button', { name: '춘천 해제', exact: true })).toHaveCount(0);
  expect(new URL(page.url()).searchParams.get('location')).toBe('일본');
});
