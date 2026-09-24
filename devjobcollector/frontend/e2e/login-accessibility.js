import { expect, test } from '@playwright/test';

const expectVisibleFocus = async (locator) => {
  await expect(locator).toBeFocused();
  const hasIndicator = await locator.evaluate((element) => {
    const style = window.getComputedStyle(element);
    return style.outlineStyle !== 'none'
      || (style.boxShadow !== 'none' && style.boxShadow !== '')
      || style.borderColor === 'rgb(0, 78, 162)';
  });
  expect(hasIndicator).toBe(true);
};

export const registerLoginAccessibilityTests = () => {
  test('성공한 SNS 로그인을 최근 사용 배지로 표시하고 실패한 시도는 반영하지 않는다', async ({ page }) => {
    await page.route('http://localhost:8080/**', async (route) => {
      const path = new URL(route.request().url()).pathname;
      if (path === '/oauth2/authorization/github') {
        await route.fulfill({
          status: 302,
          headers: {
            Location: 'http://127.0.0.1:4174/oauth/callback?token=github-token',
          },
        });
        return;
      }
      await route.fulfill({ status: 200, contentType: 'text/html', body: '<p>OAuth authorization</p>' });
    });

    await page.goto('/login?next=/login');
    await expect(page.getByText('최근 사용')).toHaveCount(0);
    await page.getByTitle('github').click();

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByText('최근 사용')).toBeVisible();
    await expect(page.getByTitle('github')).toHaveAttribute('aria-label', 'GitHub로 계속, 최근 사용');

    await page.goto('/oauth/callback?error=OAUTH_LOGIN_FAILED&provider=google');
    await page.goto('/login');
    await expect(page.getByTitle('github')).toHaveAttribute('aria-label', 'GitHub로 계속, 최근 사용');
    await expect(page.getByTitle('google')).toHaveAttribute('aria-label', 'Google로 계속');
  });

  test('로그인 핵심 컨트롤은 DOM 순서대로 키보드 탐색되고 focus가 표시된다', async ({ page }) => {
    await page.goto('/login?next=/member');

    const focusOrder = [
      page.getByLabel('아이디 또는 이메일'),
      page.getByLabel('비밀번호'),
      page.getByLabel('로그인 유지'),
      page.getByLabel('아이디 저장'),
      page.getByRole('button', { name: '로그인' }),
      page.getByRole('link', { name: '아이디 찾기' }),
      page.getByRole('link', { name: '비밀번호 찾기' }),
      page.locator('.login-signup-button'),
      page.getByTitle('google'),
      page.getByTitle('kakao'),
      page.getByTitle('github'),
    ];

    await focusOrder[0].focus();
    await expectVisibleFocus(focusOrder[0]);
    for (const locator of focusOrder.slice(1)) {
      await page.keyboard.press('Tab');
      await expectVisibleFocus(locator);
    }
    await expect(page.getByTitle('naver')).toHaveCount(0);
  });

  test('모바일 로그인 핵심 조작 영역은 최소 44px touch target을 제공한다', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile-360', 'touch target 평가는 mobile-360에서 측정합니다.');
    await page.goto('/login?next=/member');

    const targets = page.locator([
      '.id-input-box input',
      '.pw-input-box input',
      '.InpBox',
      '.btn_login',
      '.signup-forgotten a',
      '.login-signup-button',
      '.social_icon',
    ].join(','));

    const boxes = await targets.evaluateAll((elements) => elements.map((element) => {
      const rect = element.getBoundingClientRect();
      return {
        target: element.getAttribute('title') || element.textContent.trim() || element.className,
        width: Math.round(rect.width * 10) / 10,
        height: Math.round(rect.height * 10) / 10,
      };
    }));

    expect(boxes.length).toBe(11);
    for (const box of boxes) {
      expect.soft(box.width, `${box.target} width`).toBeGreaterThanOrEqual(44);
      expect.soft(box.height, `${box.target} height`).toBeGreaterThanOrEqual(44);
    }
  });
};
