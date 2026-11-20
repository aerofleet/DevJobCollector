import { expect, test } from '@playwright/test';

test('공고의 AI 직무 제안을 확인하고 원문 근거를 검토한 뒤 승인한다', async ({ page }) => {
  const writes = [];
  const job = { id: 13758, title: 'Manager, Selection Operations (Product Lifecycle Selection)', companyName: '쿠팡',
    sourcePlatform: 'GREENHOUSE', version: 3, moderationStatus: 'ACTIVE', active: true,
    endDate: '2030-12-31', originalUrl: 'https://example.com/job' };
  let review = null;
  await page.route('**/api/v1/admin/**', (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const reply = body => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    if (path.endsWith('/me')) return reply({ data: { id: 1, name: '관리자', role: 'ADMIN' } });
    if (path.endsWith('/jobs/13758')) return reply({ data: job });
    if (path.endsWith('/ai-role/history')) return reply(review ? [review] : []);
    if (path.endsWith('/ai-role/analyze')) {
      writes.push('analyze');
      review = { id: 4, job_version: 3, input_hash: 'a'.repeat(64), review_status: 'PENDING_REVIEW',
        suggested_role: '상품 운영 매니저', source_evidence: 'Lead and coach operators' };
      return reply({ status: 'STORED' });
    }
    if (path.endsWith('/ai-role/4/approve')) {
      writes.push(request.postDataJSON());
      review.review_status = 'APPROVED';
      return reply({ status: 'APPROVED' });
    }
    return reply({ data: { content: [job], totalElements: 1, totalPages: 1 } });
  });
  await page.goto('/jobs');
  await page.getByRole('button', { name: '상세', exact: true }).click();
  const section = page.getByRole('region', { name: 'AI 직무 제안 검토' });
  await section.getByRole('button', { name: 'AI 직무 분석' }).click();
  await expect(section.getByText('직무 제안: 상품 운영 매니저')).toBeVisible();
  await expect(section.getByText('원문 근거: Lead and coach operators')).toBeVisible();
  await section.getByRole('button', { name: '직무 승인' }).click();
  await expect(section.getByRole('status')).toContainText('직무 제안을 승인했습니다');
  expect(writes).toEqual(['analyze', { expectedVersion: 3, inputHash: 'a'.repeat(64) }]);
});
