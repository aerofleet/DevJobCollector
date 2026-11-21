import { test } from 'node:test';
import assert from 'node:assert/strict';
import { jobCardRoles, resolveJobCardRoles } from './jobCardRoles.js';

test('승인된 공통 분야는 알리오와 일반 공고에 같은 방식으로 표시한다', () => {
  assert.deepEqual(jobCardRoles({ sourcePlatform: 'PUBLIC_ALIO', title: '채용 공고', jobCategory: '정보통신', approvedJobFields: ['전산직', '연구직'] }), ['전산직', '연구직']);
  assert.deepEqual(jobCardRoles({ sourcePlatform: 'GREENHOUSE', title: 'Backend Engineer', approvedJobFields: ['전산직'] }), ['전산직']);
  assert.deepEqual(jobCardRoles({ sourcePlatform: 'GREENHOUSE', title: 'Backend Engineer', approvedJobFields: ['부정확한 분야'] }), ['백엔드']);
});

test('승인된 AI 직무는 기존 규칙이 없는 공고에서 표시한다', () => {
  const longTitle = 'Manager, Selection Operations (Product Lifecycle Selection)';
  assert.deepEqual(jobCardRoles({ title: longTitle, approvedJobRole: '상품 운영 매니저' }), ['상품 운영 매니저']);
  assert.equal(resolveJobCardRoles({ title: longTitle, approvedJobRole: '상품 운영 매니저' }).needsReview, false);
  assert.deepEqual(jobCardRoles({ title: longTitle, approvedJobRole: '' }), [longTitle]);
  assert.deepEqual(jobCardRoles({ title: 'Customer Engineering Intern', approvedJobRole: '고객 엔지니어링 인턴' }), ['고객 엔지니어링 인턴']);
  assert.deepEqual(jobCardRoles({ title: '백엔드 개발자', approvedJobRole: '상품 운영 매니저' }), ['백엔드']);
  assert.deepEqual(jobCardRoles({ sourcePlatform: 'PUBLIC_ALIO', title: longTitle, jobCategory: '정보통신', approvedJobRole: '상품 운영 매니저' }), ['정보통신']);
  assert.deepEqual(jobCardRoles({ id: 4517, sourcePlatform: 'GREENHOUSE', title: longTitle, approvedJobRole: '상품 운영 매니저' }), ['백엔드', 'Global Operations Technology']);
});

test('검토한 공고 20건에 해당 직무를 표시한다', () => {
  const cases = [
    ['Senior Treasury Specialist, Bridge Capital & Liquidity Risk – Digital Assets', '8504 Bridge - R&D', '재무·자금'],
    ['Senior Financial Operations Lead, Vendor Operations', 'Business Operations/Analysis', '재무·금융'],
    ['Sr. Counsel - Product, Engineering & AI ', 'Legal', '법률 자문'],
    ['Applied Scientist II - Advertiser Optimization', 'Machine Learning', '응용과학 연구원'],
    ['People Safety & Resilience Manager', 'Engagement', '산업안전·위기관리'],
    ['Professional Services Consultant, Application Security', 'Professional Services', '정보보안'],
    ['Account Development Representative', 'Sales Development', '영업 개발 담당자'],
    ['Account Development Representative', 'Sales Development', '영업 개발 담당자'],
    ['Staff IT Operations Engineer (대구 물류센터)', 'zz-Evergreen Requisition', 'IT 운영 엔지니어'],
    ['HR Operations Specialist, APAC (Full-time)', 'People & Places', '인사 운영'],
    ['People Operations Specialist, APAC (Full-time)', 'People & Places', '인사 운영'],
    ['Sales Development Representative - Summer 2027 Graduates - LATAM Portuguese Speaking', 'Sales Development', '영업 개발 담당자'],
    ['Sales Development Representative - Summer 2027 Graduates - LATAM Spanish Speaking', 'Sales Development', '영업 개발 담당자'],
    ['[쿠팡] 리테일 가격 운영 및 비즈니스 이슈 관리 매니저 (RBI팀)', 'PBS and Pricing Ops', '리테일 가격 운영'],
    ['Senior Marketplace Account Manager', 'Marketplace', '계정 관리 매니저'],
    ['Candidate Experience Specialist, Contractor (AUS)', 'Talent Acquisition', '채용 운영'],
    ['Technical Quality Specialist - Figma Weave (Tokyo, Japan)', 'Figma Weave', '기술 지원·품질'],
    ['IT 어시스턴트 (단기계약직) (IT Assistant)', null, 'IT 지원'],
    ['[화장품 ODM상장사] IT인프라 담당 (서버/네트워크/전산장비 등)', 'IT 운영 및 관리', 'IT 인프라 운영'],
    ['Sales & Account Manager - 로컬 잡스', 'Business', '영업·계정 관리'],
  ];
  for (const [title, jobCategory, expected] of cases) {
    assert.deepEqual(jobCardRoles({ title, jobCategory }), [expected], title);
  }
});

test('Treasury Specialist 공고는 재무·자금 직무로 표시한다', () => {
  assert.deepEqual(jobCardRoles({ id: 13653, sourcePlatform: 'GREENHOUSE', title: 'Senior Treasury Specialist, Bridge Capital & Liquidity Risk – Digital Assets' }), ['재무·자금']);
  assert.deepEqual(jobCardRoles({ title: 'Treasury Specialist' }), ['재무·자금']);
  assert.deepEqual(jobCardRoles({ title: 'Treasury Platform Software Engineer' }), ['소프트웨어 개발']);
  assert.deepEqual(jobCardRoles({ sourcePlatform: 'PUBLIC_ALIO', title: 'Treasury Specialist', jobCategory: '금융.보험' }), ['금융.보험']);
});

test('쿠팡 GOT 개발자 공고의 직무 태그를 표시한다', () => {
  assert.deepEqual(jobCardRoles({ id: 4517, sourcePlatform: 'GREENHOUSE', title: 'Global Operations Technology(GOT) 개발자 집중 채용', jobCategory: 'zz-Evergreen Requisition' }), ['백엔드', 'Global Operations Technology']);
  assert.deepEqual(jobCardRoles({ id: 4518, sourcePlatform: 'GREENHOUSE', title: 'Global Operations Technology(GOT) 개발자 집중 채용' }), ['Global Operations Technology(GOT) 개발자 집중 채용']);
});

test('긴 직무 태그에서 직무 외 지역과 부서 정보를 제외한다', () => {
  assert.deepEqual(jobCardRoles({ title: 'Mid-Market Sales Engineer (Dubai)', jobCategory: 'Commercial & Mid-Market Sales Engineering' }), ['세일즈 엔지니어']);
  assert.deepEqual(jobCardRoles({ title: 'Demand Generation Campaign Manager', jobCategory: 'Marketing' }), ['수요 창출 캠페인 매니저']);
  assert.deepEqual(jobCardRoles({ title: 'Senior Program Manager, Governance, Risk and Compliance', jobCategory: 'Assurance, Risk & Compliance' }), ['거버넌스·리스크·컴플라이언스 프로그램 매니저']);
  assert.deepEqual(jobCardRoles({ title: 'Strategy Specialist (in Japan)', jobCategory: 'JP - Business' }), ['Strategy Specialist']);
  assert.deepEqual(jobCardRoles({ title: '[네이버웹툰] 일본 콘텐츠 그로스 매니저 (경력)', jobCategory: 'Service & Business / Content Development' }), ['콘텐츠 그로스 매니저']);
  assert.deepEqual(jobCardRoles({ title: '2027 - AI and People Transformation Intern, NYC' }), ['AI·인사 혁신 인턴']);
  assert.deepEqual(jobCardRoles({ title: 'Engineering Manager, Stream Analytics' }), ['엔지니어링 매니저']);
  assert.deepEqual(jobCardRoles({ title: 'Senior Recruiter, Public Sector GTM' }), ['채용 담당자']);
  assert.deepEqual(jobCardRoles({ title: 'Staff Designated Support Engineer' }), ['기술 지원 엔지니어']);
  assert.deepEqual(jobCardRoles({ title: '[쿠팡] 로켓배송 직매입 운영 MD(경력 3년 이상, 계약직)' }), ['직매입 운영 MD']);
  assert.deepEqual(jobCardRoles({ title: '[쿠팡풀필먼트서비스] 물류센터 시설관리(FM) 담당자 - (부천2센터)' }), ['시설관리 담당자']);
  assert.deepEqual(jobCardRoles({ title: '[쿠팡] Senior Instock Planning Analyst (5년 이상)' }), ['재고 기획 분석가']);
  const longTitle = '확인되지 않은 매우 긴 채용 공고 제목과 부가 설명을 포함한 모집 공고';
  assert.deepEqual(jobCardRoles({ title: longTitle }), [longTitle]);
  assert.equal(resolveJobCardRoles({ title: longTitle }).needsReview, true);
  assert.deepEqual(jobCardRoles({ title: '직무 확인 필요' }), []);
  assert.deepEqual(jobCardRoles({ title: 'Sales Operations Manager' }), ['운영 매니저']);
  assert.deepEqual(jobCardRoles({ sourcePlatform: 'PUBLIC_ALIO', title: '공고', jobCategory: '정보통신' }), ['정보통신']);
});

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
  assert.deepEqual(jobCardRoles({ title: 'Operations Manager (FinCrime)', jobCategory: '3313 SDC - Financial Crimes' }), ['금융범죄 운영 매니저']);
  assert.deepEqual(jobCardRoles({ title: '[네이버웹툰] 일본어 애니메이션 번역 PM (한국어-일본어) (계약직)', jobCategory: 'Corporate / 통번역' }), ['일본어 애니메이션 번역 PM']);
  assert.deepEqual(jobCardRoles({ title: 'Senior Manager, Account Management - Marketplace Business', jobCategory: 'Marketplace' }), ['계정 관리 매니저']);
  assert.deepEqual(jobCardRoles({ title: '공연기획 운영 담당 (Live Entertainment) — Coupang Play', jobCategory: 'Coupang Play' }), ['공연 기획·운영']);
  assert.deepEqual(jobCardRoles({ title: '카카오비즈니스 마케팅 콘텐츠 제작_어시스턴트', jobCategory: '서비스비즈' }), ['마케팅 콘텐츠 제작 어시스턴트']);
  assert.deepEqual(jobCardRoles({ title: '[네이버웹툰] EN Growth&Marketing Design (체험형 인턴)', jobCategory: 'Design / Visual Comm. & Brand Design' }), ['그로스·마케팅 디자인 인턴']);
  assert.deepEqual(jobCardRoles({ title: 'Operations Manager' }), ['운영 매니저']);
  assert.deepEqual(jobCardRoles({ title: '2026년 채용', jobCategory: 'Financial Crimes' }), ['2026년 채용']);
  assert.deepEqual(jobCardRoles({ title: 'Customer Engineering Intern' }), ['Customer Engineering Intern']);
  assert.deepEqual(jobCardRoles({ title: '미화원 및 운전원 모집' }), ['미화원', '운전원']);
  assert.deepEqual(jobCardRoles({ title: 'UX 디자이너' }), ['UX·UI 디자인']);
  assert.deepEqual(jobCardRoles({ title: '2026년 10차 공개채용 공고', jobCategory: '금융.보험,경비.청소' }), ['2026년 10차 공개채용 공고']);
  assert.deepEqual(jobCardRoles({ title: '' }), []);
});

test('알리오 공고는 제목 추출 대신 제공된 채용 분야를 표시한다', () => {
  assert.deepEqual(jobCardRoles({ sourcePlatform: 'PUBLIC_ALIO', title: '미화원 채용', jobCategory: '경비.청소' }), ['경비.청소']);
  assert.deepEqual(jobCardRoles({ sourcePlatform: 'PUBLIC_ALIO', title: '공개채용', jobCategory: '금융.보험, 경비.청소, 금융.보험, ' }), ['금융.보험', '경비.청소']);
  assert.deepEqual(jobCardRoles({ sourcePlatform: 'PUBLIC_ALIO', title: '백엔드 개발자', jobCategory: '정보통신' }), ['정보통신']);
  assert.deepEqual(jobCardRoles({ sourcePlatform: 'PUBLIC_ALIO', title: '운전원 모집', jobCategory: null }), ['채용 분야 미제공']);
});
