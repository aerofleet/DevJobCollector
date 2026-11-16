import { expect, test } from '@playwright/test';

test('홈 개발자 공고에서 기관명과 비개발 직무를 제외한다', async ({ page }) => {
  await page.route('**/api/v1/jobs/search**', route => route.fulfill({ json: { content: [
    { id: 13142, title: '우체국금융개발원 2026년 10차 공개채용 공고', companyName: '(재)우체국금융개발원', jobCategory: '금융.보험,경비.청소', techStacks: [{ stackName: '보험' }] },
    { id: 1, title: '사업개발 담당자', companyName: '테스트', jobCategory: '사업개발', techStacks: [] },
    { id: 2, title: '백엔드 서버 개발', companyName: '테스트', jobCategory: 'Backend', techStacks: [] },
  ] } }));
  await page.goto('/');
  const section = page.locator('section').filter({ has: page.getByRole('heading', { name: '오늘의 개발자 채용공고' }) });
  await expect(section.getByText('백엔드 서버 개발')).toBeVisible();
  await expect(section.getByText('우체국금융개발원 2026년 10차 공개채용 공고')).toHaveCount(0);
  await expect(section.getByText('사업개발 담당자')).toHaveCount(0);
});
