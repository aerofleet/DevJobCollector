import { expect, test } from '@playwright/test';

const member = { id: 42, name: '테스트회원', email: 'member@example.com', role: 'USER' };

for (const [path, moduleName, heading] of [
  ['/my-devjobs', 'MyDevJobsPage', '마이데브잡'],
  ['/resumes', 'ResumesPage', '이력서 관리'],
]) {
  test(`${path}: 인증 대기 중 화면 코드 준비, 인증 요청 중복 방지`, async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('accessToken', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkaW5nLXRlc3QiLCJleHAiOjQxMDI0NDQ4MDB9.test-signature'));
    let release;
    const gate = new Promise((resolve) => { release = resolve; });
    let authRequests = 0;
    await page.route('**/api/v1/**', async (route) => {
      if (new URL(route.request().url()).pathname === '/api/v1/members/me') {
        authRequests += 1;
        await gate;
        return route.fulfill({ json: member });
      }
      return route.fulfill({ json: [] });
    });
    const code = page.waitForResponse((response) => response.url().includes(`/pages/${moduleName}.jsx`));
    try {
      await page.goto(path);
      await code;
      await expect(page.getByText('회원 정보를 불러오는 중입니다.')).toBeVisible();
      await expect(page.getByRole('heading', { name: heading, exact: true })).toHaveCount(0);
      expect(authRequests).toBe(1);
    } finally { release(); }
    await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
  });
}

test('느린 최근 공고와 지원 API 오류가 저장 목록을 막지 않음, 실패 탭만 재시도', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('accessToken', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkaW5nLXRlc3QiLCJleHAiOjQxMDI0NDQ4MDB9.test-signature'));
  let release;
  const gate = new Promise((resolve) => { release = resolve; });
  const counts = { bookmarks: 0, applications: 0 };
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/me')) return route.fulfill({ json: member });
    if (path.endsWith('/bookmarks')) {
      counts.bookmarks += 1;
      return route.fulfill({ json: [{ jobPostId: 1, title: '즉시 표시할 저장 공고', companyName: '테스트' }] });
    }
    if (path.endsWith('/applications')) {
      counts.applications += 1;
      return route.fulfill(counts.applications === 1 ? { status: 500, json: {} } : { json: [] });
    }
    if (path.endsWith('/recent-jobs')) await gate;
    return route.fulfill({ json: [] });
  });
  try {
    await page.goto('/my-devjobs');
    await expect(page.getByRole('heading', { name: '즉시 표시할 저장 공고' })).toBeVisible();
    await page.getByRole('tab', { name: '지원 현황' }).click();
    await expect(page.getByRole('alert')).toBeVisible();
    await page.getByRole('button', { name: '다시 시도', exact: true }).click();
    await expect(page.getByRole('heading', { name: '진행 중인 지원 내역이 없어요.' })).toBeVisible();
    expect(counts).toEqual({ bookmarks: 1, applications: 2 });
    await page.getByRole('tab', { name: '최근 본 공고' }).click();
    await expect(page.getByText('채용 활동을 불러오는 중입니다.')).toBeVisible();
  } finally { release(); }
  await expect(page.getByRole('heading', { name: '최근 확인한 공고가 없어요.' })).toBeVisible();
});
