import { expect, test } from '@playwright/test';

test('공고 전송 내용을 확인하고 동의한 후에만 AI 분석을 요청한다', async ({ page }) => {
  const writes = [];
  const job = { id: 42, title: '백엔드 개발자', companyName: '기업', sourcePlatform: 'GREENHOUSE', version: 3,
    moderationStatus: 'ACTIVE', active: true, endDate: '2030-12-31', originalUrl: 'https://example.com/job' };
  await page.route('**/api/v1/admin/**', (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const reply = (body) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    if (path.endsWith('/me')) return reply({ data: { id: 1, name: '관리자', role: 'ADMIN' } });
    if (path.endsWith('/preview')) return reply({ jobId: 42, version: 3, sourceKey: 'GREENHOUSE:example', sourceAllowed: true,
      inputHash: 'a'.repeat(64), content: '자격요건 개발 경력 3년 이상' });
    if (path.endsWith('/history')) return reply([]);
    if (path.endsWith('/analyze')) { writes.push(request.postDataJSON()); return reply({ status: 'NEEDS_REVIEW' }); }
    if (path.endsWith('/jobs/42')) return reply({ data: job });
    return reply({ data: { content: [job], totalElements: 1, totalPages: 1 } });
  });
  await page.goto('/jobs');
  await page.getByRole('button', { name: '상세', exact: true }).click();
  await page.getByRole('button', { name: '전송 내용 확인' }).click();
  await expect(page.getByLabel('AI에 전송할 공고 내용')).toHaveValue('자격요건 개발 경력 3년 이상');
  await expect(page.getByRole('button', { name: '확인하고 AI 분석' })).toBeDisabled();
  expect(writes).toHaveLength(0);
  await page.getByLabel('전송 내용과 출처의 이용 허용 범위를 확인했습니다.').check();
  await page.getByRole('button', { name: '확인하고 AI 분석' }).click();
  await expect(page.getByRole('status')).toContainText('AI 제안을 저장했습니다');
  expect(writes).toEqual([{ inputHash: 'a'.repeat(64), confirmed: true }]);
});

test('관리자가 근거를 검토하고 입력한 경력과 근무지만 반영한다', async ({ page }) => {
  const writes = [];
  const job = { id: 42, title: 'Sales Director', companyName: 'GitLab', sourcePlatform: 'GREENHOUSE', version: 3,
    moderationStatus: 'ACTIVE', active: true, endDate: '2030-12-31', originalUrl: 'https://example.com/job' };
  const review = { id: 7, job_version: 3, review_status: 'NEEDS_REVIEW', experience_suggestion: '5-10+ years',
    experience_quote: '5-10+ years of sales leadership', location_suggestion: 'New York', location_quote: 'Based in New York' };
  await page.route('**/api/v1/admin/**', (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const reply = (body) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    if (path.endsWith('/me')) return reply({ data: { id: 1, name: '관리자', role: 'ADMIN' } });
    if (path.endsWith('/preview')) return reply({ jobId: 42, version: 3, sourceKey: 'GREENHOUSE:gitlab', sourceAllowed: true,
      inputHash: 'a'.repeat(64), content: '5-10+ years of sales leadership. Based in New York.' });
    if (path.endsWith('/history')) return reply([review]);
    if (path.endsWith('/7/apply')) { writes.push(request.postDataJSON()); return reply({ status: 'APPLIED', version: 4 }); }
    if (path.endsWith('/jobs/42')) return reply({ data: job });
    return reply({ data: { content: [job], totalElements: 1, totalPages: 1 } });
  });
  await page.goto('/jobs');
  await page.getByRole('button', { name: '상세', exact: true }).click();
  await page.getByRole('button', { name: '전송 내용 확인' }).click();
  await page.getByText('이전 AI 제안 1건').click();
  await page.getByRole('button', { name: '원문 확인 후 조건 반영' }).click();
  await page.getByLabel('경력 분류').selectOption('EXPERIENCED');
  await page.getByLabel('경력 표시').fill('경력 5~10년 이상');
  await page.getByLabel('근무지 표시').fill('미국 뉴욕, 원격');
  await page.getByRole('button', { name: '확인한 조건 반영' }).click();
  await expect(page.getByRole('status')).toContainText('검색 필터에 반영했습니다');
  expect(writes).toEqual([{ expectedVersion: 3, experienceCategory: 'EXPERIENCED', experienceLabel: '경력 5~10년 이상', location: '미국 뉴욕, 원격' }]);
});
