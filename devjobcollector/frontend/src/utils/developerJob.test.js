import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isDeveloperJob } from './developerJob.js';

test('기관명과 비개발 직무의 개발은 개발자 추천에서 제외한다', () => {
  const jobs = [
    { companyName: '(재)우체국금융개발원', title: '우체국금융개발원 2026년 10차 공개채용 공고', jobCategory: '금융.보험,경비.청소', techStacks: [{ stackName: '보험' }] },
    { companyName: '한국개발연구원', title: '한국개발연구원 위촉직원 채용 공고(연구직)', jobCategory: '연구' },
    { title: '[한국장애인고용공단 경기남부직업능력개발원] 특정업무직(운전원) 채용', jobCategory: '운전.운송' },
    { title: '부자재 개발 담당자 채용', jobCategory: '구매' },
    { title: '사업개발(BD) 담당자', jobCategory: '사업개발' },
    { title: '부동산 개발자 채용', jobCategory: '부동산 개발자' },
    { title: '인재개발(HRD) 기획·운영 담당자', jobCategory: 'Management Supporting' },
    { title: '물류부동산 개발 및 투자 전문가', jobCategory: 'Finance' },
  ];
  for (const job of jobs) assert.equal(isDeveloperJob(job), false, job.title);
});

test('직무명이나 명확한 직무 분류의 소프트웨어 개발 공고는 포함한다', () => {
  const jobs = [
    { title: '[2027 인턴십] 네이버웹툰 백엔드 서버 개발', jobCategory: 'Tech / Backend' },
    { title: '프론트엔드 개발자' },
    { title: '서버 개발자 (Ads)' },
    { title: 'DevOps 엔지니어' },
    { title: 'Software Engineer' },
    { title: '2026년 공개채용', jobCategory: 'Backend' },
  ];
  for (const job of jobs) assert.equal(isDeveloperJob(job), true, job.title);
});
