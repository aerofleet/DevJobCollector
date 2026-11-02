import { test, expect } from '@playwright/test';
import { JOB_ROLES, QUICK_JOB_ROLES, TECH_STACK_GROUPS } from '../src/data/jobFilters.js';

async function openFilters(page) {
  await expect(page.getByRole('heading', { name: '개발 직무 탐색' })).toBeVisible();
  const toggle = page.getByRole('button', { name: /^필터/ });
  if (await toggle.isVisible() && await toggle.getAttribute('aria-expanded') === 'false') await toggle.click();
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/jobs/search**', route => route.fulfill({ json: { content: [], totalElements: 0, totalPages: 0 } }));
  await page.route('**/api/v1/jobs/companies**', route => route.fulfill({ json: ['토스', '카카오', 'ACME, Inc.'] }));
});

test('회사명을 검색해 복수 선택하고 URL과 API에 정확히 전달한다', async ({ page }) => {
  await page.goto('/jobs?techStack=Java');
  await openFilters(page);
  await page.getByLabel('회사명', { exact: true }).click();
  const companies = page.getByRole('group', { name: '회사명 선택' });
  await companies.getByLabel('토스', { exact: true }).click();
  await expect(companies.getByLabel('토스', { exact: true })).toBeChecked();
  await companies.getByLabel('회사명 검색').fill('ACME');
  const request = page.waitForRequest(req => {
    const url = new URL(req.url());
    return url.pathname.endsWith('/jobs/search') && url.searchParams.getAll('company').join('|') === '토스|ACME, Inc.';
  });
  await companies.getByLabel('ACME, Inc.', { exact: true }).click();
  await expect(companies.getByLabel('ACME, Inc.', { exact: true })).toBeChecked();
  const sent = new URL((await request).url());
  expect(sent.searchParams.get('techStack')).toBe('Java');
  expect(new URL(page.url()).searchParams.getAll('company')).toEqual(['토스', 'ACME, Inc.']);
  await page.reload();
  await openFilters(page);
  await expect(page.getByRole('button', { name: '토스 해제' })).toBeVisible();
  await page.getByRole('button', { name: 'ACME, Inc. 해제' }).click();
  expect(new URL(page.url()).searchParams.getAll('company')).toEqual(['토스']);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('회사 필터가 첫 번째이며 60개 이후 회사도 표시하고 검색한다', async ({ page }) => {
  const names = Array.from({ length: 75 }, (_, index) => `회사 ${String(index).padStart(3, '0')}`);
  await page.route('**/api/v1/jobs/companies**', route => route.fulfill({ json: names }));
  const catalogRequest = page.waitForRequest(req => new URL(req.url()).pathname.endsWith('/jobs/companies'));
  await page.goto('/jobs');
  expect(new URL((await catalogRequest).url()).searchParams.has('_fresh')).toBe(true);
  await openFilters(page);
  await expect(page.locator('.detail-filters .multi-filter').first().locator('summary')).toHaveAttribute('aria-label', '회사명');
  await page.getByLabel('회사명', { exact: true }).click();
  const menu = page.getByRole('group', { name: '회사명 선택' });
  await expect(menu.getByLabel('회사 000', { exact: true })).toBeVisible();
  await expect(menu.getByLabel('회사 074', { exact: true })).toHaveCount(0);
  await menu.getByRole('button', { name: '회사 더 보기' }).click();
  await expect(menu.getByLabel('회사 074', { exact: true })).toBeVisible();
  await menu.getByLabel('회사명 검색').fill('회사 074');
  await menu.getByLabel('회사 074', { exact: true }).click();
  await expect(menu.getByLabel('회사 074', { exact: true })).toBeChecked();
  expect(new URL(page.url()).searchParams.getAll('company')).toEqual(['회사 074']);
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
