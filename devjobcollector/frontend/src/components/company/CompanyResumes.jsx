import { useState } from 'react';
import authenticatedApi from '../../api/authenticatedApi';
import BasicInfo from '../resume/BasicInfo';
import TechStack from '../resume/TechStack';
import Projects from '../resume/Projects';
import Experience from '../resume/Experience';
import Attachments from '../resume/Attachments';

const noChange = () => {};

export default function CompanyResumes({ company }) {
  const [result, setResult] = useState(null);
  const [detail, setDetail] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const allowed = company.companyStatus === 'VERIFIED'
    && company.membershipStatus === 'ACTIVE'
    && ['OWNER', 'ADMIN', 'RECRUITER'].includes(company.role);
  if (!allowed) return null;

  const load = async (page = 0, resumeId = null) => {
    setBusy(true);
    setError('');
    setDetail(null);
    try {
      const base = `/companies/${company.companyId}/resumes`;
      const response = await authenticatedApi.get(resumeId ? `${base}/${resumeId}` : `${base}?page=${page}`);
      if (resumeId) setDetail(response.data);
      else setResult(response.data);
    } catch {
      setResult(null);
      setError('이력서를 불러오지 못했습니다. 공개 상태와 열람 권한을 확인해 주세요.');
    } finally {
      setBusy(false);
    }
  };

  return <section className="company-panel company-resumes" aria-label="기업 이력서 열람">
    <h2>이력서 열람</h2>
    <p>공개 이력서와 우리 기업 공고에 지원한 회원의 이력서를 확인할 수 있습니다.</p>
    <button type="button" disabled={busy} onClick={() => load()}>이력서 목록 보기</button>
    {busy && <p role="status">이력서를 불러오는 중입니다.</p>}
    {error && <p role="alert">{error}</p>}
    {result && <>
      {result.content.length === 0 && <p>열람할 수 있는 이력서가 없습니다.</p>}
      <ul>{result.content.map((resume) => <li key={resume.id}>
        <button type="button" disabled={busy} onClick={() => load(0, resume.id)}>{resume.title}</button>
        {' · '}{resume.visibility === 'PUBLIC' ? '공개' : '지원 기업 한정'}
      </li>)}</ul>
      <button type="button" disabled={busy || result.first} onClick={() => load(result.number - 1)}>이전</button>
      <button type="button" disabled={busy || result.last} onClick={() => load(result.number + 1)}>다음</button>
    </>}
    {detail && <article>
      <h3>{detail.title}</h3>
      <button type="button" onClick={() => setDetail(null)}>이력서 닫기</button>
      <fieldset disabled className="company-resume-readonly">
        <BasicInfo data={detail.content.basicInfo || {}} onChange={noChange} />
        <TechStack data={detail.content.techStack || []} onChange={noChange} />
        <Projects data={detail.content.projects || []} onChange={noChange} />
        <Experience data={detail.content.experience || []} onChange={noChange} />
      </fieldset>
      <Attachments key={detail.id} resumeId={detail.id} companyId={company.companyId} />
    </article>}
  </section>;
}
