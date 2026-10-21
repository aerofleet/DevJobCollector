import { expect, test } from '@playwright/test';

test('분석 동의·재시도 키 유지·추천 피드백·결과 삭제', async ({ page }) => {
  const keys = [];
  let history = [];
  let explanation = { analysisId: 8, status: 'NOT_REQUESTED', jobs: [] };
  const result = {
    id: 8, resumeId: 11, mode: 'LOCAL', inputMode: 'SAVED_RESUME',
    sources: [{ id: 4, filename: 'portfolio.pdf', status: 'READ' }], detectedSkills: ['java'], derivedPreferences: { careerYears: 3 },
    review: {
      version: 'verified-quotes-v1', score: { status: 'NOT_SCORED', reason: '검증된 종합 평가 기준이 없어 점수를 산출하지 않습니다.' },
      observations: { withMeasurement: 1, outcomeSentences: 3, basis: '측정 단위와 숫자를 확인합니다.' },
      skillEvidence: { java: [{ sourceKey: 'attachment:4:2', label: 'portfolio.pdf', page: 2, section: '첨부파일' }] },
      findings: [{ id: 'f1', dedupKey: 'metric:f1', source: 'RULE', quote: 'API 응답 속도를 개선했습니다.',
        problem: '측정값과 단위를 확인해 주세요.', rewriteTemplate: '[측정 전 값·단위] → [측정 후 값·단위]', feedback: 'OPEN',
        locations: [{ sourceKey: 'attachment:4:2', label: 'portfolio.pdf', page: 2, section: '첨부파일' }] }],
    },
    comparison: { retained: 0, newFindings: 1, notDetected: 2 },
    improvements: [{ sourcePath: '/projects', issueType: 'MISSING_PROJECT', feedback: '프로젝트 역할을 작성해 주세요.' }],
    recommendations: [{
      jobId: 17, rank: 1, matchScore: 80, title: '백엔드 개발자', companyName: '테스트 기업',
      location: '서울', experience: '신입', matchPoints: ['java'], missingPoints: ['mysql'],
    }],
  };
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjo0MTAyNDQ0ODAwfQ.test');
  });
  await page.route('**/api/v1/**', async (route) => {
    const req = route.request();
    const path = new URL(req.url()).pathname;
    const respond = (json, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(json) });
    if (path === '/api/v1/members/me') return respond({ id: 42, name: '회원', email: 'test@example.com', role: 'USER', profileStatus: 'ACTIVE' });
    if (path === '/api/v1/members/me/resumes') return respond([{ id: 11, title: '내 이력서' }]);
    if (path.endsWith('/resumes/11/attachments')) return respond([{ id: 4, filename: 'portfolio.pdf' }]);
    if (path.endsWith('/capabilities')) return respond({
      mode: 'LOCAL', consentVersion: 'local-analysis-v1', dailyLimit: 5,
      externalProcessing: true, externalConsentVersion: 'openai-evidence-v1', externalModel: 'gpt-5-nano',
    });
    if (path === '/api/v1/ai/resume-analyses') {
      if (req.method() === 'GET') return respond(history);
      keys.push(req.headers()['idempotency-key']);
      expect(req.postDataJSON()).toEqual({ resumeId: 11, consentVersion: 'local-analysis-v1' });
      if (keys.length === 1) return respond({}, 500);
      history = [{ id: 8, resumeTitle: '내 이력서', createdAt: '2026-10-20T12:00:00' }];
      return respond(result);
    }
    if (path.endsWith('/8/findings/f1/feedback')) {
      result.review.findings[0].feedback = req.postDataJSON().type;
      return route.fulfill({ status: 204 });
    }
    if (path.endsWith('/8/feedback')) {
      expect(req.postDataJSON()).toEqual({ jobId: 17, type: 'MATCHED' });
      return route.fulfill({ status: 204 });
    }
    if (path.endsWith('/8/explanation')) {
      if (req.method() === 'POST') {
        expect(req.postDataJSON()).toEqual({ consentVersion: 'openai-evidence-v1' });
        explanation = {
          analysisId: 8, status: 'COMPLETED', model: 'gpt-5-nano',
          jobs: [{ jobId: 17, highlights: ['이력서에 작성한 기술과 공고의 기술 태그가 일치합니다.'] }],
        };
      }
      return respond(explanation);
    }
    if (path.endsWith('/8')) {
      if (req.method() === 'DELETE') { history = []; return route.fulfill({ status: 204 }); }
      return respond(result);
    }
    return respond([]);
  });
  await page.goto('/resume-analysis?resumeId=11');
  const submit = page.getByRole('button', { name: '분석하기', exact: true });
  await expect(submit).toBeDisabled();
  await expect(page.getByText('portfolio.pdf', { exact: true })).toBeVisible();
  await expect(page.getByLabel('경력 연수')).toHaveCount(0);
  await expect(page.getByLabel('희망 지역')).toHaveCount(0);
  await page.getByRole('checkbox').check();
  await submit.click();
  await expect(page.getByRole('alert')).toBeVisible();
  await submit.click();
  await expect(page.getByRole('region', { name: '분석 결과' })).toBeVisible();
  await expect(page.getByText('portfolio.pdf · 읽기 완료')).toBeVisible();
  await expect(page.getByText('API 응답 속도를 개선했습니다.', { exact: true })).toBeVisible();
  await expect(page.getByText('portfolio.pdf · 2쪽', { exact: true }).first()).toBeVisible();
  await expect(page.getByText(/일치 점수/)).toHaveCount(0);
  await page.getByRole('button', { name: '반영했어요', exact: true }).click();
  await expect(page.getByRole('button', { name: '반영했어요', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByText('직접 반영 표시 1개')).toBeVisible();
  await page.getByRole('button', { name: '이 지적이 틀렸어요', exact: true }).click();
  await expect(page.getByRole('button', { name: '반영했어요', exact: true })).toHaveAttribute('aria-pressed', 'false');
  await expect(page.getByRole('button', { name: '이 지적이 틀렸어요', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: /내 이력서 · 2026-10-20/ }).click();
  await expect(page.getByRole('button', { name: '이 지적이 틀렸어요', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByText('유지 0개 · 새 후보 1개 · 이번에 미검출 2개')).toBeVisible();
  expect(keys).toHaveLength(2);
  expect(keys[0]).toBe(keys[1]);
  await expect(page.getByRole('link', { name: '백엔드 개발자' })).toHaveAttribute('href', '/job/17');
  const explain = page.getByRole('button', { name: 'AI로 추천 근거 정리', exact: true });
  await expect(explain).toBeDisabled();
  await page.getByLabel(/추천 근거 정리를 위해/).check();
  await explain.click();
  await expect(page.getByText('이력서에 작성한 기술과 공고의 기술 태그가 일치합니다.')).toBeVisible();
  await expect(explain).toHaveCount(0);
  await page.getByLabel('추천 피드백').selectOption('MATCHED');
  await expect(page.getByLabel('추천 피드백')).toHaveValue('MATCHED');
  await page.screenshot({ path: `.codex/resume-analysis-${test.info().project.name}.png`, fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: '결과 삭제', exact: true }).click();
  await expect(page.getByRole('region', { name: '분석 결과' })).toHaveCount(0);
  await expect(page.getByText('저장된 분석 결과가 없습니다.')).toBeVisible();
});

test('비활성 기능은 분석 양식을 노출하지 않는다', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjo0MTAyNDQ0ODAwfQ.test');
  });
  await page.route('**/api/v1/**', (route) => {
    if (new URL(route.request().url()).pathname === '/api/v1/members/me') {
      return route.fulfill({ json: { id: 42, name: '회원', role: 'USER', profileStatus: 'ACTIVE' } });
    }
    return route.fulfill({ status: 404, json: {} });
  });
  await page.goto('/resume-analysis');
  await expect(page.getByText('기능을 준비하고 있습니다.')).toBeVisible();
  await expect(page.getByRole('button', { name: '분석하기' })).toHaveCount(0);
});

test('첨부 읽기 실패는 파일명을 안내하고 결과를 만들지 않는다', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('accessToken', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjo0MTAyNDQ0ODAwfQ.test'));
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/members/me')) return route.fulfill({ json: { id: 42, name: '회원', role: 'USER', profileStatus: 'ACTIVE' } });
    if (path.endsWith('/capabilities')) return route.fulfill({ json: { mode: 'LOCAL', consentVersion: 'local-analysis-v2', dailyLimit: 5 } });
    if (path.endsWith('/resumes')) return route.fulfill({ json: [{ id: 11, title: '내 이력서' }] });
    if (path.endsWith('/attachments')) return route.fulfill({ json: [{ id: 4, filename: '읽을수없는문서.pdf' }] });
    if (route.request().method() === 'POST') return route.fulfill({ status: 422, json: { code: 'ATTACHMENT_READ_FAILED', attachmentId: 4 } });
    return route.fulfill({ json: [] });
  });
  await page.goto('/resume-analysis?resumeId=11');
  await expect(page.getByText('읽을수없는문서.pdf', { exact: true })).toBeVisible();
  await page.getByRole('checkbox').check();
  await page.getByRole('button', { name: '분석하기', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('읽을수없는문서.pdf');
  await expect(page.getByRole('region', { name: '분석 결과' })).toHaveCount(0);
});


test('실제 응답을 기다리는 진행 상태와 추천 0건 안내', async ({ page }) => {
  let complete;
  const responseGate = new Promise(resolve => { complete = resolve; });
  await page.addInitScript(() => localStorage.setItem('accessToken', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjo0MTAyNDQ0ODAwfQ.test'));
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/members/me')) return route.fulfill({ json: { id: 42, name: '회원', role: 'USER', profileStatus: 'ACTIVE' } });
    if (path.endsWith('/capabilities')) return route.fulfill({ json: { mode: 'LOCAL', consentVersion: 'local-analysis-v3', dailyLimit: 5 } });
    if (path.endsWith('/resumes')) return route.fulfill({ json: [{ id: 11, title: '내 이력서' }] });
    if (route.request().method() === 'POST') {
      await responseGate;
      return route.fulfill({ json: { id: 8, resumeId: 11, recommendations: [], improvements: [] } });
    }
    return route.fulfill({ json: [] });
  });
  await page.goto('/resume-analysis?resumeId=11');
  await page.getByRole('checkbox').check();
  await page.getByRole('button', { name: '분석하기', exact: true }).click();
  await expect(page.getByText('자료를 분석하고 있습니다', { exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: '분석 결과' })).toHaveCount(0);
  complete();
  await expect(page.getByRole('region', { name: '분석 결과' })).toBeVisible();
  await expect(page.getByText('자료를 분석하고 있습니다', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('link', { name: '전체 공개 공고 둘러보기 →' })).toHaveAttribute('href', '/jobs');
  await expect(page.getByText(/지원 가능한 공고가 전혀 없다는 뜻은 아닙니다/)).toBeVisible();
});
