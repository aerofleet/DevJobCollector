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
