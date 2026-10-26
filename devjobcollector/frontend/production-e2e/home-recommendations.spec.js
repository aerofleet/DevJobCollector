import { expect, test } from '@playwright/test';

test.use({ trace: 'off', screenshot: 'off' });

test('운영 로그인 홈은 분석 요청 없이 저장 이력서로 추천을 표시한다', async ({ page, request }, testInfo) => {
  test.skip(globalThis.process?.env.DJC_E2E_HOME_ENABLED !== 'true' || testInfo.project.name !== 'desktop-1440');
  const token = globalThis.process?.env.DJC_E2E_ACCESS_TOKEN;
  expect(Boolean(token)).toBe(true);
  const base = 'https://djc-api.itsdev.kr/api/v1';
  const headers = { Authorization: `Bearer ${token}` };
  let resumeId;
  try {
    const created = await request.post(`${base}/members/me/resumes`, { headers, data: {
      title: `HOME-E2E-${Date.now()}`,
      content: { basicInfo: {}, careerProfile: { category: ['백엔드'] }, techStack: ['Java', 'Spring Boot'], projects: [], experience: [] },
    } });
    expect(created.status()).toBe(201);
    resumeId = (await created.json()).id;
    await page.addInitScript(value => localStorage.setItem('accessToken', value), token);
    let analysisWrites = 0;
    page.on('request', req => { if (req.method() !== 'GET' && req.url().includes('/ai/resume-analyses')) analysisWrites++; });
    const responsePromise = page.waitForResponse(response => response.request().method() === 'GET'
      && new URL(response.url()).pathname.endsWith('/resume-analyses/home-recommendations'));
    await page.goto('/');
    const response = await responsePromise;
    expect(response.status()).toBe(200);
    const result = await response.json();
    expect(result).toMatchObject({ resumeId, inputMode: 'HOME_RESUME' });
    expect(result.id).toBeUndefined();
    expect(result.aiReview).toBeUndefined();
    expect(result.recommendations.length).toBeGreaterThan(0);
    expect(result.recommendations.length).toBeLessThanOrEqual(8);
    await expect(page.getByRole('heading', { name: /님을 위한 맞춤 AI 추천!/ })).toBeVisible();
    await expect(page.locator('.personal-job-grid article')).toHaveCount(result.recommendations.length);
    expect(analysisWrites).toBe(0);
    const history = await request.get(`${base}/ai/resume-analyses`, { headers });
    expect(history.status()).toBe(200);
    expect((await history.json()).some(item => item.resumeId === resumeId)).toBe(false);
  } finally {
    if (resumeId) {
      const deleted = await request.delete(`${base}/members/me/resumes/${resumeId}`, { headers });
      expect([200, 204, 404]).toContain(deleted.status());
      expect((await request.get(`${base}/members/me/resumes/${resumeId}`, { headers })).status()).toBe(404);
    }
  }
});
