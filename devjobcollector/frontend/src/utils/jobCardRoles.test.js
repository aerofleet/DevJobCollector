import { test } from 'node:test';
import assert from 'node:assert/strict';
import { jobCardRoles } from './jobCardRoles.js';

test('직무명과 분류를 표시하고 기술 및 조직명은 직무로 오인하지 않는다', () => {
  assert.deepEqual(jobCardRoles({ title: 'Technical Program Manager II', jobCategory: 'Oversight Engineering', mentionedTechStacks: ['AWS', 'Grafana', 'SQL'] }), ['기술 프로그램 매니저']);
  assert.deepEqual(jobCardRoles({ title: '백엔드 엔지니어', techStacks: [{ stackName: 'Java' }] }), ['백엔드']);
  assert.deepEqual(jobCardRoles({ title: '공개 채용', jobCategory: 'backend' }), ['백엔드']);
  assert.deepEqual(jobCardRoles({ title: '공개 채용', jobCategory: 'Oversight Engineering', techStacks: [{ stackName: 'AWS' }] }), ['직무 확인 필요']);
});
