import { test, expect } from '@playwright/test';

const devJob = (id, title = `개발자 ${id}`) => ({
  id,
  title,
  companyName: '테스트',
  techStacks: [{ name: 'Java' }],
});

const nonDevJob = (id) => ({
  id,
  title: `마케팅 매니저 ${id}`,
  companyName: '테스트',
  techStacks: [],
});

test.describe('홈 공고 로딩 최적화', () => {
  test('충분한 후보 — API 1회, 카드 8개, 비개발 제외', async ({ page }) => {
    let searchCalls = 0;
    await page.route('**/api/v1/jobs/search**', async (route) => {
      searchCalls += 1;
      return route.fulfill({
        json: {
          content: [
            ...Array.from({ length: 10 }, (_, i) => devJob(i + 1)),
            ...Array.from({ length: 5 }, (_, i) => nonDevJob(100 + i)),
          ],
          totalElements: 15,
          totalPages: 1,
        },
      });
    });

    await page.goto('/');
    await expect(page.locator('.job-card')).toHaveCount(8);
    await expect(page.getByText('마케팅 매니저')).toHaveCount(0);
    expect(searchCalls).toBe(1);
  });

  test('부족한 후보 — API 1회, 있는 만큼만 표시', async ({ page }) => {
    let searchCalls = 0;
    await page.route('**/api/v1/jobs/search**', async (route) => {
      searchCalls += 1;
      return route.fulfill({
        json: {
          content: Array.from({ length: 3 }, (_, i) => devJob(i + 1)),
          totalElements: 3,
          totalPages: 1,
        },
      });
    });

    await page.goto('/');
    await expect(page.locator('.job-card')).toHaveCount(3);
    expect(searchCalls).toBe(1);
  });

  test('전체 실패 — 오류 메시지 표시, 허위 0건 아님', async ({ page }) => {
    await page.route('**/api/v1/jobs/search**', (route) =>
      route.fulfill({ status: 500, json: { error: 'server error' } })
    );

    await page.goto('/');
    await expect(page.getByText('추천 공고를 불러오지 못했습니다')).toBeVisible();
    await expect(page.getByText('현재 노출할 공고가 없습니다')).toHaveCount(0);
  });

  test('StrictMode 중복 요청 방지 (useRef)', async ({ page }) => {
    let searchCalls = 0;
    await page.route('**/api/v1/jobs/search**', async (route) => {
      searchCalls += 1;
      return route.fulfill({
        json: {
          content: Array.from({ length: 10 }, (_, i) => devJob(i + 1)),
          totalElements: 10,
          totalPages: 1,
        },
      });
    });

    await page.goto('/');
    await expect(page.locator('.job-card')).toHaveCount(8);
    expect(searchCalls).toBe(1);
  });
});

test.describe('홈 글꼴 일관성', () => {
  test('홈과 공고 카드에 Pretendard Variable 적용 및 로드', async ({ page }) => {
    await page.route('**/api/v1/jobs/search**', (route) =>
      route.fulfill({
        json: {
          content: Array.from({ length: 10 }, (_, i) => devJob(i + 1, `소프트웨어 엔지니어 ${i}`)),
          totalElements: 10,
          totalPages: 1,
        },
      })
    );

    await page.goto('/');
    await expect(page.locator('.job-card')).toHaveCount(8);

    for (const target of [page.locator('body'), page.locator('.job-card').first()]) {
      await expect(target).toHaveCSS('font-family', /^"?Pretendard Variable"?,/);
    }
    const loadedFonts = await page.evaluate(async () => {
      const faces = await document.fonts.load('16px "Pretendard Variable"', '소프트웨어 엔지니어');
      return faces.map((face) => ({ family: face.family, status: face.status }));
    });
    expect(loadedFonts.length).toBeGreaterThan(0);
    expect(loadedFonts.every((face) => face.family === 'Pretendard Variable' && face.status === 'loaded')).toBe(true);
  });
});
