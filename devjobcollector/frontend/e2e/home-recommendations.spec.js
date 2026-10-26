import { expect, test } from '@playwright/test';

const token = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjo0MTAyNDQ0ODAwfQ.test';
const history = [8, 7].map(id => ({ id, status: 'COMPLETED_LOCAL', resumeId: 11, resumeTitle: `이력서 ${id}`, createdAt: '2026-10-24' }));
const job = { jobId: 17, title: '백엔드 엔지니어', companyName: '합성 기업', location: '서울', experience: '1~3년', endDate: '2026-12-31', matchPoints: ['Java', 'Spring Boot'], missingPoints: [] };
const result = id => ({ id, resumeId: 11, createdAt: '2026-10-24', detectedSkills: ['java'], derivedPreferences: {}, improvements: [], recommendations: [job],
  review: { findings: [], observations: {}, score: {} },
  aiReview: id === 8 ? { status: 'COMPLETED', findings: [], strengths: [], gaps: [], jobs: [{ jobId: 17, relevance: 'RELATED', lead_with: 'API 운영 경험을 앞세우세요.', gap: '담당 범위를 확인하세요.', criteria: [] }] } : null });

async function mock(page, { loggedIn = true, empty = false, fail = false, reason = null, supplemented = false, preview = false } = {}) {
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
      return route.fulfill({ json: empty || preview ? [] : history });
    }
    if (/\/resume-analyses\/\d+$/.test(path)) return route.fulfill({ json: { ...result(Number(path.split('/').pop())), recommendations: state.hidden ? [] : [job] } });
    if (path.endsWith('/recommendations') || path.endsWith('/home-recommendations')) {
      if (empty) return route.fulfill({ json: { recommendations: [], recommendationStatus: { reason: 'NO_RESUME' } } });
      const analysisId = path.endsWith('/home-recommendations') ? 8 : Number(path.split('/').at(-2));
      return route.fulfill({ json: {
      ...result(analysisId), ...(preview ? { id: undefined, inputMode: 'HOME_RESUME' } : {}),
      recommendations: state.hidden || reason ? [] : preview ? Array.from({ length: 8 }, (_, index) => ({ ...job, jobId: 17 + index, supplemented: true })) : [{ ...job, supplemented }],
      excludedRecommendations: state.hidden ? [job] : [],
      aiReview: supplemented || preview ? null : result(analysisId).aiReview,
      recommendationStatus: { reason: reason || (state.hidden ? 'ALL_EXCLUDED' : 'AVAILABLE'), roleSource: 'RESUME_TITLE', categories: ['백엔드'] },
    } }); }
    if (path.endsWith('/recommendations/feedback')) {
      expect(req.postDataJSON().jobId).toBe(17);
      state.hidden = req.postDataJSON().type !== 'MATCHED';
      return route.fulfill({ status: 204 });
    }
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
  await expect(page.locator('.recommendation-login-banner')).toHaveAttribute('href', '/login?next=%2Fresume-analysis');
  await expect(page.locator('.recommendation-login-banner')).toContainText('회원가입/로그인');
  expect(state.privateCalls).toBe(0); expect(state.writes).toBe(0);
});

test('추천 근거·이력서 전환·제외 저장·모바일 배치', async ({ page }) => {
  const state = await mock(page);
  await page.goto('/');
  const section = page.getByRole('region', { name: '내 이력서로 찾는 추천 포지션' });
  await section.getByText('추천 근거 보기', { exact: true }).click();
  await expect(section.getByText('API 운영 경험을 앞세우세요.')).toBeVisible();
  expect(state.writes).toBe(0);
  await expect(section.getByText('마감일: 2026.12.31')).toBeVisible();
  await expect(section.locator('.job-card')).toHaveAttribute('href', '/job/17');
  await expect(section.getByRole('link', { name: '이력서 분석 · AI 코칭' })).toHaveCount(0);
  await expect(section.getByRole('link', { name: '현재 이력서로 새로 분석 →' })).toHaveCount(0);
  await section.getByText('추천 기준 변경', { exact: true }).click();
  await section.getByLabel('추천 기준 이력서').selectOption('7');
  await section.getByText('추천 근거 보기', { exact: true }).click();
  await expect(section.getByText('기술·경력 기반', { exact: true })).toBeVisible();
  await expect(section.getByText('AI 검토', { exact: true })).toHaveCount(0);
  await section.getByLabel('추천 기준 이력서').selectOption('8');
  await section.getByText('추천 근거 보기', { exact: true }).click();
  await expect(section.getByText('API 운영 경험을 앞세우세요.')).toBeVisible();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: `.codex/recommendations-${test.info().project.name}.png`, fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await section.getByRole('button', { name: '백엔드 엔지니어 추천 제외' }).click();
  await expect(section.getByText('현재 표시할 추천 공고가 없어요.')).toBeVisible();
  await page.reload();
  await expect(section.getByText('현재 표시할 추천 공고가 없어요.')).toBeVisible();
  await expect(section.getByText('조건에 맞는 현재 공고를 모두 제외했어요.', { exact: false })).toBeVisible();
  await section.getByText('제외한 공고 1개').click();
  await section.getByRole('button', { name: '백엔드 엔지니어 제외 해제' }).click();
  await expect(section.getByRole('button', { name: '백엔드 엔지니어 추천 제외' })).toBeVisible();
});

test('보충 공고는 AI 검토로 표시하지 않고 공고로 직접 연결한다', async ({ page }) => {
  await mock(page, { supplemented: true });
  await page.goto('/');
  const section = page.getByRole('region', { name: '내 이력서로 찾는 추천 포지션' });
  await expect(section.locator('.job-card')).toHaveAttribute('href', '/job/17');
  await section.getByText('추천 근거 보기', { exact: true }).click();
  await expect(section.getByText('일치 조건: Java, Spring Boot')).toBeVisible();
  await expect(section.getByText('AI 검토', { exact: true })).toHaveCount(0);
  await expect(section.getByRole('link', { name: '추천 기준 이력서 확인' })).toHaveAttribute('href', '/resume?resumeId=11');
});

for (const [reason, message] of [['MISSING_CRITERIA', '추천 기준이 부족해요.'], ['NO_CURRENT_MATCHES', '현재 조회한 공고 중 기술 또는 직무가 연결되는 공고를 찾지 못했어요.'], ['SAVED_JOBS_UNAVAILABLE', '저장된 추천 공고가 마감되거나 공개가 중단됐어요.']]) {
  test(`추천 0건 사유 구분: ${reason}`, async ({ page }) => {
    await mock(page, { reason });
    await page.goto('/');
    await expect(page.getByText(message, { exact: false })).toBeVisible();
  });
}

test('조회 오류 재시도와 분석 없는 회원 안내', async ({ page }) => {
  const state = await mock(page, { fail: true, empty: true });
  await page.goto('/member');
  await expect(page.getByRole('alert')).toContainText('추천을 불러오지 못했어요');
  state.fail = false;
  await page.getByRole('button', { name: '다시 시도', exact: true }).click();
  await expect(page.getByRole('link', { name: '이력서 작성하고 추천받기' })).toBeVisible();
  expect(state.writes).toBe(0);
});

test('로그인 후 분석 없이 이름과 저장 이력서 추천이 테마보다 먼저 표시된다', async ({ page }) => {
  const state = await mock(page, { preview: true, supplemented: true });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: '테스트님을 위한 맞춤 추천!' })).toBeVisible();
  const section = page.getByRole('region', { name: '내 이력서로 찾는 추천 포지션' });
  await expect(section.locator('.job-card').first()).toBeVisible();
  await expect(section.locator('.personal-job-grid article')).toHaveCount(8);
  await expect(section.getByRole('link', { name: '이 이력서로 AI 코칭 받기' })).toHaveAttribute('href', '/resume-analysis?resumeId=11');
  await expect(section.getByText('AI 검토', { exact: true })).toHaveCount(0);
  await expect(section.getByRole('button', { name: /추천 제외/ })).toHaveCount(0);
  expect(await section.evaluate(node => Boolean(node.compareDocumentPosition(document.querySelector('.discovery-section')) & Node.DOCUMENT_POSITION_FOLLOWING))).toBe(true);
  expect(state.writes).toBe(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: `.codex/home-immediate-${test.info().project.name}.png`, fullPage: true });
});

test('추천 링크는 선택한 저장 분석을 직접 연다', async ({ page }) => {
  const state = await mock(page);
  await page.goto('/resume-analysis?analysisId=8');
  await expect(page.getByRole('heading', { name: '맞춤 공고 + 공고별 어필법' })).toBeVisible();
  await expect(page.getByRole('link', { name: '백엔드 엔지니어', exact: true })).toBeVisible();
  expect(state.writes).toBe(0);
});

test('상시채용 마감일은 공통 카드 형식으로 표시한다', async ({ page }) => {
  await mock(page);
  await page.route('**/home-recommendations', route => route.fulfill({ json: { ...result(8), recommendations: [{ ...job, endDate: '9999-12-31' }] } }));
  await page.goto('/');
  const card = page.locator('.personal-job-grid .job-card');
  await expect(card.getByText('채용시마감', { exact: true })).toBeVisible();
  await expect(card.getByText('마감일: 채용 시 마감', { exact: true })).toBeVisible();
  await expect(card).not.toContainText('9999');
});

test('AI 적합성 판정과 보류 근거를 구분해 표시한다', async ({ page }) => {
  await mock(page);
  await page.route('**/home-recommendations', route => route.fulfill({ json: {
    ...result(8), recommendations: [{ ...job, aiDecision: 'STRETCH', aiFit: { decision: 'STRETCH', decisionReason: 'API 경험은 연결되지만 운영 범위는 확인이 필요합니다.', criteria: [] } }],
    aiExcludedRecommendations: [{ ...job, jobId: 99, title: 'Senior Software Engineer', aiDecision: 'NOT_RECOMMENDED', aiFit: { decisionReason: '공고의 운영 책임과 이력서 경력 간 차이가 큽니다.', criteria: [] } }],
    recommendationStatus: { reason: 'AVAILABLE', aiFiltered: true, aiPendingCount: 1 },
  } }));
  await page.goto('/');
  const section = page.getByRole('region', { name: '내 이력서로 찾는 추천 포지션' });
  await expect(section.locator('.personal-job-grid article')).toHaveCount(1);
  await section.getByText('추천 근거 보기', { exact: true }).click();
  await expect(section.getByText('도전 지원', { exact: true })).toBeVisible();
  await section.getByText('AI 검토로 보류한 공고 1개').click();
  await expect(section.getByText('부적합: 공고의 운영 책임과 이력서 경력 간 차이가 큽니다.')).toBeVisible();
  await expect(section.getByText('AI 적합성 검토 전까지 추천을 보류했어요.', { exact: false })).toBeVisible();
});

test('시니어 공고의 최초 경력 검토 대기를 설명한다', async ({ page }) => {
  await mock(page);
  await page.route('**/home-recommendations', route => route.fulfill({ json: {
    ...result(8), recommendations: [],
    recommendationStatus: { reason: 'AI_REVIEW_REQUIRED', aiFiltered: false, aiPendingCount: 1 },
  } }));
  await page.goto('/');
  const section = page.getByRole('region', { name: '내 이력서로 찾는 추천 포지션' });
  await expect(section.locator('.personal-job-grid article')).toHaveCount(0);
  await expect(section.getByText('Senior·시니어·Vice President·Director·Manager 등 경력과 책임 범위 검토가 필요한 공고이거나', { exact: false })).toBeVisible();
  await expect(section.getByText('공고 1개는 AI 적합성 검토 전까지', { exact: false })).toBeVisible();
});
