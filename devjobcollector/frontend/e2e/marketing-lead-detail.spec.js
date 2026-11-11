import { expect, test } from '@playwright/test';

test('마케팅 공고의 업무·자격과 사무실 또는 지역 원격 조건을 표시한다', async ({ page }) => {
  await page.route('**/api/v1/jobs/12684**', route => route.fulfill({ json: {
    id: 12684, title: 'Marketing Lead', companyName: 'MongoDB', sourcePlatform: 'GREENHOUSE',
    experience: '경력 5년 이상', location: 'London', hireType: 'Full-time', endDate: '9999-12-31',
    originalUrl: 'https://www.mongodb.com/careers/job/?gh_jid=8367108', techStacks: [],
    applyQual: 'This role can be based out of our London office or remotely in the EMEA region.\nPosition Expectations\n• Run partner programs\nThe right candidate for this role will be:\n• Have 5+ years in growth marketing\nAbout MongoDB\nAvailable across AWS and Microsoft Azure.',
  } }));
  await page.goto('/job/12684/marketing-lead');
  const duties = page.locator('.detail-section').filter({ has: page.getByRole('heading', { name: '담당 업무' }) });
  const requirements = page.locator('.detail-section').filter({ has: page.getByRole('heading', { name: '자격 조건' }) });
  await expect(duties).toContainText('Run partner programs');
  await expect(duties).not.toContainText('5+ years');
  await expect(requirements).toContainText('5+ years in growth marketing');
  await expect(page.locator('.info-item').filter({ hasText: '근무 방식' })).toContainText('London 사무실 또는 EMEA 지역 원격');
  await expect(page.locator('.detail-section').filter({ has: page.getByRole('heading', { name: '기술 스택' }) })).not.toContainText('AWS');
});
