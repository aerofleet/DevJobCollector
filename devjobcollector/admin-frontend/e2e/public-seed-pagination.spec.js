import { expect, test } from '@playwright/test';

test('공공 기업 총 건수와 페이지 이동 및 페이지 크기를 사용한다', async ({ page }) => {
  await page.route('**/api/v1/admin/me', route => route.fulfill({ json: { data: { id: 1, name: '관리자', role: 'ADMIN' } } }));
  await page.route('**/api/v1/admin/companies/discovery-candidates?*', route => route.fulfill({ json: { data: [] } }));
  await page.route('**/api/v1/admin/companies/public-seeds/paged?*', route => {
    const params = new URL(route.request().url()).searchParams;
    const size = Number(params.get('size')), requested = Number(params.get('page'));
    const totalElements = 137, totalPages = Math.ceil(totalElements / size), current = Math.min(requested, totalPages - 1);
    const items = Array.from({ length: Math.min(size, totalElements - current * size) }, (_, index) => ({
      id: current * size + index + 1, companyName: `공공기업 ${current * size + index + 1}`,
      source: 'KOSME_JOB_COMPANIES', status: 'DISCOVERED', version: 0,
    }));
    return route.fulfill({ json: { data: { items, page: current, size, totalElements, totalPages } } });
  });
  await page.goto('/discovery');
  const nav = page.getByRole('navigation', { name: '공공 기업 페이지' });
  await expect(nav).toContainText('전체 137개');
  await expect(nav).toContainText('1 / 7 페이지');
  await nav.getByRole('button', { name: '3페이지' }).click();
  await expect(page.getByRole('cell', { name: '공공기업 41', exact: true })).toBeVisible();
  await page.reload();
  await expect(nav).toContainText('3 / 7 페이지');
  await expect(page.getByRole('cell', { name: '공공기업 41', exact: true })).toBeVisible();
  await expect(nav.getByLabel('페이지 이동')).toHaveValue('3');
  await nav.getByLabel('페이지 이동').fill('7');
  await nav.getByRole('button', { name: '이동', exact: true }).click();
  await expect(page.getByRole('cell', { name: '공공기업 137', exact: true })).toBeVisible();
  await expect(nav.getByRole('button', { name: '다음 페이지' })).toBeDisabled();
  await nav.getByLabel('페이지 이동').fill('8');
  await nav.getByRole('button', { name: '이동', exact: true }).click();
  await expect(nav.getByRole('alert')).toContainText('1부터 7까지');
  await nav.getByLabel('페이지당').selectOption('50');
  await expect(nav).toContainText('1 / 3 페이지');
  await expect(page.getByRole('cell', { name: '공공기업 50', exact: true })).toBeVisible();
  await nav.getByRole('button', { name: '3페이지' }).click();
  await expect(page.getByRole('cell', { name: '공공기업 137', exact: true })).toBeVisible();
  await page.reload();
  await expect(nav).toContainText('3 / 3 페이지');
  await expect(nav.getByLabel('페이지당')).toHaveValue('50');
  await expect(page.getByRole('cell', { name: '공공기업 137', exact: true })).toBeVisible();
});

test('버전 충돌 시 현재 페이지에서 최신 Seed를 다시 확인한다', async ({ page }) => {
  let version = 0, discoveries = 0;
  await page.route('**/api/v1/admin/me', route => route.fulfill({ json: { data: { id: 1, name: '관리자', role: 'ADMIN' } } }));
  await page.route('**/api/v1/admin/companies/discovery-candidates?*', route => route.fulfill({ json: { data: [] } }));
  await page.route('**/api/v1/admin/companies/public-seeds/paged?*', route => route.fulfill({ json: { data: {
    page: 0, size: 20, totalElements: 1, totalPages: 1, items: [{ id: 1, companyName: '기업', status: 'DISCOVERED',
      source: 'KOSME_JOB_COMPANIES', homepageCandidateUrl: 'https://example.com', version }],
  } } }));
  await page.route('**/api/v1/admin/companies/public-seeds/1/ats-candidates?*', route => route.fulfill({ json: { data: [] } }));
  await page.route('**/api/v1/admin/companies/public-seeds/1/discover', route => {
    discoveries++;
    if (discoveries === 1) { version = 1; return route.fulfill({ status: 409, json: {} }); }
    expect(route.request().postDataJSON().expectedVersion).toBe(1);
    return route.fulfill({ json: { data: { candidateCount: 0 } } });
  });
  await page.goto('/discovery');
  await page.getByRole('button', { name: 'ATS 후보 확인' }).click();
  await page.getByRole('button', { name: '홈페이지에서 ATS 자동 검색' }).click();
  await expect(page.getByText(/인식 가능한 ATS 링크를 찾지 못했습니다/)).toBeVisible();
  expect(discoveries).toBe(2);
});
