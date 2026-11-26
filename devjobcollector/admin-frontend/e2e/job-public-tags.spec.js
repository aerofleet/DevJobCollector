import { expect, test } from '@playwright/test';

test('공고 상세에 공개 카드 태그를 표시하고 공개되지 않은 공고를 구분한다', async ({ page }) => {
  const job = { id: 1, title: 'Product Designer (인턴) - 커머스', companyName: '당근',
    sourcePlatform: 'GREENHOUSE', moderationStatus: 'ACTIVE', active: true, version: 1,
    endDate: '2099-12-31', originalUrl: 'https://example.com/job' };
  const hiddenJob = { id: 2, title: '공개채용', companyName: '기관', sourcePlatform: 'PUBLIC_ALIO',
    moderationStatus: 'HIDDEN', jobCategory: '경비.청소, 경비', endDate: '2099-12-31' };
  let visible = true;
  await page.route('**/api/v1/admin/**', route => {
    const path = new URL(route.request().url()).pathname;
    const data = path.endsWith('/me') ? { id: 1, role: 'ADMIN' }
      : path.endsWith('/jobs/1') ? job : { content: [job, hiddenJob], totalElements: 2, totalPages: 1 };
    return route.fulfill({ json: { data } });
  });
  await page.route('**/api/v1/jobs/1**', route => visible
    ? route.fulfill({ json: { ...job, approvedJobRole: 'UX·UI 디자인', techStacks: [], mentionedTechStacks: [] } })
    : route.fulfill({ status: 404 }));
  await page.goto('/jobs');
  await expect(page.getByRole('columnheader', { name: '직무 태그' })).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: job.title })).toContainText('UX·UI 디자인');
  const hiddenRow = page.getByRole('row').filter({ hasText: hiddenJob.title });
  await expect(hiddenRow.getByText('경비', { exact: true })).toHaveCount(1);
  await expect(hiddenRow.getByText('청소', { exact: true })).toBeVisible();
  await page.getByRole('row').filter({ hasText: job.title }).getByRole('button', { name: '상세' }).click();
  const tags = page.getByRole('region', { name: '사용자 화면 태그' });
  await expect(tags).toContainText('직무: UX·UI 디자인');
  await page.getByRole('button', { name: '공고 상세 닫기' }).click();
  visible = false;
  await page.getByRole('row').filter({ hasText: job.title }).getByRole('button', { name: '상세' }).click();
  await expect(tags).toContainText('현재 공개되지 않은 공고입니다.');
});
