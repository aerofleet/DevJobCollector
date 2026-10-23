import { expect, test } from '@playwright/test';
import { Buffer } from 'node:buffer';

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

test('새 이력서에서 파일 첨부·유지·다운로드·삭제 및 형식 오류 처리', async ({ page }) => {
  const state = await installApiMock(page, []);
  await page.goto('/resume');
  await page.getByPlaceholder('이력서 제목을 입력하세요').fill('포트폴리오 이력서');
  const section = page.getByRole('region', { name: '첨부파일', exact: true });
  await expect(section).toContainText('첨부파일이 비어있습니다.');
  await page.getByLabel('첨부파일 선택').setInputFiles({ name: 'bad.html', mimeType: 'text/html', buffer: Buffer.from('bad') });
  await expect(section.getByRole('alert')).toContainText('PDF, PNG, JPG');
  expect(state.lastCreate).toBeNull();
  await page.getByLabel('첨부파일 선택').setInputFiles({ name: 'portfolio.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n%%EOF') });
  await expect(page).toHaveURL(/resumeId=12/);
  await expect(section.getByRole('button', { name: 'portfolio.pdf', exact: true })).toBeVisible();
  expect(state.uploads).toBe(1);
  await page.reload();
  await expect(page.getByPlaceholder('이력서 제목을 입력하세요')).toHaveValue('포트폴리오 이력서');
  const downloadPromise = page.waitForEvent('download');
  await section.getByRole('button', { name: 'portfolio.pdf', exact: true }).click();
  expect((await downloadPromise).suggestedFilename()).toBe('portfolio.pdf');
  await section.scrollIntoViewIfNeeded();
  await page.screenshot({ path: `.codex/resume-attachments-${test.info().project.name}.png`, fullPage: true });
  const layout = await page.evaluate(() => ({
    width: window.innerWidth,
    scrollWidth: document.documentElement.scrollWidth,
    overflow: [...document.querySelectorAll('body *')].map((element) => ({
      tag: element.tagName, className: element.className?.toString(), right: element.getBoundingClientRect().right,
    })).filter((element) => element.right > window.innerWidth).slice(0, 12),
  }));
  expect(layout.scrollWidth, JSON.stringify(layout)).toBeLessThanOrEqual(layout.width);
  page.once('dialog', (dialog) => dialog.accept());
  await section.getByRole('button', { name: 'portfolio.pdf 삭제' }).click();
  await expect(section).toContainText('첨부파일이 비어있습니다.');
});

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
    attachments: new Map(),
    uploads: 0,
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

    const attachmentMatch = path.match(/^\/api\/v1\/members\/me\/resumes\/(\d+)\/attachments(?:\/(\d+))?$/);
    if (attachmentMatch) {
      const id = Number(attachmentMatch[1]);
      const fileId = Number(attachmentMatch[2]);
      const files = state.attachments.get(id) || [];
      if (method === 'POST') {
        expect(request.headers()['content-type']).toContain('multipart/form-data; boundary=');
        state.uploads += 1;
        const file = { id: state.uploads, filename: 'portfolio.pdf', contentType: 'application/pdf', size: 14 };
        state.attachments.set(id, [...files, file]);
        return respond(file, 201);
      }
      if (method === 'DELETE') {
        state.attachments.set(id, files.filter((file) => file.id !== fileId));
        return route.fulfill({ status: 204, body: '' });
      }
      if (fileId) return route.fulfill({ contentType: 'application/pdf', body: '%PDF-1.4\n%%EOF' });
      return respond(files);
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


test('지원 방향과 구체적 경험을 저장하고 분석 화면으로 이동한다', async ({ page }) => {
  const state = await installApiMock(page, []);
  await page.goto('/resume');
  const roles = page.getByRole('group', { name: '희망 직무', exact: true });
  const locations = page.getByRole('group', { name: '희망 지역', exact: true });
  const employment = page.getByRole('group', { name: '고용 형태', exact: true });
  for (const group of [roles, locations, employment]) {
    await group.getByRole('button', { name: '전체 선택', exact: true }).click();
    expect(await group.getByRole('checkbox', { checked: true }).count()).toBe(await group.getByRole('checkbox').count());
    await group.getByRole('button', { name: '전체 해제', exact: true }).click();
    await expect(group.getByRole('checkbox', { checked: true })).toHaveCount(0);
  }
  await roles.getByLabel('백엔드', { exact: true }).check();
  await roles.getByLabel('프론트엔드', { exact: true }).check();
  await locations.getByLabel('서울', { exact: true }).check();
  await locations.getByLabel('경기', { exact: true }).check();
  await employment.getByRole('button', { name: '전체 선택', exact: true }).click();
  await page.getByLabel('경험 요약').fill('Java API 개발 경험');
  await page.getByRole('button', { name: '프로젝트 추가', exact: true }).click();
  await page.getByPlaceholder('프로젝트명', { exact: true }).fill('주문 서비스');
  await page.getByPlaceholder('프로젝트 설명', { exact: true }).fill('기존에 작성한 설명');
  await page.getByText('경험을 구체적으로 정리하기 · 선택', { exact: true }).click();
  await page.getByLabel('프로젝트 1 내 역할과 담당 범위').fill('주문 API 구현');
  await page.getByLabel('프로젝트 1 직접 수행한 작업').fill('Spring Boot로 트랜잭션을 처리했습니다.');
  await page.getByRole('button', { name: '경력 추가', exact: true }).click();
  await page.getByLabel('현재 재직 중').check();
  await page.getByRole('button', { name: '저장하고 AI 분석', exact: true }).click();
  await expect(page).toHaveURL(/resume-analysis\?resumeId=12/);
  expect(state.lastCreate.content.careerProfile).toMatchObject({ category: ['백엔드', '프론트엔드'], location: ['서울', '경기'],
    employmentType: ['정규직', '계약직', '인턴', '프리랜서'], summary: 'Java API 개발 경험' });
  expect(state.lastCreate.content.projects[0]).toMatchObject({ title: '주문 서비스', description: '기존에 작성한 설명', role: '주문 API 구현', actions: 'Spring Boot로 트랜잭션을 처리했습니다.' });
  expect(state.lastCreate.content.experience[0].isCurrent).toBe(true);
  await page.goto('/resume?resumeId=12');
  await expect(roles.getByLabel('백엔드', { exact: true })).toBeChecked();
  await expect(roles.getByLabel('프론트엔드', { exact: true })).toBeChecked();
  await expect(locations.getByRole('checkbox', { checked: true })).toHaveCount(2);
  await expect(employment.getByRole('checkbox', { checked: true })).toHaveCount(4);
  await page.locator('#projects').getByText('경험을 구체적으로 정리하기 · 선택', { exact: true }).click();
  await expect(page.getByLabel('프로젝트 1 내 역할과 담당 범위')).toHaveValue('주문 API 구현');
  await expect(page.getByPlaceholder('프로젝트 설명', { exact: true })).toHaveValue('기존에 작성한 설명');
  await expectNoHorizontalScroll(page);
  await page.screenshot({ path: `.codex/resume-structured-${test.info().project.name}.png`, fullPage: true });
});

test('확장 항목 저장·재조회·미리보기·진행 상태를 보존한다', async ({ page }) => {
  const state = await installApiMock(page, []);
  await page.goto('/resume');
  await page.getByLabel('이력서 제목').fill('백엔드 지원 이력서');
  await page.getByLabel('이름', { exact: true }).fill('테스트 작성자');
  await page.getByLabel('이메일', { exact: true }).fill('candidate@example.com');
  await page.getByRole('textbox', { name: '간단소개', exact: true }).fill('서비스의 문제를 해결하는 개발자입니다.');
  await page.getByRole('button', { name: '+ 링크 추가', exact: true }).click();
  await page.getByLabel('링크 이름', { exact: true }).fill('포트폴리오');
  await page.getByLabel('주소', { exact: true }).fill('javascript:alert(1)');
  await expect(page.getByText('http:// 또는 https://로 시작하는 주소를 입력해 주세요.')).toBeVisible();
  await page.getByLabel('주소', { exact: true }).fill('https://example.com/portfolio');
  await page.getByRole('button', { name: '+ 학력 추가', exact: true }).click();
  await page.getByLabel('학교명', { exact: true }).fill('테스트대학교');
  await page.getByLabel('전공', { exact: true }).fill('컴퓨터공학');
  await page.getByLabel('학적 상태').selectOption('졸업');
  await page.getByLabel('교육이력 항목 표시').check();
  await page.getByRole('button', { name: '+ 교육 추가', exact: true }).click();
  await page.getByLabel('교육명', { exact: true }).fill('서버 개발 과정');
  await page.getByLabel('교육 내용', { exact: true }).fill('Spring Boot로 API를 구현했습니다.');
  await page.getByLabel('자기소개서 항목 표시').check();
  await page.getByRole('textbox', { name: '자기소개서', exact: true }).fill('문제를 분석하고 직접 확인합니다.');
  await page.getByRole('button', { name: '프로젝트 추가', exact: true }).click();
  await page.getByPlaceholder('프로젝트명', { exact: true }).fill('주문 서비스');
  await page.getByPlaceholder('프로젝트 설명', { exact: true }).fill('주문 API를 구현했습니다.');
  await page.getByLabel('프로젝트 1 종료일').fill('2026-07-01');
  await page.getByLabel('현재 진행 중').check();
  await expect(page.getByLabel('프로젝트 1 종료일')).toHaveValue('');
  await expect(page.getByLabel('프로젝트 1 종료일')).toBeDisabled();
  await page.getByLabel('저장소 링크', { exact: true }).fill('https://example.com/code');
  await page.getByRole('button', { name: '경력 추가', exact: true }).click();
  await page.getByPlaceholder('회사명', { exact: true }).fill('테스트 회사');
  await page.getByLabel('경력 구분').selectOption('NON_DEVELOPMENT');
  await page.getByLabel('경력 1 종료일').fill('2025-01-01');
  await page.getByLabel('현재 재직 중').check();
  await expect(page.getByLabel('경력 1 종료일')).toHaveValue('');
  await page.getByLabel('간단소개 항목 표시').uncheck();
  await page.getByRole('button', { name: '미리보기', exact: true }).click();
  const preview = page.getByRole('dialog', { name: '이력서 미리보기' });
  await expect(preview).toContainText('서비스의 문제를 해결하는 개발자입니다.');
  await expect(preview).toContainText('테스트대학교');
  await expect(preview).toContainText('서버 개발 과정');
  await expect(preview.getByRole('link', { name: /포트폴리오/ })).toHaveAttribute('href', 'https://example.com/portfolio');
  await page.screenshot({ path: `.codex/resume-preview-${test.info().project.name}.png` });
  await page.keyboard.press('Escape');
  await expect(preview).toHaveCount(0);
  await expect(page.getByRole('button', { name: '미리보기', exact: true })).toBeFocused();
  await page.getByRole('button', { name: '저장하기', exact: true }).click();
  await expect(page).toHaveURL(/\/resumes$/);
  expect(state.lastCreate.content.education[0].school).toBe('테스트대학교');
  expect(state.lastCreate.content.projects[0]).toMatchObject({ endDate: '', isCurrent: true });
  expect(state.lastCreate.content.experience[0]).toMatchObject({ workType: 'NON_DEVELOPMENT', endDate: '', isCurrent: true });
  await page.goto('/resume?resumeId=12');
  await expect(page.getByLabel('교육명', { exact: true })).toHaveValue('서버 개발 과정');
  await expect(page.getByRole('textbox', { name: '간단소개', exact: true })).toHaveValue('서비스의 문제를 해결하는 개발자입니다.');
  await expectNoHorizontalScroll(page);
  await page.screenshot({ path: `.codex/resume-editor-${test.info().project.name}.png`, fullPage: true });
});

test('사진 형식 검증·저장·재조회·삭제가 동작한다', async ({ page }) => {
  const state = await installApiMock(page, []);
  await page.goto('/resume');
  await page.getByLabel('사진 항목 표시').check();
  await page.getByLabel('사진 선택').setInputFiles({ name: 'bad.svg', mimeType: 'image/svg+xml', buffer: Buffer.from('<svg/>') });
  await expect(page.getByRole('alert')).toContainText('JPG, PNG, WebP');
  const encoded = await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 16; canvas.height = 16;
    canvas.getContext('2d').fillRect(0, 0, 16, 16); return canvas.toDataURL('image/png').split(',')[1];
  });
  await page.getByLabel('사진 선택').setInputFiles({ name: 'photo.png', mimeType: 'image/png', buffer: Buffer.from(encoded, 'base64') });
  await expect(page.getByRole('img', { name: '이력서 사진' })).toBeVisible();
  await page.getByRole('button', { name: '저장하기', exact: true }).click();
  await expect(page).toHaveURL(/\/resumes$/);
  expect(state.lastCreate.content.basicInfo.photo).toMatch(/^data:image\/jpeg;base64,/);
  await page.goto('/resume?resumeId=12');
  await expect(page.getByRole('img', { name: '이력서 사진' })).toBeVisible();
  await page.getByRole('button', { name: '사진 삭제', exact: true }).click();
  await page.getByRole('button', { name: '저장하기', exact: true }).click();
  await expect(page).toHaveURL(/\/resumes$/);
  expect(state.lastUpdate.content.basicInfo.photo).toBe('');
});

test('기존 단일 지원 방향을 불러와 복수 선택과 전체 해제를 저장한다', async ({ page }) => {
  const state = await installApiMock(page, [{ id: 11, title: '기존 이력서', status: 'DRAFT', updatedAt: timestamp }]);
  state.details.get(11).content.careerProfile = { category: '백엔드', location: '서울', employmentType: '정규직' };
  await page.goto('/resume?resumeId=11');
  const roles = page.getByRole('group', { name: '희망 직무', exact: true });
  const locations = page.getByRole('group', { name: '희망 지역', exact: true });
  const employment = page.getByRole('group', { name: '고용 형태', exact: true });
  await expect(roles.getByLabel('백엔드', { exact: true })).toBeChecked();
  await expect(locations.getByLabel('서울', { exact: true })).toBeChecked();
  await expect(employment.getByLabel('정규직', { exact: true })).toBeChecked();
  await roles.getByRole('button', { name: '전체 선택', exact: true }).click();
  await roles.getByLabel('보안', { exact: true }).uncheck();
  await locations.getByRole('button', { name: '전체 해제', exact: true }).click();
  await employment.getByLabel('인턴', { exact: true }).check();
  await page.getByRole('button', { name: '저장하고 AI 분석', exact: true }).click();
  await expect(page).toHaveURL(/resume-analysis\?resumeId=11/);
  await page.goto('/resume?resumeId=11');
  await expect(roles.getByRole('checkbox', { checked: true })).toHaveCount(7);
  await expect(roles.getByLabel('보안', { exact: true })).not.toBeChecked();
  await expect(locations.getByRole('checkbox', { checked: true })).toHaveCount(0);
  await expect(locations.getByText('지역 제한 없음')).toBeVisible();
  await expect(employment.getByRole('checkbox', { checked: true })).toHaveCount(2);
  await expectNoHorizontalScroll(page);
});
