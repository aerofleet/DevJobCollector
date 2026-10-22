import { expect, test } from '@playwright/test';

test('추천 피드백 저장 실패·제외·새로고침·다시 노출', async ({ page }) => {
  let feedback = '';
  let failNext = true;
  const job = { jobId: 17, rank: 1, title: '피드백 테스트 공고', companyName: '기업', matchPoints: [], missingPoints: [] };
  await page.addInitScript(() => localStorage.setItem('accessToken', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjo0MTAyNDQ0ODAwfQ.test'));
  await page.route('**/api/v1/**', route => {
    const req = route.request(); const path = new URL(req.url()).pathname;
    if (path.endsWith('/members/me')) return route.fulfill({ json: { id: 42, name: '회원', role: 'USER', profileStatus: 'ACTIVE' } });
    if (path.endsWith('/capabilities')) return route.fulfill({ json: { consentVersion: 'local-analysis-v3', dailyLimit: 5 } });
    if (path.endsWith('/resumes')) return route.fulfill({ json: [{ id: 11, title: '내 이력서' }] });
    if (path.endsWith('/resume-analyses')) return route.fulfill({ json: [{ id: 8, resumeTitle: '피드백 분석', createdAt: '2026-10-22' }] });
    if (path.endsWith('/8/feedback')) {
      expect(req.postDataJSON().jobId).toBe(17);
      if (failNext) { failNext = false; return route.fulfill({ status: 500, json: {} }); }
      feedback = req.postDataJSON().type;
      return route.fulfill({ status: 204 });
    }
    if (path.endsWith('/resume-analyses/8')) {
      const excluded = feedback && feedback !== 'MATCHED';
      return route.fulfill({ json: { id: 8, resumeId: 11, improvements: [],
        recommendations: excluded ? [] : [{ ...job, feedback }], excludedRecommendations: excluded ? [{ ...job, feedback }] : [] } });
    }
    return route.fulfill({ json: [] });
  });
  await page.goto('/resume-analysis?resumeId=11');
  const open = () => page.getByRole('button', { name: /피드백 분석 ·/ }).click();
  const link = page.getByRole('link', { name: job.title, exact: true });
  await open();
  await page.getByLabel('추천 피드백').selectOption('CAREER_MISMATCH');
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(link).toBeVisible();
  await expect(page.getByLabel('추천 피드백')).toHaveValue('');
  for (const type of ['CAREER_MISMATCH', 'SKILL_MISMATCH', 'LOCATION_MISMATCH', 'ALREADY_SEEN', 'NOT_INTERESTED', 'INACCURATE_JOB']) {
    await page.getByLabel('추천 피드백').selectOption(type);
    await expect(link).toHaveCount(0);
    await expect(page.getByText('피드백에 따라 모든 추천 공고를 제외했습니다.', { exact: false })).toBeVisible();
    await page.reload(); await open();
    await expect(link).toHaveCount(0);
    await page.getByText('제외한 공고 1개', { exact: true }).click();
    await page.getByRole('button', { name: `${job.title} 다시 노출` }).click();
    await expect(link).toBeVisible();
    await expect(page.getByLabel('추천 피드백')).toHaveValue('MATCHED');
    await page.reload(); await open();
    await expect(link).toBeVisible();
    await expect(page.getByLabel('추천 피드백')).toHaveValue('MATCHED');
  }
});

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
  await expect(page.getByText('이 결과는 이전 검사 규칙으로 저장됐습니다.', { exact: false })).toBeVisible();
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


test('기존 분석의 반복 제안을 묶고 대상 항목과 개선 절차를 안내한다', async ({ page }) => {
  const metric = (index) => ({ sourcePath: `/projects/${index}/description`, issueType: 'METRIC_NOT_DETECTED', feedback: '검증 가능한 수치가 있다면 측정 조건과 함께 추가해 주세요.' });
  await page.addInitScript(() => localStorage.setItem('accessToken', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjo0MTAyNDQ0ODAwfQ.test'));
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/members/me')) return route.fulfill({ json: { id: 42, name: '회원', role: 'USER', profileStatus: 'ACTIVE' } });
    if (path.endsWith('/capabilities')) return route.fulfill({ json: { mode: 'LOCAL', consentVersion: 'local-analysis-v3', dailyLimit: 5 } });
    if (path.endsWith('/resumes')) return route.fulfill({ json: [{ id: 11, title: '내 이력서' }] });
    if (path.endsWith('/resume-analyses')) return route.fulfill({ json: [{ id: 8, resumeTitle: '이전 분석', createdAt: '2026-10-20' }] });
    if (path.endsWith('/resume-analyses/8')) return route.fulfill({ json: { id: 8, resumeId: 11, recommendations: [], improvements: [metric(0), metric(1), metric(0),
      { sourcePath: '/projects/2/description', issueType: 'MISSING_DESCRIPTION', feedback: '설명을 작성해 주세요.' }] } });
    return route.fulfill({ json: [] });
  });
  await page.goto('/resume-analysis');
  await page.getByRole('button', { name: /이전 분석 ·/ }).click();
  const section = page.getByRole('region', { name: '작성 항목별 보완 제안' });
  await expect(section.getByRole('article')).toHaveCount(2);
  const metricCard = section.getByRole('article').filter({ has: page.getByRole('heading', { name: '성과를 확인할 근거를 보완해 주세요' }) });
  await expect(metricCard.getByText('프로젝트 1 · 설명', { exact: true })).toHaveCount(1);
  await expect(metricCard.getByText('프로젝트 2 · 설명', { exact: true })).toBeVisible();
  await expect(metricCard.getByText('보완 검토', { exact: true })).toBeVisible();
  await expect(metricCard.getByText(/측정값이 없다면 수행한 작업과 확인 방법/)).toBeVisible();
  await metricCard.getByText('작성 예시 보기', { exact: true }).click();
  await expect(metricCard.getByText(/측정 전 값·단위/)).toBeVisible();
  await expect(section.getByText('프로젝트 3 · 설명', { exact: true })).toBeVisible();
  await expect(section.getByRole('link', { name: '이력서에서 해당 항목 수정하기 →' }).first()).toHaveAttribute('href', '/resume?resumeId=11');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: `.codex/writing-suggestions-${test.info().project.name}.png`, fullPage: true });
});


test('AI 내용 분석은 전송 미리보기 동의 후 실행하고 수정안과 공고 근거를 표시한다', async ({ page }) => {
  let aiCalls = 0;
  let state = { status: 'NOT_REQUESTED' };
  const result = { id: 8, resumeId: 11, inputFingerprint: 'snapshot', improvements: [], recommendations: [
    { jobId: 17, title: 'Java 개발자', rank: 1, matchPoints: ['java'], missingPoints: [], companyName: '기업', skillEvidenceSource: 'TECH_TAGS' }] };
  await page.addInitScript(() => localStorage.setItem('accessToken', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjo0MTAyNDQ0ODAwfQ.test'));
  await page.route('**/api/v1/**', async route => {
    const req = route.request(); const path = new URL(req.url()).pathname;
    if (path.endsWith('/members/me')) return route.fulfill({ json: { id: 42, name: '회원', role: 'USER', profileStatus: 'ACTIVE' } });
    if (path.endsWith('/capabilities')) return route.fulfill({ json: { contentReviewAvailable: true, externalProcessing: true, consentVersion: 'local-analysis-v3', dailyLimit: 5 } });
    if (path.endsWith('/resumes')) return route.fulfill({ json: [{ id: 11, title: '내 이력서' }] });
    if (path.endsWith('/resume-analyses')) return route.fulfill({ json: [{ id: 8, resumeTitle: '저장된 분석', createdAt: '2026-10-22' }] });
    if (path.endsWith('/resume-analyses/8')) return route.fulfill({ json: { ...result, aiReview: state } });
    if (path.endsWith('/content-review/preview')) return route.fulfill({ json: { consentVersion: 'openai-content-review-v1', previewHash: 'a'.repeat(64),
      sources: [{ id: 'S1', label: '저장된 이력서', section: '프로젝트 1' }, { id: 'S2', label: 'portfolio.pdf', page: 2 }],
      payload: { sources: [{ sourceId: 'S1', text: 'Java API를 개발했습니다.' }, { sourceId: 'S2', text: '테스트를 작성했습니다.' }], jobs: [{ slot: 0, text: 'Java API 개발' }] } } });
    if (path.endsWith('/content-review')) {
      if (req.method() === 'POST') {
        aiCalls++;
        expect(req.postDataJSON()).toEqual({ consentVersion: 'openai-content-review-v1', previewHash: 'a'.repeat(64) });
        state = { status: 'COMPLETED', findings: [{ id: 'f1', feedback: 'OPEN', issueType: 'ROLE', quote: 'Java API를 개발했습니다.', label: '저장된 이력서', section: '프로젝트 1',
          problem: '담당한 기능을 구분해 주세요.', actions: ['직접 구현한 기능을 적어 주세요.'], rewriteTemplate: '[담당 기능]을 구현했습니다.' }],
          jobs: [{ jobId: 17, relevance: 'RELATED', criteria: [{ jobQuote: 'Java API 개발', resumeQuote: 'Java API를 개발했습니다.', assessment: 'SUPPORTED', explanation: 'API 개발 경험을 연결할 수 있습니다.' }] }] };
      }
      return route.fulfill({ json: state });
    }
    if (path.endsWith('/findings/f1/feedback')) { state.findings[0].feedback = req.postDataJSON().type; return route.fulfill({ status: 204 }); }
    return route.fulfill({ json: [] });
  });
  await page.goto('/resume-analysis');
  await page.getByRole('button', { name: /저장된 분석 ·/ }).click();
  const panel = page.getByRole('region', { name: 'AI 내용 분석', exact: true });
  await panel.getByRole('button', { name: 'AI에 전송할 내용 확인' }).click();
  await expect(panel.getByLabel('S1 전송 내용')).toHaveValue('Java API를 개발했습니다.');
  await expect(panel.getByLabel('S2 전송 내용')).toHaveValue('테스트를 작성했습니다.');
  const submit = panel.getByRole('button', { name: '동의하고 AI 내용 분석' });
  await expect(submit).toBeDisabled(); expect(aiCalls).toBe(0);
  await panel.getByRole('checkbox').check(); await submit.click();
  await expect(panel.getByText('담당한 기능을 구분해 주세요.', { exact: true })).toBeVisible();
  await expect(page.getByText('API 개발 경험을 연결할 수 있습니다.', { exact: true })).toBeVisible();
  await panel.getByRole('button', { name: '이 지적이 틀렸어요', exact: true }).click();
  await expect(panel.getByRole('button', { name: '이 지적이 틀렸어요', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await panel.getByRole('button', { name: 'AI 처리 상태 확인' }).click(); expect(aiCalls).toBe(1);
  await expect(panel.getByRole('button', { name: '동의하고 AI 내용 분석' })).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: `.codex/content-review-${test.info().project.name}.png`, fullPage: true });
});
