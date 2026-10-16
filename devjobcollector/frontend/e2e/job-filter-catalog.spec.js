import { test, expect } from '@playwright/test';
import { JOB_ROLES, QUICK_JOB_ROLES, TECH_STACK_GROUPS } from '../src/data/jobFilters.js';

async function openFilters(page) {
  await expect(page.getByRole('heading', { name: '개발 직무 탐색' })).toBeVisible();
  const toggle = page.getByRole('button', { name: /^필터/ });
  if (await toggle.isVisible() && await toggle.getAttribute('aria-expanded') === 'false') await toggle.click();
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/jobs/search**', route => route.fulfill({ json: { content: [], totalElements: 0, totalPages: 0 } }));
});

test('multiple selections preserve URL, API, individual removal and history', async ({ page }) => {
  await page.goto('/jobs');
  await openFilters(page);
  await page.getByLabel('직무 상세', { exact: true }).click();
  const role = page.getByRole('group', { name: '직무 상세 선택' });
  await expect(role.getByRole('checkbox')).toHaveCount(JOB_ROLES.length - 1);
  await role.getByLabel('백엔드', { exact: true }).click();
  await expect(role.getByLabel('백엔드', { exact: true })).toBeChecked();
  await role.getByLabel('프론트엔드', { exact: true }).click();
  await expect(role.getByLabel('프론트엔드', { exact: true })).toBeChecked();
  await page.getByLabel('기술 스택', { exact: true }).click();
  const stack = page.getByRole('group', { name: '기술 스택 선택' });
  await expect(stack.getByRole('checkbox')).toHaveCount(TECH_STACK_GROUPS.flatMap(group => group.options).length);
  await stack.getByLabel('C++', { exact: true }).click();
  await expect(stack.getByLabel('C++', { exact: true })).toBeChecked();
  const request = page.waitForRequest(req => {
    const url = new URL(req.url());
    return url.pathname.endsWith('/jobs/search') && url.searchParams.get('jobCategory') === 'backend,frontend' && url.searchParams.get('techStack') === 'C++,Java' && url.searchParams.get('page') === '0';
  });
  await stack.getByLabel('Java', { exact: true }).click();
  await expect(stack.getByLabel('Java', { exact: true })).toBeChecked();
  await request;
  expect(new URL(page.url()).searchParams.get('techStack')).toBe('C++,Java');
  await page.reload();
  await openFilters(page);
  await page.getByLabel('직무 상세', { exact: true }).click();
  await expect(role.getByLabel('백엔드', { exact: true })).toBeChecked();
  await expect(role.getByLabel('프론트엔드', { exact: true })).toBeChecked();
  await page.getByLabel('기술 스택', { exact: true }).click();
  await expect(stack.getByLabel('C++', { exact: true })).toBeChecked();
  await expect(stack.getByLabel('Java', { exact: true })).toBeChecked();
  await stack.getByLabel('기술 스택 검색').fill('zzzz');
  await expect(stack.getByText('일치하는 항목이 없습니다.')).toBeVisible();
  await stack.getByLabel('기술 스택 검색').press('Escape');
  await expect(stack).toBeHidden();
  await page.getByRole('button', { name: 'Java 해제', exact: true }).click();
  expect(new URL(page.url()).searchParams.get('techStack')).toBe('C++');
  await page.goBack();
  await expect(page.getByRole('button', { name: 'Java 해제', exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByRole('button', { name: '전체 초기화', exact: true }).click();
  await expect(page).toHaveURL(/\/jobs$/);
});

test('legacy URL and quick role buttons support multiple selections', async ({ page }) => {
  await page.goto('/jobs?jobCategory=backend&techStack=Java');
  await openFilters(page);
  const quick = page.getByRole('group', { name: '개발 직무', exact: true });
  await expect(quick.getByRole('button', { name: '백엔드', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await quick.getByRole('button', { name: '프론트엔드', exact: true }).click();
  await expect(quick.getByRole('button', { name: '프론트엔드', exact: true })).toHaveAttribute('aria-pressed', 'true');
  expect(new URL(page.url()).searchParams.get('jobCategory')).toBe('backend,frontend,web-publishing');
  await quick.getByRole('button', { name: '백엔드', exact: true }).click();
  await expect(quick.getByRole('button', { name: '백엔드', exact: true })).toHaveAttribute('aria-pressed', 'false');
  expect(new URL(page.url()).searchParams.get('jobCategory')).toBe('frontend,web-publishing');
  await quick.getByRole('button', { name: '전체 직무', exact: true }).click();
  await expect(quick.getByRole('button', { name: '전체 직무', exact: true })).toHaveAttribute('aria-pressed', 'true');
  expect(new URL(page.url()).searchParams.has('jobCategory')).toBe(false);
  expect(new URL(page.url()).searchParams.get('techStack')).toBe('Java');
});

test('quick categories cover every detailed role and toggle complete groups', async ({ page }) => {
  expect(new Set(QUICK_JOB_ROLES.flatMap(option => option.values)))
    .toEqual(new Set(JOB_ROLES.filter(role => role.value).map(role => role.value)));
  await page.goto('/jobs?techStack=Java');
  await openFilters(page);
  const quick = page.getByRole('group', { name: '개발 직무', exact: true });
  await expect(quick.getByRole('button')).toHaveCount(15);
  for (const option of QUICK_JOB_ROLES.filter(option => option.value)) {
    const button = quick.getByRole('button', { name: option.label, exact: true });
    const request = page.waitForRequest(req => {
      const url = new URL(req.url());
      return url.pathname.endsWith('/jobs/search') && url.searchParams.get('jobCategory') === option.values.join(',');
    });
    await button.click();
    await expect(button).toHaveAttribute('aria-pressed', 'true');
    await request;
    expect(new URL(page.url()).searchParams.get('techStack')).toBe('Java');
    await button.click();
    await expect(button).toHaveAttribute('aria-pressed', 'false');
    expect(new URL(page.url()).searchParams.has('jobCategory')).toBe(false);
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('partial category selection can be completed and removed without clearing other roles', async ({ page }) => {
  await page.goto('/jobs?jobCategory=backend,robotics&techStack=Java');
  await openFilters(page);
  const category = page.getByRole('button', { name: '임베디드·로보틱스·IoT', exact: true });
  await expect(category).toHaveAttribute('aria-pressed', 'mixed');
  await category.click();
  await expect(category).toHaveAttribute('aria-pressed', 'true');
  expect(new URL(page.url()).searchParams.get('jobCategory').split(','))
    .toEqual(['backend', 'robotics', 'embedded', 'iot']);
  await page.getByRole('button', { name: 'IoT 해제', exact: true }).click();
  await expect(category).toHaveAttribute('aria-pressed', 'mixed');
  await category.click();
  await expect(category).toHaveAttribute('aria-pressed', 'true');
  await category.click();
  await expect(category).toHaveAttribute('aria-pressed', 'false');
  expect(new URL(page.url()).searchParams.get('jobCategory')).toBe('backend');
  expect(new URL(page.url()).searchParams.get('techStack')).toBe('Java');
});
