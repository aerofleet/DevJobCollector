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
      const url = new URL(route.request().url());
      const keyword = url.searchParams.get('keyword') ?? '';
      // 키워드 없는 초기 조회: 개발 10 + 비개발 5
      if (!keyword) {
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
      }
      // 키워드 검색은 도달하면 안 됨
      return route.fulfill({ json: { content: [], totalElements: 0, totalPages: 0 } });
    });

    await page.goto('/');
    await expect(page.locator('.job-card')).toHaveCount(8);

    // 비개발 공고 제목이 없어야 함
    await expect(page.getByText('마케팅 매니저')).toHaveCount(0);

    // 초기 조회 1회만 실행
    expect(searchCalls).toBe(1);
  });

  test('부족한 후보 — 보충 검색 실행, 중복 제거', async ({ page }) => {
    let searchCalls = 0;
    await page.route('**/api/v1/jobs/search**', async (route) => {
      searchCalls += 1;
      const url = new URL(route.request().url());
      const keyword = url.searchParams.get('keyword') ?? '';
      if (!keyword) {
        // 초기 조회에서 개발 공고 3개만 반환
        return route.fulfill({
          json: {
            content: Array.from({ length: 3 }, (_, i) => devJob(i + 1)),
            totalElements: 3,
            totalPages: 1,
          },
        });
      }
      // 키워드 검색: 일부 중복 포함
      return route.fulfill({
        json: {
          content: [
            devJob(1, '개발자 1'),  // 중복
            devJob(20 + searchCalls, `${keyword} 엔지니어`),
          ],
          totalElements: 2,
          totalPages: 1,
        },
      });
    });

    await page.goto('/');
    await expect(page.locator('.job-card').first()).toBeVisible();

    // 보충 검색이 실행되어 1회 이상
    expect(searchCalls).toBeGreaterThan(1);

    // 카드 ID 중복 없음 확인
    const ids = await page.evaluate(() =>
      [...document.querySelectorAll('.job-card')].map((el) => el.dataset.jobId ?? el.textContent)
    );
    expect(new Set(ids).size).toBe(ids.length);
  });

  test('전체 실패 — 오류 메시지 표시, 허위 0건 아님', async ({ page }) => {
    await page.route('**/api/v1/jobs/search**', (route) =>
      route.fulfill({ status: 500, json: { error: 'server error' } })
    );

    await page.goto('/');
    await expect(page.getByText('추천 공고를 불러오지 못했습니다')).toBeVisible();
    // 빈 상태가 아닌 오류 메시지
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

    // dev 서버의 StrictMode에서도 요청은 1회
    expect(searchCalls).toBe(1);
  });
});

test.describe('홈 글꼴 최적화', () => {
  test('홈에서 woff/woff2 글꼴 추가 요청 없음', async ({ page }) => {
    const fontRequests = [];
    page.on('request', (req) => {
      const url = req.url();
      if (/\.woff2?(\?|$)/i.test(url)) {
        fontRequests.push(url);
      }
    });

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

    // 한글 공고 제목이 렌더링된 후에도 추가 글꼴 subset 요청 없음
    // CSS에서 index.css 자체 import된 기본 font-face는 있을 수 있으나
    // body:has(.main-page)로 시스템 글꼴 스택이 적용되어 Pretendard woff 없어야 함
    const pretendardRequests = fontRequests.filter((url) => /pretendard/i.test(url));
    expect(pretendardRequests).toEqual([]);
  });
});
