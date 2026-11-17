import { test } from 'node:test';
import assert from 'node:assert/strict';
import { jobCardRoles } from './jobCardRoles.js';

test('직무명과 분류를 표시하고 기술 및 조직명은 직무로 오인하지 않는다', () => {
  assert.deepEqual(jobCardRoles({ title: 'Technical Program Manager II', jobCategory: 'Oversight Engineering', mentionedTechStacks: ['AWS', 'Grafana', 'SQL'] }), ['기술 프로그램 매니저']);
  assert.deepEqual(jobCardRoles({ title: '백엔드 엔지니어', techStacks: [{ stackName: 'Java' }] }), ['백엔드']);
  assert.deepEqual(jobCardRoles({ title: '공개 채용', jobCategory: 'backend' }), ['백엔드']);
  assert.deepEqual(jobCardRoles({ title: '공개 채용', jobCategory: 'Oversight Engineering', techStacks: [{ stackName: 'AWS' }] }), ['공개 채용']);
  assert.deepEqual(jobCardRoles({ title: '[한국장애인고용공단 대구직업능력개발원] 특정업무직(미화원) 채용공고' }), ['미화원']);
  assert.deepEqual(jobCardRoles({ title: '[한국장애인고용공단 경기남부직업능력개발원] 특정업무직(운전원) 채용' }), ['운전원']);
  assert.deepEqual(jobCardRoles({ title: '계약직 고객 상담원 채용' }), ['고객 상담원']);
  assert.deepEqual(jobCardRoles({ title: 'Seller Compliance Manager' }), ['컴플라이언스 매니저']);
  assert.deepEqual(jobCardRoles({ title: 'Real Estate Transactions Manager' }), ['부동산 거래 매니저']);
  assert.deepEqual(jobCardRoles({ title: 'Cinematographer/Video Editor' }), ['영상 촬영·편집']);
  assert.deepEqual(jobCardRoles({ title: 'Enterprise Risk Management (ERM) Program Lead' }), ['리스크 관리']);
  assert.deepEqual(jobCardRoles({ title: 'Account Executive, Existing Business' }), ['영업 담당자']);
  assert.deepEqual(jobCardRoles({ title: 'People Team Intern, EMEA Employment Compliance & Manager Enablement (Spring 2027)', jobCategory: 'University' }), ['인사팀 인턴']);
  assert.deepEqual(jobCardRoles({ title: 'Senior Manager, Customer Engineering', jobCategory: 'Solution Engineering' }), ['고객 엔지니어링 매니저']);
  assert.deepEqual(jobCardRoles({ title: '[네이버웹툰] 일본어 Localization PM (한국어-일본어) (계약직)', jobCategory: 'Corporate / 통번역' }), ['일본어 현지화 PM']);
  assert.deepEqual(jobCardRoles({ title: 'Sr. Regional Marketing Manager - UKI', jobCategory: 'Marketing' }), ['지역 마케팅 매니저']);
  assert.deepEqual(jobCardRoles({ title: 'Business Development Representative - French Speaking', jobCategory: 'BDR' }), ['사업개발 담당자']);
  assert.deepEqual(jobCardRoles({ title: 'Localization PM' }), ['현지화 PM']);
  assert.deepEqual(jobCardRoles({ title: 'Customer Engineering Intern' }), ['Customer Engineering Intern']);
  assert.deepEqual(jobCardRoles({ title: '미화원 및 운전원 모집' }), ['미화원', '운전원']);
  assert.deepEqual(jobCardRoles({ title: 'UX 디자이너' }), ['UX·UI 디자인']);
  assert.deepEqual(jobCardRoles({ title: '2026년 10차 공개채용 공고', jobCategory: '금융.보험,경비.청소' }), ['2026년 10차 공개채용 공고']);
  assert.deepEqual(jobCardRoles({ title: '' }), []);
});
