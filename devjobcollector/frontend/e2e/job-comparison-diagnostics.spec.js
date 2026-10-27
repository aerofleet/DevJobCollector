import { test, expect } from '@playwright/test';

const cases = [
  { name: '이전 결과', label: '비교 원인 미기록', diagnostic: null, expected: '등록 자료가 부족한지 이 결과만으로 판단할 수 없습니다.' },
  { name: 'AI 누락', label: 'AI 비교 누락', diagnostic: { status: 'MODEL_OMITTED', submittedCriteria: 0, acceptedCriteria: 0 }, expected: 'AI 응답에 이 공고의 비교 결과가 없습니다.' },
  { name: '빈 AI 비교', label: 'AI 비교 미완료', diagnostic: { status: 'EMPTY_COMPARISON', submittedCriteria: 0, acceptedCriteria: 0 }, expected: 'AI가 공고별 비교 항목을 작성하지 않았습니다.' },
  { name: '인용 실패', label: 'AI 근거 검증 실패', diagnostic: { status: 'VALIDATION_FAILED', submittedCriteria: 1, acceptedCriteria: 0, issues: { INVALID_JOB_REFERENCE: 1 } }, expected: 'AI가 지정한 공고 인용 위치를 원문에서 찾을 수 없음' },
  { name: '비교 입력 없음', label: '전송 자료 확인 필요', diagnostic: { status: 'INPUT_MISSING', submittedCriteria: 0, acceptedCriteria: 0, resumePassageCount: 0, issues: { NO_RESUME_PASSAGES: 1 } }, expected: '전송된 이력서에 인용 가능한 구간이 없음' },
  { name: '공고 상세 요건 미수집', label: '전송 자료 확인 필요', diagnostic: { status: 'INPUT_MISSING', submittedCriteria: 1, acceptedCriteria: 0, issues: { JOB_REQUIREMENTS_NOT_COLLECTED: 1 } }, expected: '공고의 상세 자격 요건이 수집되지 않음' },
  { name: '해당 요건 인용 없음', label: '요건 확인 필요', diagnostic: { status: 'RESUME_EVIDENCE_NOT_CITED', submittedCriteria: 1, acceptedCriteria: 1, uncitedResumeCriteria: 1 }, expected: '해당 경험이 없다는 뜻은 아니며' },
];

for (const item of cases) for (const surface of ['home', 'analysis']) {
  test(`${surface}: ${item.name} — 지원자 정보 부족과 구분`, async ({ page }) => {
    const criteria = item.diagnostic?.acceptedCriteria ? [{ jobQuote: 'Java API 운영 경험', resumeQuote: '', assessment: 'CHECK', explanation: '이 요건과 연결할 이력서 인용을 확인해 주세요.' }] : [];
    const review = { jobId: 17, relevance: 'REVIEW_NEEDED', decision: 'INSUFFICIENT_INFORMATION', decisionReason: criteria.length ? 'API 운영 경험과 연결한 인용이 없습니다.' : '이전의 포괄적인 보류 사유', criteria };
    if (item.diagnostic) review.comparisonDiagnostics = { version: 1, resumePassageCount: 5, jobPassageCount: 3, issues: {}, uncitedResumeCriteria: 0, ...item.diagnostic };
    const job = { jobId: 17, title: '합성 테스트 공고', companyName: '테스트 기업', matchPoints: [], missingPoints: [], aiDecision: review.decision, aiFit: review };
    const aiReview = { status: 'COMPLETED', findings: [], jobs: [review] };
    const result = { id: 8, resumeId: 11, inputFingerprint: 'synthetic', detectedSkills: [], derivedPreferences: {},
      review: { findings: [], observations: {}, score: {} }, improvements: [], aiReview, recommendations: [job] };
    await page.addInitScript(() => localStorage.setItem('accessToken', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0MiIsImV4cCI6NDEwMjQ0NDgwMH0.test'));
    await page.route('**/api/v1/**', route => {
      const path = new URL(route.request().url()).pathname;
      let data = [];
      if (path.endsWith('/members/me')) data = { id: 42, name: '테스트', role: 'USER', profileStatus: 'ACTIVE' };
      else if (path.endsWith('/capabilities')) data = { contentReviewAvailable: true };
      else if (path.endsWith('/home-recommendations')) data = { ...result, recommendations: [], aiExcludedRecommendations: [job], recommendationStatus: { aiFiltered: true, reason: 'NO_AI_APPROVED_MATCHES' } };
      else if (path.endsWith('/resume-analyses/8')) data = result;
      else if (path.endsWith('/content-review')) data = aiReview;
      else if (path.endsWith('/explanation')) data = { status: 'NOT_REQUESTED' };
      else if (path.endsWith('/resumes')) data = [{ id: 11, title: '합성 이력서' }];
      else if (path.includes('/jobs')) data = { content: [], totalElements: 0, totalPages: 0, last: true };
      return route.fulfill({ json: data });
    });
    await page.goto(surface === 'home' ? '/' : '/resume-analysis?analysisId=8');
    if (surface === 'home') await page.locator('.recommendation-excluded > summary').click();
    const panel = page.locator(surface === 'home' ? '.recommendation-excluded' : '.ai-job-review');
    await expect(panel.getByText(item.label, { exact: false }).first()).toBeVisible();
    await expect(panel.getByText(item.expected, { exact: false })).toBeVisible();
    await expect(panel.getByText('이전의 포괄적인 보류 사유')).toHaveCount(0);
    if (item.diagnostic) await expect(panel.getByText(`AI 비교 항목 ${item.diagnostic.submittedCriteria}개 중 ${item.diagnostic.acceptedCriteria}개가 인용 검증을 통과했습니다.`)).toBeVisible();
  });
}
