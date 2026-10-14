import { expect, test } from '@playwright/test';

const company = { companyId: 77, displayName: '테스트 기업', legalName: '테스트 기업', companyStatus: 'VERIFIED', role: 'OWNER', membershipStatus: 'ACTIVE' };
const content = { title: '백엔드 개발자', jobCategory: 'backend', experience: '신입', location: '서울', hireType: '정규직', techStacks: 'Java,Spring', description: '함께 개발할 동료를 찾습니다.', endDate: '2030-12-31', applyUrl: 'https://example.com/jobs' };
const reply = (route, data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) });

async function setup(page, overrides = {}, initial = []) {
  const state = { jobs: initial, writes: [], submissions: [] };
  await page.addInitScript(() => localStorage.setItem('accessToken', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJvd25lci10b2tlbiIsImV4cCI6NDEwMjQ0NDgwMH0.test-signature'));
  await page.route('**/api/v1/**', (route) => {
    const req = route.request(), path = new URL(req.url()).pathname;
    if (path === '/api/v1/members/me') return reply(route, { id: 42, name: '담당자', email: 'owner@example.com', role: 'USER', profileStatus: 'ACTIVE' });
    if (path === '/api/v1/companies/me') return reply(route, [{ ...company, ...overrides }]);
    if (path === '/api/v1/companies/77/jobs' && req.method() === 'GET') return reply(route, { content: state.jobs, totalPages: 1 });
    if (path === '/api/v1/companies/77/jobs' && req.method() === 'POST') {
      const job = { id: 1, content: req.postDataJSON(), state: 'DRAFT', liveStatus: 'UNPUBLISHED', version: 0, views: 0, applyClicks: 0 };
      state.writes.push(job.content); state.jobs = [job]; return reply(route, job);
    }
    if (path === '/api/v1/companies/77/jobs/1' && req.method() === 'PUT') {
      const update = req.postDataJSON();
      state.writes.push(update.content);
      state.jobs[0] = { ...state.jobs[0], content: update.content, version: state.jobs[0].version + 1 };
      return reply(route, state.jobs[0]);
    }
    if (path.endsWith('/submit')) { state.submissions.push(req.postDataJSON()); state.jobs[0] = { ...state.jobs[0], state: 'IN_REVIEW', version: state.jobs[0].version + 1 }; return reply(route, state.jobs[0]); }
    if (path.endsWith('/close')) { state.jobs[0] = { ...state.jobs[0], state: 'CLOSED', liveStatus: 'CLOSED', version: 2 }; return reply(route, state.jobs[0]); }
    return reply(route, {});
  });
  return state;
}
test('기업 공고 작성 미리보기 임시저장 검수요청 마감', async ({ page }) => {
  const state = await setup(page);
  await page.goto('/company');
  await page.getByRole('button', { name: '새 공고 작성' }).click();
  for (const [key, value] of Object.entries(content)) await page.locator(`#cj-${key}`).fill(value);
  await page.getByRole('button', { name: '미리보기', exact: true }).click();
  await expect(page.getByRole('article', { name: '공고 미리보기' })).toContainText(content.description);
  await page.screenshot({ path: `.codex/company-job-editor-${test.info().project.name}.png`, fullPage: true });
  await page.getByRole('button', { name: '임시저장', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('임시저장했습니다');
  expect(state.writes[0]).toEqual(content);
  await expect(page.getByRole('button', { name: '편집 닫기' })).toBeVisible();
  await expect(page.getByRole('button', { name: '검수 요청', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: '검수 요청', exact: true }).click();
  await expect(page.locator('.company-job-status')).toContainText('검수 대기');
  await page.getByRole('button', { name: '공고 마감', exact: true }).click();
  await expect(page.locator('.company-job-status')).toContainText('마감');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: `.codex/company-jobs-${test.info().project.name}.png`, fullPage: true });
});

test('저장된 초안은 편집 중에도 제출 가능하고 미저장 변경은 저장 후 최신 버전으로 제출한다', async ({ page }) => {
  const state = await setup(page, {}, [{ id: 1, content, state: 'DRAFT', liveStatus: 'UNPUBLISHED', version: 3, views: 0, applyClicks: 0 }]);
  await page.goto('/company');
  await page.getByRole('button', { name: '수정', exact: true }).click();
  const submit = page.getByRole('button', { name: '검수 요청', exact: true });
  await expect(submit).toBeEnabled();
  await page.locator('#cj-title').fill('수정한 공고');
  await expect(submit).toBeDisabled();
  await expect(page.locator('#company-job-submit-help')).toBeVisible();
  expect(state.submissions).toHaveLength(0);
  await page.locator('#cj-title').fill(content.title);
  await expect(submit).toBeEnabled();
  await page.locator('#cj-title').fill('수정한 공고');
  await page.getByRole('button', { name: '임시저장', exact: true }).click();
  await expect(submit).toBeEnabled();
  await expect(page.locator('#company-job-submit-help')).toHaveCount(0);
  await submit.click();
  await expect(page.locator('.company-job-status')).toContainText('검수 대기');
  expect(state.submissions).toEqual([{ expectedVersion: 4 }]);
  expect(state.jobs[0].content.title).toBe('수정한 공고');
});
test('VIEWER는 목록만 조회한다', async ({ page }) => {
  await setup(page, { role: 'VIEWER' }); await page.goto('/company');
  await expect(page.getByText('조회 권한입니다.', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: '새 공고 작성' })).toHaveCount(0);
});

test('삭제된 공고는 사유를 확인할 수 있지만 수정 복사 제출 마감할 수 없다', async ({ page }) => {
  await setup(page, {}, [{ id: 1, content, state: 'DELETED', liveStatus: 'HIDDEN', version: 4, reviewReason: '기업 요청', views: 0, applyClicks: 0 }]);
  await page.goto('/company');
  await expect(page.locator('.company-job-status')).toContainText('삭제됨');
  await expect(page.getByText('처리 사유: 기업 요청')).toBeVisible();
  for (const name of ['수정', '복사', '검수 요청', '공고 마감']) await expect(page.getByRole('button', { name, exact: true })).toHaveCount(0);
});
test('미인증 기업은 공고 작성 기능을 노출하지 않는다', async ({ page }) => {
  await setup(page, { companyStatus: 'PENDING_VERIFICATION' }); await page.goto('/company');
  await expect(page.getByRole('heading', { name: '기업 인증 요청', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '새 공고 작성' })).toHaveCount(0);
});
test('공고 복사는 새 초안을 생성하며 기존 공고를 수정하지 않는다', async ({ page }) => {
  const original = { id: 8, content, state: 'PUBLISHED', liveStatus: 'ACTIVE', version: 3, views: 20, applyClicks: 2 };
  const state = await setup(page, {}, [original]); await page.goto('/company');
  await page.getByRole('button', { name: '복사', exact: true }).click();
  await expect(page.locator('#cj-title')).toHaveValue(content.title);
  await page.getByRole('button', { name: '임시저장', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('임시저장했습니다');
  expect(state.writes).toHaveLength(1); expect(original.version).toBe(3);
});
