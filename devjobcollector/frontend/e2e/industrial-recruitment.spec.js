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

test('당근 산업기능요원 공고의 업무와 자격 조건을 분리한다', async ({ page }) => {
  await page.route('**/api/v1/jobs/10600?*', route => route.fulfill({ json: { ...job, applyQual: '지원 전, 꼭 확인해 주세요!\n• 보충역 신규 편입\n이런 일을 해요\n• 백엔드 서비스를 개발해요\n• 대규모 트래픽을 개선해요\n이런 분을 찾고 있어요\n• 서비스를 운영해 본 경험이 있는 분\n이런 분이면 더 좋아요!\n• Kafka 경험\n참고해 주세요\n• 3개월 수습\n이렇게 합류해요\n1. 서류 전형 → 2. 인터뷰\n팀 동료들의 한마디\n산업기능요원 복무 후기' } }));
  await page.goto('/job/10600');
  const duties = page.getByRole('heading', { name: '담당 업무' }).locator('..');
  await expect(duties.locator('li')).toHaveCount(2);
  await expect(duties).toContainText('백엔드 서비스를 개발해요');
  for (const text of ['운영해 본 경험', 'Kafka 경험', '3개월 수습', '복무 후기']) await expect(duties).not.toContainText(text);
  await expect(page.getByRole('heading', { name: '자격 조건' }).locator('..')).toContainText('서비스를 운영해 본 경험');
  await expect(page.getByRole('heading', { name: '우대 사항' }).locator('..')).toContainText('Kafka 경험');
});
