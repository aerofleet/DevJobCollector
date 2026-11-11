import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { companyJobsApi } from '../../api/companyJobsApi';
import '../../styles/CompanyJobs.css';

const EMPTY = { title: '', jobCategory: '', experience: '', location: '', hireType: '', workArrangement: '', koreanLevel: '', englishLevel: '', techStacks: '', description: '', endDate: '', applyUrl: '', recruiterNote: '', referralSource: '' };
const editableContent = (content) => Object.fromEntries(Object.keys(EMPTY).map((key) => [key, content?.[key] ?? '']));
const STATES = { DRAFT: '임시저장', IN_REVIEW: '검수 대기', CHANGES_REQUESTED: '수정 요청', PUBLISHED: '승인 완료', CLOSED: '마감', SUSPENDED: '게시 중단', DELETED: '삭제됨' };
const LIVE = { UNPUBLISHED: '미게시', ACTIVE: '게시 중', CLOSED: '마감', HIDDEN: '게시 중단' };
const FIELDS = [
  ['title', '공고 제목', '예: 백엔드 개발자 (Java/Spring)'],
  ['jobCategory', '직무', '예: 백엔드 개발자'],
  ['experience', '경력', '예: 신입 또는 경력 3년 이상'],
  ['location', '근무지', '예: 서울 강남구 · 주 2일 재택'],
  ['hireType', '고용형태', ''],
  ['workArrangement', '근무형태 (선택)', ''],
  ['koreanLevel', '한국어 수준 (선택)', ''],
  ['englishLevel', '영어 수준 (선택)', ''],
  ['techStacks', '기술스택 (쉼표로 구분)', '예: Java, Spring Boot, MySQL'],
  ['endDate', '마감일', ''],
  ['applyUrl', '지원 URL (https)', '예: https://careers.example.com/jobs/123'],
];
const CHOICES = {
  hireType: ['정규직', '계약직', '인턴', '파트타임', '프리랜서', '기타'],
  workArrangement: ['출근', '하이브리드', '원격', '협의 가능'],
  koreanLevel: ['필수 아님', '기초', '일상 회화', '비즈니스', '유창', '원어민/이중 언어'],
  englishLevel: ['필수 아님', '기초', '일상 회화', '비즈니스', '유창', '원어민/이중 언어'],
};
const REFERRAL_CHOICES = [
  ['search', '구글 / 검색 엔진'], ['ai-assistant', 'AI 어시스턴트 (ChatGPT, Claude, Perplexity 등)'],
  ['linkedin', '링크드인'], ['social-media', 'X / 소셜 미디어'], ['discord', '디스코드 / 커뮤니티'],
  ['newsletter', '뉴스레터'], ['referral', '지인 추천 / 입소문'], ['event', '행사 / 컨퍼런스'], ['other', '기타'],
];
const DESCRIPTION_TEMPLATE = '[팀과 포지션 소개]\n\n[담당 업무]\n- \n\n[필수 자격]\n- \n\n[우대 사항]\n- \n\n[근무 조건]\n- \n\n[급여 및 복리후생]\n- \n\n[채용 절차 및 지원 안내]\n- ';
const DESCRIPTION_EXAMPLE = '예: 담당 업무, 필수 자격, 우대 사항, 근무 조건, 급여 및 채용 절차를 구분해 작성해 주세요.';
const failure = (e) => e.response?.status === 409 ? '다른 작업으로 공고가 변경되었습니다. 목록을 새로고침한 뒤 다시 열어주세요.'
  : e.response?.status === 403 ? '기업 인증 상태와 공고 관리 권한을 확인해주세요.'
    : e.response?.status === 400 ? '필수 항목, 마감일, 기술스택 및 https 지원 URL을 확인해주세요.' : '처리하지 못했습니다. 잠시 후 다시 시도해주세요.';

export default function CompanyJobs({ company }) {
  const [result, setResult] = useState(null);
  const [page, setPage] = useState(0);
  const [editor, setEditor] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const [preview, setPreview] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const canWrite = company.membershipStatus === 'ACTIVE' && ['OWNER', 'ADMIN', 'RECRUITER'].includes(company.role);
  const hasUnsavedChanges = editor !== null && (!editor.id || Object.keys(EMPTY).some((key) => form[key] !== editableContent(editor.content)[key]));
  const load = useCallback(async () => {
    try { setResult(await companyJobsApi.list(company.companyId, page)); }
    catch (e) { setError(failure(e)); }
  }, [company.companyId, page]);
  useEffect(() => { load(); }, [load]);

  const openEditor = (job, copy = false) => {
    setEditor(copy || !job ? { id: null } : job);
    setForm(editableContent(job?.content));
    setPreview(false); setError(''); setNotice('');
  };
  const save = async (event, submitForReview = false) => {
    event?.preventDefault(); if (busy) return;
    setBusy(true); setError(''); setNotice('');
    let saved = null;
    try {
      const content = { ...form, endDate: form.endDate || null };
      saved = editor.id ? await companyJobsApi.edit(company.companyId, editor.id, editor.version, content)
        : await companyJobsApi.create(company.companyId, content);
      setEditor(saved); setForm(editableContent(saved.content));
      if (submitForReview) {
        await companyJobsApi.action(company.companyId, saved, 'submit');
        setEditor(null); setNotice('공고를 저장하고 검수를 요청했습니다. 승인 후 채용 목록에 게시됩니다.');
      } else setNotice('임시저장했습니다. 목록에서 바로 검수를 요청할 수 있습니다.');
      await load();
    } catch (e) {
      if (saved && submitForReview) {
        setNotice('공고는 임시저장되었습니다. 내용을 확인한 뒤 검수를 다시 요청해 주세요.');
        await load();
      }
      setError(failure(e));
    } finally { setBusy(false); }
  };
  const act = async (job, action) => {
    if (busy) return;
    setBusy(true); setError(''); setNotice('');
    try {
      await companyJobsApi.action(company.companyId, job, action);
      setEditor(null); setNotice(action === 'submit' ? '검수를 요청했습니다.' : '공고를 마감했습니다.'); await load();
    } catch (e) { setError(failure(e)); } finally { setBusy(false); }
  };
  const totalPages = result?.page?.totalPages ?? result?.totalPages ?? 1;
  return <section className="company-panel company-jobs" aria-label="기업 공고 관리">
    <div className="company-jobs-heading"><div><h2>채용 공고 관리</h2><p>채용 공고를 무료로 등록하고 검수를 요청하세요. 승인된 공고는 채용 목록에 게시됩니다.</p></div>
      {canWrite && <button type="button" disabled={busy} onClick={() => openEditor(null)}>새 공고 작성</button>}</div>
    {!canWrite && <p>조회 권한입니다. 공고 등록은 기업 관리자 또는 채용 담당자가 진행할 수 있습니다.</p>}
    {error && <p role="alert" className="company-feedback error">{error}</p>}
    {notice && <p role="status" className="company-feedback notice">{notice}</p>}
    {editor && <form className="company-job-editor" onSubmit={save}>
      <h3>{editor.id ? '공고 수정' : '무료 채용 공고 작성'}</h3><p>임시저장은 일부 항목만 입력해도 가능합니다. 저장하고 검수를 요청하면 관리자의 승인 후 게시됩니다. 검수 요청에는 선택 항목을 제외한 9개 항목이 필요합니다.</p>
      <p>게시 중인 공고를 수정하면 기존 승인본은 다음 승인까지 유지됩니다.</p>
      <fieldset disabled={busy}><div className="company-job-fields">{FIELDS.map(([key, label, placeholder]) => <label key={key} htmlFor={`cj-${key}`}>{label}
        {CHOICES[key] ? <select id={`cj-${key}`} value={form[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value })}>
          <option value="">선택해 주세요</option>
          {form[key] && !CHOICES[key].includes(form[key]) && <option value={form[key]}>{form[key]} (기존 입력값)</option>}
          {CHOICES[key].map(option => <option key={option} value={option}>{option}</option>)}
        </select> : <input id={`cj-${key}`} type={key === 'endDate' ? 'date' : 'text'} value={form[key]}
          placeholder={placeholder || undefined}
          maxLength={key === 'applyUrl' ? 2000 : key === 'techStacks' ? 1000 : 255}
          onChange={(e) => setForm({ ...form, [key]: e.target.value })} />}</label>)}</div>
        <label htmlFor="cj-description">상세내용 (일반 텍스트)<textarea id="cj-description" rows={10} maxLength={20000} value={form.description} placeholder={DESCRIPTION_EXAMPLE} aria-describedby="company-job-description-help"
          onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
        <p id="company-job-description-help" className="company-job-description-help">팀 소개, 담당 업무, 필수·우대 자격, 근무 조건, 급여·복리후생과 채용 절차를 확인해 주세요.</p>
        {!form.description.trim() && <button type="button" onClick={() => setForm({ ...form, description: DESCRIPTION_TEMPLATE })}>상세내용 작성 틀 넣기</button>}
        <label htmlFor="cj-recruiterNote">검토 메모 (선택)<textarea id="cj-recruiterNote" rows={3} maxLength={2000} value={form.recruiterNote} placeholder="예: 공고 검수 시 확인이 필요한 내용을 적어 주세요." aria-describedby="company-job-note-help" onChange={(e) => setForm({ ...form, recruiterNote: e.target.value })} /></label>
        <p id="company-job-note-help" className="company-job-description-help">기업 담당자와 검수 관리자가 확인하며, 공개 공고에는 표시되지 않습니다.</p>
        <label htmlFor="cj-referralSource">DevJobs를 어떻게 알게 되셨나요? (선택)
          <select id="cj-referralSource" value={form.referralSource} onChange={(e) => setForm({ ...form, referralSource: e.target.value })} aria-describedby="company-job-referral-help">
            <option value="">선택해 주세요</option>
            {form.referralSource && !REFERRAL_CHOICES.some(([value]) => value === form.referralSource) && <option value={form.referralSource}>{form.referralSource} (기존 입력값)</option>}
            {REFERRAL_CHOICES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </label>
        <p id="company-job-referral-help" className="company-job-description-help">공개 공고에는 표시되지 않습니다.</p>
        <div className="company-job-actions"><button type="submit">{busy ? '저장 중…' : '임시저장'}</button>
          <button type="button" className="company-job-submit" onClick={() => save(null, true)}>저장하고 검수 요청</button>
          <button type="button" onClick={() => setPreview(!preview)}>미리보기</button>
          <button type="button" onClick={() => setEditor(null)}>편집 닫기</button></div>
      </fieldset>
      {preview && <article className="company-job-preview" aria-label="공고 미리보기"><span>기업 직접 등록 · {company.displayName}</span>
        <h3>{form.title || '공고 제목'}</h3><p>{[form.jobCategory, form.experience, form.location, form.hireType, form.workArrangement].filter(Boolean).join(' · ')}</p>
        {(form.koreanLevel || form.englishLevel) && <p>{[form.koreanLevel && `한국어: ${form.koreanLevel}`, form.englishLevel && `영어: ${form.englishLevel}`].filter(Boolean).join(' · ')}</p>}
        <p>{form.techStacks}</p><div style={{ whiteSpace: 'pre-wrap' }}>{form.description}</div><p>마감일: {form.endDate || '미입력'}</p><p>지원: {form.applyUrl || '미입력'}</p></article>}
    </form>}
    <div className="company-job-actions"><button type="button" disabled={busy} onClick={() => { setError(''); load(); }}>목록 새로고침</button></div>
    {hasUnsavedChanges && <p id="company-job-submit-help">저장하지 않은 내용이 있습니다. 임시저장하거나 편집을 닫으면 검수를 요청할 수 있습니다.</p>}
    {!result && !error && <p role="status">공고를 불러오는 중입니다.</p>}
    {result?.content.length === 0 && <p>아직 등록한 공고가 없습니다.</p>}
    <div className="company-job-list">{result?.content.map((job) => <article key={job.id}>
      <div className="company-job-status">{STATES[job.state]} · {LIVE[job.liveStatus]}</div><h3>{job.content.title || '제목 없는 공고'}</h3>
      <p>마감일 {job.content.endDate || '미입력'} · 조회 {job.views} · 지원 링크 클릭 {job.applyClicks}</p>
      {job.reviewReason && <p>처리 사유: {job.reviewReason}</p>}
      <div className="company-job-actions">
        {job.liveStatus === 'ACTIVE' && <Link to={`/job/${job.publishedJobId}`}>게시 공고 보기</Link>}
        {canWrite && job.state !== 'DELETED' && <>
          {!['IN_REVIEW', 'CLOSED', 'SUSPENDED'].includes(job.state) && <button disabled={busy} onClick={() => openEditor(job)}>수정</button>}
          <button disabled={busy} onClick={() => openEditor(job, true)}>복사</button>
          {['DRAFT', 'CHANGES_REQUESTED'].includes(job.state) && <button disabled={busy || hasUnsavedChanges} aria-describedby={hasUnsavedChanges ? 'company-job-submit-help' : undefined} onClick={() => act(job, 'submit')}>검수 요청</button>}
          {!['CLOSED', 'SUSPENDED'].includes(job.state) && <button disabled={busy} onClick={() => act(job, 'close')}>공고 마감</button>}
        </>}
      </div>
    </article>)}</div>
    <p className="company-job-footnote">조회와 지원 링크 클릭은 참고 지표이며, 실제 지원 완료 건수가 아닙니다. 임시저장 후 바로 검수를 요청할 수 있습니다.</p>
    <div className="company-job-actions"><button disabled={page === 0 || busy} onClick={() => setPage(page - 1)}>이전</button>
      <span>{page + 1} / {Math.max(1, totalPages)}</span><button disabled={page + 1 >= totalPages || busy} onClick={() => setPage(page + 1)}>다음</button></div>
  </section>;
}
