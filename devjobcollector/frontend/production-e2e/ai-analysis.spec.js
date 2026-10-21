import { expect, test } from '@playwright/test';
import { Buffer } from 'node:buffer';

// One-page text PDF with byte-accurate xref; synthetic text only, no personal data.
const syntheticPdf = () => {
  const stream = 'BT /F1 18 Tf 40 700 Td (Java synthetic portfolio) Tj ET';
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`,
  ];
  let text = '%PDF-1.4\n';
  const offsets = [0];
  objects.forEach((object, index) => {
    offsets.push(Buffer.byteLength(text));
    text += `${index + 1} 0 obj\n${object}\nendobj\n`;
  });
  const xref = Buffer.byteLength(text);
  text += `xref\n0 6\n0000000000 65535 f \n${offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')}trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(text);
};

test.use({ trace: 'off', screenshot: 'off' });

test('운영 합성 이력서 분석·동의·gpt-5-nano 근거 정리·삭제', async ({ page, request }, testInfo) => {
  test.skip(globalThis.process?.env.DJC_E2E_AI_ENABLED !== 'true'
    || testInfo.project.name !== 'desktop-1440', '합성 AI 운영 평가는 명시적으로 선택한 경우에만 1회 실행합니다.');
  test.setTimeout(90000);
  const token = globalThis.process?.env.DJC_E2E_ACCESS_TOKEN;
  expect(Boolean(token), 'Authenticated E2E token is required').toBe(true);
  const base = 'https://djc-api.itsdev.kr/api/v1';
  const headers = { Authorization: `Bearer ${token}` };
  const capabilities = await request.get(`${base}/ai/resume-analyses/capabilities`, { headers });
  expect(capabilities.status()).toBe(200);
  expect(await capabilities.json()).toMatchObject({ externalProcessing: true, externalModel: 'gpt-5-nano' });
  let resumeId;
  let analysisId;
  try {
    const created = await request.post(`${base}/members/me/resumes`, {
      headers,
      data: {
        title: `AI-E2E-${Date.now()}`,
        content: { basicInfo: {}, techStack: [{ name: 'Spring Boot' }], projects: [], experience: [] },
      },
    });
    expect(created.status()).toBe(201);
    resumeId = (await created.json()).id;
    const uploaded = await request.post(`${base}/members/me/resumes/${resumeId}/attachments`, {
      headers, multipart: { file: { name: 'synthetic-portfolio.pdf', mimeType: 'application/pdf', buffer: syntheticPdf() } },
    });
    expect(uploaded.status()).toBe(201);
    await page.addInitScript((value) => localStorage.setItem('accessToken', value), token);
    const navigation = await page.goto(`/resume-analysis?resumeId=${resumeId}`);
    expect(navigation.status()).toBe(200);
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex,follow');
    await expect(page.getByLabel('희망 직무')).toHaveCount(0);
    await page.getByRole('checkbox').check();
    const analysisResponse = page.waitForResponse((response) =>
      response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/v1/ai/resume-analyses');
    await page.getByRole('button', { name: '분석하기', exact: true }).click();
    const analyzed = await analysisResponse;
    expect(analyzed.status()).toBe(200);
    const analysis = await analyzed.json();
    analysisId = analysis.id;
    expect(analysis.inputMode).toBe('SAVED_RESUME');
    expect(analysis.sources).toEqual([expect.objectContaining({ filename: 'synthetic-portfolio.pdf', status: 'READ' })]);
    expect(analysis.detectedSkills).toContain('java');
    expect(analysis.recommendations.length).toBeGreaterThan(0);
    await page.getByLabel(/추천 근거 정리를 위해/).check();
    const explanationResponse = page.waitForResponse((response) =>
      response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith(`/${analysisId}/explanation`),
    { timeout: 40000 });
    await page.getByRole('button', { name: 'AI로 추천 근거 정리', exact: true }).click();
    const explained = await explanationResponse;
    expect(explained.status()).toBe(200);
    const explanation = await explained.json();
    expect(explanation).toMatchObject({ status: 'COMPLETED', model: 'gpt-5-nano', externalAttemptCount: 1 });
    expect(explanation.jobs.length).toBeGreaterThan(0);
    await expect(page.getByText('AI가 선택한 근거를 각 추천 공고에 표시했습니다.')).toBeVisible();
    const cached = await request.post(`${base}/ai/resume-analyses/${analysisId}/explanation`, {
      headers, data: { consentVersion: 'openai-evidence-v1' },
    });
    expect(cached.status()).toBe(200);
    expect((await cached.json()).jobs).toEqual(explanation.jobs);
  } finally {
    if (resumeId) {
      const deleted = await request.delete(`${base}/members/me/resumes/${resumeId}`, { headers });
      expect([204, 200, 404]).toContain(deleted.status());
    }
    if (analysisId) {
      const removed = await request.get(`${base}/ai/resume-analyses/${analysisId}`, { headers });
      expect(removed.status()).toBe(404);
    }
  }
});
