import { expect, test } from '@playwright/test';

const job = { id: 10600, title: 'Software Engineer, Backend', companyName: '당근', sourcePlatform: 'COMPANY_PAGE', experience: '신입/경력', location: '서울', techStacks: [], industrialPersonnelRecruitment: true, applyQual: '지원 가능 유형은 보충역 신규 편입, 보충역 전직, 현역 전직이에요.\n산업기능요원도 동료입니다.' };

test('산업기능요원 공고는 목록과 상세에서 구별하고 일반 공고에는 표시하지 않는다', async ({ page }) => {
  await page.route('**/api/v1/jobs/search**', route => route.fulfill({ json: { content: [job, { ...job, id: 2, title: '일반 공고', industrialPersonnelRecruitment: false }], totalElements: 2, totalPages: 1 } }));
  await page.route('**/api/v1/jobs/companies**', route => route.fulfill({ json: [] }));
  await page.route('**/api/v1/jobs/10600?*', route => route.fulfill({ json: job }));
  await page.goto('/jobs');
  await expect(page.locator('.job-card').filter({ hasText: 'Software Engineer, Backend' })).toContainText('산업기능요원 모집');
  await expect(page.locator('.job-card').filter({ hasText: '일반 공고' })).not.toContainText('산업기능요원 모집');
  await page.locator('.job-card').filter({ hasText: 'Software Engineer, Backend' }).click();
  await expect(page.getByText('산업기능요원 모집 공고입니다.', { exact: false })).toBeVisible();
  await expect(page.getByText('지원 가능한 편입·전직 유형은 공고 원문을 확인해 주세요.', { exact: false })).toBeVisible();
});
