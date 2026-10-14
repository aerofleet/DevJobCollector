import { test, expect } from '@playwright/test';
import { JOB_ROLES, TECH_STACK_GROUPS } from '../src/data/jobFilters.js';

test('expanded filters preserve URL, API parameters and mobile layout', async ({ page }) => {
  await page.route('**/api/v1/jobs/search**', route => route.fulfill({ json: { content: [], totalElements: 0, totalPages: 0 } }));
  await page.goto('/jobs');
  await expect(page.getByRole('heading', { name: '개발 직무 탐색' })).toBeVisible();
  const toggle = page.getByRole('button', { name: /^필터/ });
  if (await toggle.isVisible()) await toggle.click();
  const role = page.getByLabel('직무 상세', { exact: true });
  const stack = page.getByLabel('기술 스택', { exact: true });
  await expect(role.locator('option')).toHaveCount(JOB_ROLES.length);
  const stacks = TECH_STACK_GROUPS.flatMap(group => group.options);
  expect(new Set(stacks).size).toBe(stacks.length);
  expect(stacks.length).toBeGreaterThan(150);
  await expect(stack.locator('option')).toHaveCount(stacks.length + 1);
  await role.selectOption('embedded');
  const request = page.waitForRequest(req => {
    const url = new URL(req.url());
    return url.pathname.endsWith('/jobs/search') && url.searchParams.get('jobCategory') === 'embedded' && url.searchParams.get('techStack') === 'C++';
  });
  await stack.selectOption('C++');
  await request;
  await expect(page).toHaveURL(/jobCategory=embedded&techStack=C%2B%2B/);
  await page.reload();
  await expect(page.getByRole('heading', { name: '개발 직무 탐색' })).toBeVisible();
  if (await toggle.isVisible()) await toggle.click();
  await expect(role).toHaveValue('embedded');
  await expect(stack).toHaveValue('C++');
  await expect(page.locator('.active-filter-row')).toContainText('임베디드·펌웨어');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByRole('button', { name: '전체 초기화', exact: true }).click();
  await expect(role).toHaveValue('');
  await expect(stack).toHaveValue('');
  await expect(page).toHaveURL(/\/jobs$/);
});
