import { expect, test } from '@playwright/test';

const accessToken = globalThis.process?.env.DJC_E2E_ACCESS_TOKEN;

test.use({ trace: 'off' });
test.describe.configure({ mode: 'serial' });

test('인증 사용자는 커리어 홈에 도달하고 활성 회원 정보를 확인한다', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop-1440', '인증 운영 평가는 단일 viewport에서만 실행합니다.');
  test.skip(!accessToken, 'DJC_E2E_ACCESS_TOKEN이 설정되어야 합니다.');

  await page.addInitScript((token) => {
    localStorage.setItem('accessToken', token);
  }, accessToken);

  const memberResponsePromise = page.waitForResponse((response) => (
    response.request().method() === 'GET'
    && new URL(response.url()).pathname.endsWith('/api/v1/members/me')
  ));

  await page.goto('/member');
  const memberResponse = await memberResponsePromise;
  expect(memberResponse.status()).toBe(200);

  const member = await memberResponse.json();
  expect(member.profileStatus).toBe('ACTIVE');
  expect(member.role).toBe('USER');

  await expect(page).toHaveURL(/\/member$/);
  await expect(page.getByRole('heading', { name: /내 커리어를 한곳에서/ })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '내 커리어 메뉴' })).toBeVisible();
  await expect(page.getByRole('link', { name: '커리어 홈' })).toHaveAttribute('aria-current', 'page');
});

test('인증 사용자는 이력서를 생성·수정·재조회하고 테스트 데이터를 정리한다', async ({ page, request }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop-1440', '인증 운영 쓰기 평가는 단일 viewport에서만 실행합니다.');
  test.skip(!accessToken, 'DJC_E2E_ACCESS_TOKEN이 설정되어야 합니다.');

  await page.addInitScript((token) => {
    localStorage.setItem('accessToken', token);
  }, accessToken);

  const runId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const createdTitle = `CH-G1-04 ${runId}`;
  const updatedTitle = `${createdTitle} 수정`;
  let createdResume;
  let collectionUrl;
  let cleanupCompleted = false;

  try {
    await page.goto('/resume');
    await expect(page.getByLabel('이력서 제목')).toBeVisible();
    await page.getByLabel('이력서 제목').fill(createdTitle);

    const createResponsePromise = page.waitForResponse((response) => (
      response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/api/v1/members/me/resumes')
    ));
    await page.getByRole('button', { name: '저장하기' }).click();
    const createResponse = await createResponsePromise;
    expect(createResponse.status()).toBe(201);
    createdResume = await createResponse.json();
    collectionUrl = createResponse.url();

    await expect(page).toHaveURL(/\/resumes$/);
    const createdCard = page.locator('.resume-manager-card', {
      has: page.getByRole('heading', { name: createdTitle, exact: true }),
    });
    await expect(createdCard).toBeVisible();
    await createdCard.getByRole('button', { name: `${createdTitle} 메뉴 열기` }).click();
    await createdCard.getByRole('menuitem', { name: '수정' }).click();

    await expect(page).toHaveURL(new RegExp(`/resume\\?resumeId=${createdResume.id}$`));
    await expect(page.getByLabel('이력서 제목')).toHaveValue(createdTitle);
    await page.getByLabel('이력서 제목').fill(updatedTitle);

    const updateResponsePromise = page.waitForResponse((response) => (
      response.request().method() === 'PUT'
      && new URL(response.url()).pathname.endsWith(`/api/v1/members/me/resumes/${createdResume.id}`)
    ));
    await page.getByRole('button', { name: '저장하기' }).click();
    const updateResponse = await updateResponsePromise;
    expect(updateResponse.status()).toBe(200);

    await expect(page).toHaveURL(/\/resumes$/);
    await page.reload();
    await expect(page.getByRole('heading', { name: updatedTitle, exact: true })).toBeVisible();

    const cleanupResponse = await request.delete(`${collectionUrl}/${createdResume.id}`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect([200, 204]).toContain(cleanupResponse.status());
    cleanupCompleted = true;

    await page.reload();
    await expect(page.getByRole('heading', { name: updatedTitle, exact: true })).toHaveCount(0);
  } finally {
    if (createdResume && collectionUrl && !cleanupCompleted) {
      const cleanupResponse = await request.delete(`${collectionUrl}/${createdResume.id}`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect([200, 204, 404]).toContain(cleanupResponse.status());
    }
  }
});
