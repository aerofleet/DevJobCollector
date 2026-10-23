import { expect, test } from '@playwright/test';

const token = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjo0MTAyNDQ0ODAwfQ.test';
const history = [8, 7].map(id => ({ id, status: 'COMPLETED_LOCAL', resumeId: 11, resumeTitle: `이력서 ${id}`, createdAt: '2026-10-24' }));
const job = { jobId: 17, title: '백엔드 엔지니어', companyName: '합성 기업', location: '서울', experience: '1~3년', endDate: '2026-12-31', matchPoints: ['Java', 'Spring Boot'], missingPoints: [] };
const result = id => ({ id, resumeId: 11, createdAt: '2026-10-24', detectedSkills: ['java'], derivedPreferences: {}, improvements: [], recommendations: [job],
  review: { findings: [], observations: {}, score: {} },
  aiReview: id === 8 ? { status: 'COMPLETED', findings: [], strengths: [], gaps: [], jobs: [{ jobId: 17, relevance: 'RELATED', lead_with: 'API 운영 경험을 앞세우세요.', gap: '담당 범위를 확인하세요.', criteria: [] }] } : null });

async function mock(page, { loggedIn = true, empty = false, fail = false } = {}) {
  if (loggedIn) await page.addInitScript(value => localStorage.setItem('accessToken', value), token);
  const state = { hidden: false, fail, privateCalls: 0, writes: 0 };
  await page.route('**/api/v1/**', route => {
    const req = route.request(); const path = new URL(req.url()).pathname;
    if (path.includes('/ai/')) state.privateCalls++;
    if (req.method() !== 'GET') state.writes++;
    if (path.endsWith('/members/me')) return route.fulfill({ json: { id: 1, name: '테스트', role: 'USER', profileStatus: 'ACTIVE' } });
    if (path.endsWith('/capabilities')) return route.fulfill({ json: { contentReviewAvailable: true, consentVersion: 'local-analysis-v3', dailyLimit: 5 } });
    if (path.endsWith('/resume-analyses')) {
      if (state.fail) return route.fulfill({ status: 500, json: {} });
      return route.fulfill({ json: empty ? [] : history });
    }
    if (/\/resume-analyses\/\d+$/.test(path)) return route.fulfill({ json: { ...result(Number(path.split('/').pop())), recommendations: state.hidden ? [] : [job] } });
    if (path.endsWith('/feedback')) { expect(req.postDataJSON()).toEqual({ jobId: 17, type: 'NOT_INTERESTED' }); state.hidden = true; return route.fulfill({ status: 204 }); }
    if (path.endsWith('/resumes')) return route.fulfill({ json: [{ id: 11, title: '내 이력서' }] });
    if (path.endsWith('/content-review')) return route.fulfill({ json: result(8).aiReview });
    if (path.includes('/jobs')) return route.fulfill({ json: { content: [], totalElements: 0 } });
    return route.fulfill({ json: [] });
  });
  return state;
}

test('공개 홈은 개인 API나 모델 호출 없이 추천 시작 안내를 표시한다', async ({ page }) => {
  const state = await mock(page, { loggedIn: false });
  await page.goto('/');
  await expect(page.getByRole('link', { name: '로그인하고 추천 시작하기' })).toHaveAttribute('href', '/login?next=%2Fresume-analysis');
  expect(state.privateCalls).toBe(0); expect(state.writes).toBe(0);
});

test('추천 근거·이력서 전환·제외 저장·모바일 배치', async ({ page }) => {
  const state = await mock(page);
  await page.goto('/');
  const section = page.getByRole('region', { name: '내 이력서로 찾는 추천 포지션' });
  await expect(section.getByText('API 운영 경험을 앞세우세요.')).toBeVisible();
  expect(state.writes).toBe(0);
  await expect(section.getByText('마감 2026-12-31')).toBeVisible();
  await expect(section.getByRole('link', { name: '추천 근거 보기' })).toHaveAttribute('href', '/resume-analysis?analysisId=8');
  await section.getByLabel('추천 기준 이력서').selectOption('7');
  await expect(section.getByText('기술·경력 기반', { exact: true })).toBeVisible();
  await expect(section.getByText('AI 검토', { exact: true })).toHaveCount(0);
  await section.getByLabel('추천 기준 이력서').selectOption('8');
  await expect(section.getByText('API 운영 경험을 앞세우세요.')).toBeVisible();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: `.codex/recommendations-${test.info().project.name}.png`, fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await section.getByRole('button', { name: '백엔드 엔지니어 추천 제외' }).click();
  await expect(section.getByText('현재 표시할 추천 공고가 없어요.')).toBeVisible();
  await page.reload();
  await expect(section.getByText('현재 표시할 추천 공고가 없어요.')).toBeVisible();
});

test('조회 오류 재시도와 분석 없는 회원 안내', async ({ page }) => {
  const state = await mock(page, { fail: true, empty: true });
  await page.goto('/member');
  await expect(page.getByRole('alert')).toContainText('추천을 불러오지 못했어요');
  state.fail = false;
  await page.getByRole('button', { name: '다시 시도', exact: true }).click();
  await expect(page.getByRole('link', { name: '이력서 선택하고 추천받기' })).toBeVisible();
  expect(state.writes).toBe(0);
});

test('추천 링크는 선택한 저장 분석을 직접 연다', async ({ page }) => {
  const state = await mock(page);
  await page.goto('/resume-analysis?analysisId=8');
  await expect(page.getByRole('heading', { name: '맞춤 공고 + 공고별 어필법' })).toBeVisible();
  await expect(page.getByRole('link', { name: '백엔드 엔지니어', exact: true })).toBeVisible();
  expect(state.writes).toBe(0);
});
