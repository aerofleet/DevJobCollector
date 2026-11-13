import { expect, test } from '@playwright/test';
import { Buffer } from 'node:buffer';

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/admin/companies/public-seeds?*', (route) => route.fulfill({ json: { data: [] } }));
});

test('후보 목록의 상태 필터와 페이지 이동을 사용한다', async ({ page }) => {
  await page.route('**/api/v1/admin/me', (route) => route.fulfill({ json: {
    data: { id: 1, name: '심사자', role: 'REVIEWER' },
  } }));
  await page.route('**/api/v1/admin/companies/discovery-candidates?*', (route) => {
    const params = new URL(route.request().url()).searchParams;
    const after = Number(params.get('afterId'));
    const status = params.get('status');
    const records = status === 'REJECTED' ? [] : Array.from({ length: 21 }, (_, index) => ({
      id: index + 1, companyName: `기업 ${index + 1}`, homepageUrl: 'https://example.com',
      provider: 'GREETING', sourceIdentifier: `board-${index + 1}`,
      sourceUrl: `https://board-${index + 1}.career.greetinghr.com/ko/home`,
      status: 'DISCOVERED', lastSeenAt: '2026-11-12T00:00:00',
    })).filter((candidate) => candidate.id > after);
    return route.fulfill({ json: { data: records } });
  });
  await page.goto('/discovery');
  await expect(page.getByRole('cell', { name: '기업 1', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '다음', exact: true }).click();
  await expect(page.getByRole('cell', { name: '기업 21', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '이전', exact: true }).click();
  await expect(page.getByRole('cell', { name: '기업 1', exact: true })).toBeVisible();
  await page.getByLabel('후보 상태').selectOption('REJECTED');
  await expect(page.getByText('조건에 맞는 후보가 없습니다.')).toBeVisible();
  await expect(page.getByRole('button', { name: '승인' })).toHaveCount(0);
});

test('사유를 입력하고 후보를 거절한다', async ({ page }) => {
  await page.route('**/api/v1/admin/me', (route) => route.fulfill({ json: {
    data: { id: 1, name: '심사자', role: 'REVIEWER' },
  } }));
  let status = 'DISCOVERED';
  await page.route('**/api/v1/admin/companies/discovery-candidates?*', (route) => route.fulfill({ json: {
    data: [{ id: 1, companyName: '기업', homepageUrl: 'https://example.com', provider: 'GREETING',
      sourceIdentifier: 'example', sourceUrl: 'https://example.career.greetinghr.com/ko/home', status }],
  } }));
  await page.route('**/api/v1/admin/companies/discovery-candidates/1/reject', (route) => {
    expect(route.request().postDataJSON()).toEqual({ reason: '기업 관계 미확인' });
    status = 'REJECTED';
    return route.fulfill({ status: 204 });
  });
  await page.goto('/discovery');
  await page.getByRole('button', { name: '거절', exact: true }).click();
  await page.getByRole('button', { name: '거절 확정' }).click();
  await expect(page.getByRole('alert')).toContainText('거절 사유를 입력해주세요.');
  await page.getByLabel('거절 사유').fill('기업 관계 미확인');
  await page.getByRole('button', { name: '거절 확정' }).click();
  await expect(page.getByText('후보를 거절했습니다.')).toBeVisible();
  await expect(page.getByRole('cell', { name: '거절', exact: true })).toBeVisible();
});

test('관리자가 공공 CSV를 수입한다', async ({ page }) => {
  let imported = false;
  let homepage = null;
  let ats = [];
  await page.route('**/api/v1/admin/companies/public-seeds?*', (route) => route.fulfill({ json: {
    data: imported ? [{ id: 1, companyName: '국내기업', source: 'KOSME_JOB_COMPANIES', status: 'DISCOVERED',
      matchedCompanyId: 7, matchedCompanyName: '국내기업', companyStatus: 'VERIFIED',
      version: 0, homepageCandidateUrl: homepage }] : [],
  } }));
  await page.route('**/api/v1/admin/me', (route) => route.fulfill({ json: {
    data: { id: 1, name: '관리자', role: 'ADMIN' },
  } }));
  await page.route('**/api/v1/admin/companies/discovery-candidates?*', (route) => route.fulfill({ json: { data: [] } }));
  await page.route('**/api/v1/admin/companies/public-seeds/import', (route) => {
    expect(route.request().headers()['content-type']).toContain('multipart/form-data');
    imported = true;
    return route.fulfill({ json: { data: { accepted: 2, skipped: 1 } } });
  });
  await page.route('**/api/v1/admin/companies/public-seeds/1/homepage', (route) => {
    expect(route.request().postDataJSON()).toEqual({ url: 'https://example.com', expectedVersion: 0 });
    homepage = 'https://example.com';
    return route.fulfill({ status: 204 });
  });
  await page.route('**/api/v1/admin/companies/public-seeds/1/ats-candidates?*', (route) => route.fulfill({ json: { data: ats } }));
  await page.route('**/api/v1/admin/companies/public-seeds/1/discover-html', (route) => {
    expect(route.request().postDataJSON()).toEqual({ html: '<a href="https://nuua.career.greetinghr.com/ko/home">채용</a>', expectedVersion: 0 });
    ats = [{ id: 1, provider: 'GREETING', sourceIdentifier: 'nuua', sourceUrl: 'https://nuua.career.greetinghr.com/ko/home', status: 'DISCOVERED',
      firstHomepageCandidateUrl: 'https://old.example', firstSeedVersion: 0, currentSeedVersion: 1 }];
    return route.fulfill({ json: { data: { candidateCount: 1 } } });
  });
  await page.route('**/api/v1/admin/companies/public-seeds/1/ats-candidates/1/reject', (route) => {
    expect(route.request().postDataJSON()).toEqual({ reason: '소유권 미확인' });
    ats = ats.map((item) => ({ ...item, status: 'REJECTED' }));
    return route.fulfill({ status: 204 });
  });
  await page.route('**/api/v1/admin/companies/public-seeds/1/ats-candidates/1/review', (route) => {
    expect(route.request().postDataJSON()).toEqual({ note: '기업 관계 확인 중', referenceUrl: 'https://example.com/about', expectedSeedVersion: 0 });
    ats = ats.map((item) => ({ ...item, status: 'VERIFYING', reviewNote: '기업 관계 확인 중', reviewReferenceUrl: 'https://example.com/about' }));
    return route.fulfill({ status: 204 });
  });
  await page.route('**/api/v1/admin/companies/public-seeds/1/ats-candidates/1/verify-board', (route) => {
    expect(route.request().postDataJSON()).toEqual({ expectedSeedVersion: 0 });
    ats = ats.map((item) => ({ ...item, status: 'BOARD_VERIFIED', boardVerifiedAt: '2026-11-13T00:00:00', boardJobCount: 1, boardSeedVersion: 0 }));
    return route.fulfill({ json: { data: { jobCount: 1 } } });
  });
  await page.goto('/discovery');
  await page.getByLabel('중진공 구인기업 CSV (10MB 이하)').setInputFiles({
    name: 'seed.csv', mimeType: 'text/csv', buffer: Buffer.from('기업명,사업자번호'),
  });
  await page.getByRole('button', { name: '기업 Seed 수입' }).click();
  await expect(page.getByText(/유효 행 2건, 제외 행 1건/)).toBeVisible();
  await expect(page.getByRole('cell', { name: '국내기업', exact: true })).toBeVisible();
  await expect(page.getByRole('cell', { name: '국내기업 (#7)', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '홈페이지 입력' }).click();
  await page.getByLabel('홈페이지 후보 URL').fill('https://example.com');
  await page.getByRole('button', { name: '후보 저장' }).click();
  await expect(page.getByText('홈페이지 후보를 저장했습니다. 공식 여부는 확인이 필요합니다.')).toBeVisible();
  await expect(page.getByRole('cell', { name: 'https://example.com', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'ATS 후보 확인' }).click();
  await page.getByLabel('홈페이지 HTML').fill('<a href="https://nuua.career.greetinghr.com/ko/home">채용</a>');
  await page.getByRole('button', { name: 'ATS 후보 추출' }).click();
  await expect(page.getByText(/GREETING · nuua/)).toBeVisible();
  await expect(page.getByText(/최초 발견 이후 홈페이지 입력 이력 변경됨/)).toBeVisible();
  await expect(page.getByText(/HTML 출처와 기업 소유권은 확인되지 않았습니다/)).toBeVisible();
  await page.getByRole('button', { name: '검수 시작' }).click();
  await page.getByLabel('검수 메모').fill('기업 관계 확인 중');
  await page.getByLabel('참고 URL (선택)').fill('https://example.com/about');
  await page.getByRole('button', { name: '검수 기록 저장' }).click();
  await expect(page.getByText(/검수 메모: 기업 관계 확인 중/)).toBeVisible();
  await page.getByRole('button', { name: 'ATS 공고 확인' }).click();
  await expect(page.getByText(/ATS 공고 확인: 1건/)).toBeVisible();
  await page.getByRole('button', { name: 'ATS 후보 거절' }).click();
  await page.getByLabel('ATS 후보 거절 사유').fill('소유권 미확인');
  await page.getByRole('button', { name: 'ATS 거절 확정' }).click();
  await expect(page.getByText('ATS 후보를 거절했습니다.')).toBeVisible();
  await expect(page.getByText(/GREETING · nuua.*REJECTED/)).toBeVisible();
});

test('관리자가 소유권을 확인한 뒤 수집 대상을 승인한다', async ({ page }) => {
  await page.route('**/api/v1/admin/me', (route) => route.fulfill({ json: { data: { id: 1, name: '관리자', role: 'ADMIN' } } }));
  await page.route('**/api/v1/admin/companies/discovery-candidates?*', (route) => route.fulfill({ json: { data: [] } }));
  await page.route('**/api/v1/admin/companies/public-seeds?*', (route) => route.fulfill({ json: { data: [{
    id: 1, companyName: '국내기업', source: 'KOSME_JOB_COMPANIES', status: 'DISCOVERED', version: 1,
    homepageCandidateUrl: 'https://example.com',
  }] } }));
  let status = 'BOARD_VERIFIED';
  await page.route('**/api/v1/admin/companies/public-seeds/1/ats-candidates?*', (route) => route.fulfill({ json: { data: [{
    id: 1, provider: 'GREETING', sourceIdentifier: 'nuua', sourceUrl: 'https://nuua.career.greetinghr.com/ko/home',
    status, currentSeedVersion: 1, boardSeedVersion: 1, boardVerifiedAt: '2026-11-13T00:00:00', boardJobCount: 1,
  }] } }));
  await page.route('**/api/v1/admin/companies/public-seeds/1/ats-candidates/1/approve-target', (route) => {
    expect(route.request().postDataJSON()).toEqual({ expectedSeedVersion: 1, companyName: '국내기업', ownershipConfirmed: true });
    status = 'TARGET_CREATED';
    return route.fulfill({ json: { data: { targetId: 7 } } });
  });
  await page.goto('/discovery');
  await page.getByRole('button', { name: 'ATS 후보 확인' }).click();
  await page.getByRole('button', { name: '수집 대상 승인' }).click();
  await page.getByLabel('기업명 확인').fill('국내기업');
  await page.getByLabel('공식 홈페이지와 ATS의 기업 소유권을 확인했습니다.').check();
  await page.getByRole('button', { name: '승인 및 수집 대상 생성' }).click();
  await expect(page.getByText(/수집 대상 #7을 생성했습니다/)).toBeVisible();
});
