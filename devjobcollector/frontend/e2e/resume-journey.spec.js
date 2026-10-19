import { expect, test } from '@playwright/test';

const member = {
  id: 42,
  email: 'member@example.com',
  name: '테스트회원',
  role: 'USER',
  profileStatus: 'ACTIVE',
};

const initialContent = {
  basicInfo: { name: '테스트회원', email: 'member@example.com' },
  techStack: [],
  projects: [],
  experience: [],
};

const timestamp = '2026-08-28T09:00:00';

test('이력서 공개 설정 저장·새로고침·비공개 전환·실패 복원', async ({ page }) => {
  await installApiMock(page, [{ id: 11, title: '내 이력서', status: 'DRAFT', updatedAt: timestamp }]);
  await page.goto('/resumes');
  const toggle = page.getByRole('switch', { name: '내 이력서 공개 설정' });
  await expect(toggle).not.toBeChecked();
  await page.screenshot({ path: `.codex/resume-visibility-simple-${test.info().project.name}.png`, fullPage: true });
  await toggle.click();
  await expect(toggle).toBeChecked();
  await page.reload();
  await expect(toggle).toBeChecked();
  await toggle.focus();
  await page.keyboard.press('Space');
  await expect(toggle).not.toBeChecked();
  await page.route('**/members/me/resumes/11/visibility', (route) => route.fulfill({ status: 500, body: '' }));
  await toggle.click();
  await expect(page.getByRole('alert')).toContainText('공개 설정을 저장하지 못했습니다');
  await expect(toggle).not.toBeChecked();
});

const installApiMock = async (page, initialResumes) => {
  const state = {
    resumes: [...initialResumes],
    details: new Map(initialResumes.map((resume) => [resume.id, {
      ...resume,
      content: structuredClone(initialContent),
    }])),
    lastCreate: null,
    lastUpdate: null,
    lastDelete: null,
  };

  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJyZXN1bWUtZTJlLXRva2VuIiwiZXhwIjo0MTAyNDQ0ODAwfQ.test-signature');
  });

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    const respond = (json, status = 200) => route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(json),
    });

    if (path === '/api/v1/members/me') {
      return respond(member);
    }

    if (path === '/api/v1/members/me/resumes' && method === 'GET') {
      return respond(state.resumes);
    }

    if (path === '/api/v1/members/me/resumes' && method === 'POST') {
      state.lastCreate = request.postDataJSON();
      const created = {
        id: 12,
        title: state.lastCreate.title,
        status: 'DRAFT',
        content: state.lastCreate.content,
        createdAt: timestamp,
        updatedAt: timestamp,
      };
      state.details.set(created.id, created);
      state.resumes = [created];
      return respond(created, 201);
    }

    const visibilityMatch = path.match(/^\/api\/v1\/members\/me\/resumes\/(\d+)\/visibility$/);
    if (visibilityMatch && method === 'PATCH') {
      const id = Number(visibilityMatch[1]);
      const updated = { ...state.details.get(id), visibility: request.postDataJSON().visibility };
      state.details.set(id, updated);
      state.resumes = state.resumes.map((resume) => resume.id === id ? updated : resume);
      return respond(updated);
    }
    const resumeMatch = path.match(/^\/api\/v1\/members\/me\/resumes\/(\d+)$/);
    if (resumeMatch) {
      const resumeId = Number(resumeMatch[1]);
      if (method === 'GET') {
        return respond(state.details.get(resumeId));
      }
      if (method === 'PUT') {
        state.lastUpdate = request.postDataJSON();
        const updated = {
          ...state.details.get(resumeId),
          title: state.lastUpdate.title,
          content: state.lastUpdate.content,
          updatedAt: '2026-08-28T10:00:00',
        };
        state.details.set(resumeId, updated);
        state.resumes = [updated];
        return respond(updated);
      }
      if (method === 'DELETE') {
        state.lastDelete = resumeId;
        state.details.delete(resumeId);
        state.resumes = state.resumes.filter((resume) => resume.id !== resumeId);
        return route.fulfill({ status: 204, body: '' });
      }
    }

    return route.fulfill({ status: 404, body: '' });
  });

  return state;
};

const expectNoHorizontalScroll = async (page) => {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - window.innerWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
};

test('기존 이력서를 목록에서 열어 수정하고 복귀한다', async ({ page }) => {
  const existing = {
    id: 11,
    title: '기존 개발자 이력서',
    status: 'DRAFT',
    createdAt: timestamp,
    updatedAt: timestamp,
  };
  const state = await installApiMock(page, [existing]);

  await page.goto('/resumes');
  await expect(page.getByRole('heading', { name: '이력서 관리' })).toBeVisible();
  await expect(page.getByRole('heading', { name: existing.title })).toBeVisible();
  await expect(page.getByText('작성 중')).toBeVisible();
  await expectNoHorizontalScroll(page);

  const menuTrigger = page.getByRole('button', { name: `${existing.title} 메뉴 열기` });
  await menuTrigger.click();
  await expect(page.getByRole('menuitem', { name: '수정' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('menuitem', { name: '수정' })).toHaveCount(0);
  await menuTrigger.click();
  await page.getByRole('menuitem', { name: '수정' }).click();
  await expect(page).toHaveURL(/\/resume\?resumeId=11$/);
  const title = page.getByLabel('이력서 제목');
  await expect(title).toHaveValue(existing.title);
  await title.fill('수정된 개발자 이력서');
  await page.getByRole('button', { name: '저장하기' }).click();

  await expect(page).toHaveURL(/\/resumes$/);
  await expect(page.getByRole('heading', { name: '수정된 개발자 이력서' })).toBeVisible();
  expect(state.lastUpdate.title).toBe('수정된 개발자 이력서');
  expect(state.lastUpdate.content.basicInfo.name).toBe('테스트회원');
  await expectNoHorizontalScroll(page);
});

test('빈 목록에서 새 이력서를 작성하고 목록으로 복귀한다', async ({ page }) => {
  const state = await installApiMock(page, []);

  await page.goto('/resumes');
  await expect(page.getByText('작성한 이력서가 없습니다.')).toBeVisible();
  await expectNoHorizontalScroll(page);
  await page.getByRole('link', { name: '이력서 작성하기' }).first().click();
  await expect(page).toHaveURL(/\/resume$/);

  await page.getByLabel('이력서 제목').fill('신규 이력서');
  await page.getByRole('button', { name: '저장하기' }).click();

  await expect(page).toHaveURL(/\/resumes$/);
  await expect(page.getByRole('heading', { name: '신규 이력서' })).toBeVisible();
  expect(state.lastCreate.title).toBe('신규 이력서');
  expect(state.lastCreate.content.techStack).toEqual([]);
  await expectNoHorizontalScroll(page);
});

test('저장된 이력서를 확인 후 삭제하고 빈 목록을 표시한다', async ({ page }) => {
  const existing = {
    id: 11,
    title: '삭제할 개발자 이력서',
    status: 'DRAFT',
    createdAt: timestamp,
    updatedAt: timestamp,
  };
  const state = await installApiMock(page, [existing]);

  await page.goto('/resumes');
  await expect(page.getByRole('heading', { name: existing.title })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: '삭제' })).toHaveCount(0);
  await page.getByRole('button', { name: `${existing.title} 메뉴 열기` }).click();
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('menuitem', { name: '삭제' }).click();

  await expect(page.getByText('작성한 이력서가 없습니다.')).toBeVisible();
  await expect(page.getByRole('heading', { name: existing.title })).toHaveCount(0);
  expect(state.lastDelete).toBe(existing.id);
  await expectNoHorizontalScroll(page);
});
