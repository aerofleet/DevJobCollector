import { expect, test } from '@playwright/test';

test('알리오 공고의 AI 직무 분야와 원문을 검토한다', async ({ page }) => {
  const writes = [];
  const job = { id: 42, title: '전산 연구원 채용', companyName: '공공기관', sourcePlatform: 'PUBLIC_ALIO',
    version: 3, moderationStatus: 'ACTIVE', active: true, endDate: '2030-12-31', originalUrl: 'https://example.com/job' };
  let review = null;
  await page.route('**/api/v1/admin/**', route => {
    const request = route.request(); const path = new URL(request.url()).pathname;
    const reply = body => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    if (path.endsWith('/me')) return reply({ data: { id: 1, name: '관리자', role: 'ADMIN' } });
    if (path.endsWith('/jobs/42')) return reply({ data: job });
    if (path.endsWith('/ai-fields/history')) return reply(review ? [review] : []);
    if (path.endsWith('/ai-fields/analyze')) {
      writes.push('analyze');
      review = { id: 5, job_version: 3, input_hash: 'a'.repeat(64), review_status: 'PENDING_REVIEW',
        fields_json: JSON.stringify({ fields: [{ field: '전산직', evidence: '시스템 개발' }] }) };
      return reply({ status: 'STORED' });
    }
    if (path.endsWith('/ai-fields/5/approve')) { writes.push(request.postDataJSON()); review.review_status = 'APPROVED'; return reply({ status: 'APPROVED' }); }
    return reply({ data: { content: [job], totalElements: 1, totalPages: 1 } });
  });
  await page.goto('/jobs');
  await page.getByRole('button', { name: '상세', exact: true }).click();
  const section = page.getByRole('region', { name: 'AI 직무 분야 검토' });
  await section.getByRole('button', { name: 'AI 직무 분야 분석' }).click();
  await expect(section.getByText('분야: 전산직 · 원문 근거: 시스템 개발')).toBeVisible();
  await section.getByRole('button', { name: '분야 승인' }).click();
  await expect(section.getByRole('status')).toContainText('직무 분야를 승인했습니다');
  expect(writes).toEqual(['analyze', { expectedVersion: 3, inputHash: 'a'.repeat(64) }]);
});
