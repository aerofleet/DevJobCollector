import { expect, test } from '@playwright/test';

test('디자인 관리자 공고에 원문의 경력과 자격 조건을 표시한다', async ({ page }) => {
  await page.route('**/api/v1/jobs/6531**', route => route.fulfill({ json: {
    id: 6531, title: 'Senior Design Manager - Design Systems', companyName: 'Datadog',
    sourcePlatform: 'GREENHOUSE', jobCategory: 'Product Design', experience: '경력 확인 필요',
    location: 'New York, New York, USA', endDate: '9999-12-31', startDate: '2026-09-23',
    originalUrl: 'https://careers.datadoghq.com/detail/8223855/?gh_jid=8223855', techStacks: [],
    applyQual: 'Who You Are\n• You have 8+ years in product design, 3+ of them managing.\n• You read and write front-end code.\nAbout Datadog\nCompany description',
  } }));
  await page.goto('/job/6531');
  await expect(page.locator('.info-item').filter({ hasText: '경력' })).toContainText('8년 이상');
  const qualifications = page.locator('.detail-section').filter({ has: page.getByRole('heading', { name: '자격 조건' }) });
  await expect(qualifications).toContainText('8+ years in product design, 3+ of them managing');
  await expect(qualifications).toContainText('front-end code');
  await expect(qualifications).not.toContainText('Company description');
});
