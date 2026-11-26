import { useCallback, useEffect, useRef, useState } from 'react';
import { adminApi, adminRequest } from '../api/adminApi';
import { useAdminAuth } from '../auth/AdminAuthContext';
import DetailModal from '../components/DetailModal';
import { paginationPage, paginationSize, readPaginationSession, savePaginationSession } from '../utils/paginationSession';
import { jobCardTags } from '../../../frontend/src/utils/jobCardTags.js';
import { jobCardRoles } from '../../../frontend/src/utils/jobCardRoles.js';

const statusText = { ACTIVE: '노출', HIDDEN: '숨김', CLOSED: '강제 마감' };
const actionText = { ACTIVE: '재활성', HIDDEN: '공고 숨김', CLOSED: '강제 마감', DELETED: '공고 삭제' };
const companyActions = { HIDDEN: 'SUSPEND', CLOSED: 'FORCE_CLOSE', DELETED: 'DELETE' };
const PAGINATION_KEY = 'admin.jobs.pagination';
const readFieldProposal = (value) => {
  try { const parsed = typeof value === 'string' ? JSON.parse(value) : value; return Array.isArray(parsed?.fields) ? parsed.fields : []; }
  catch { return []; }
};

export default function JobsPage() {
  const { admin } = useAdminAuth();
  const [initialPagination] = useState(() => readPaginationSession(PAGINATION_KEY));
  const initialSearch = typeof initialPagination.search === 'string' ? initialPagination.search.slice(0, 100) : '';
  const [keyword, setKeyword] = useState(initialSearch);
  const [search, setSearch] = useState(initialSearch);
  const [status, setStatus] = useState(() => Object.hasOwn(statusText, initialPagination.status) ? initialPagination.status : '');
  const [page, setPage] = useState(() => paginationPage(initialPagination.page));
  const [size, setSize] = useState(() => paginationSize(initialPagination.size));
  const [pageInput, setPageInput] = useState(() => String(paginationPage(initialPagination.page) + 1));
  const [pageError, setPageError] = useState('');
  const requestSequence = useRef(0);
  const [result, setResult] = useState(null);
  const [selected, setSelected] = useState(null);
  const [publicTags, setPublicTags] = useState(null);
  const [reason, setReason] = useState('');
  const [newEndDate, setNewEndDate] = useState('');
  const [pendingStatus, setPendingStatus] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [aiPreview, setAiPreview] = useState(null);
  const [aiResult, setAiResult] = useState(null);
  const [aiHistory, setAiHistory] = useState([]);
  const [aiConfirmed, setAiConfirmed] = useState(false);
  const [reviewDraft, setReviewDraft] = useState(null);
  const [salaryPreview, setSalaryPreview] = useState(null);
  const [roleHistory, setRoleHistory] = useState([]);
  const [roleLoaded, setRoleLoaded] = useState(false);
  const [roleNotice, setRoleNotice] = useState('');
  const [fieldHistory, setFieldHistory] = useState([]);
  const [fieldLoaded, setFieldLoaded] = useState(false);
  const [fieldNotice, setFieldNotice] = useState('');

  useEffect(() => { savePaginationSession(PAGINATION_KEY, { page, size, search, status }); }, [page, size, search, status]);

  const load = useCallback(async () => {
    const sequence = ++requestSequence.current;
    setError('');
    try {
      const params = { page: String(page), size: String(size) };
      if (search) params.keyword = search;
      if (status) params.status = status;
      const data = (await adminApi.jobs(params)).data;
      if (sequence !== requestSequence.current) return;
      if (page > 0 && page >= data.totalPages) {
        setPage(Math.max(0, data.totalPages - 1)); setPageInput(String(Math.max(1, data.totalPages))); return;
      }
      setResult(data);
    } catch (cause) { if (sequence === requestSequence.current) setError(cause.message); }
  }, [page, size, search, status]);

  useEffect(() => { const sequence = requestSequence; load(); return () => { sequence.current++; }; }, [load]);

  const goToPage = (next) => {
    if (!result || next < 0 || next >= result.totalPages) return;
    setPageError(''); setPageInput(String(next + 1)); setPage(next);
  };
  const submitPage = (event) => {
    event.preventDefault();
    const target = Number(pageInput);
    if (!/^\d+$/.test(pageInput) || !Number.isSafeInteger(target) || target < 1 || target > result.totalPages) {
      setPageError(`1부터 ${result.totalPages}까지 입력해 주세요.`); return;
    }
    goToPage(target - 1);
  };

  const selectJob = async (id) => {
    setPublicTags(null);
    setError(''); setReason(''); setNewEndDate(''); setPendingStatus(null);
    setAiPreview(null); setAiResult(null); setAiHistory([]); setAiConfirmed(false); setReviewDraft(null); setSalaryPreview(null);
    setRoleHistory([]); setRoleLoaded(false); setRoleNotice('');
    setFieldHistory([]); setFieldLoaded(false); setFieldNotice('');
    try {
      const job = (await adminApi.job(id)).data;
      if (job.sourcePlatform === 'COMPANY_DIRECT') {
        job.companyJob = (await adminRequest(`/jobs/company-submissions/published/${id}`)).data;
      }
      setSelected(job);
    }
    catch (cause) { setError(cause.message); }
  };

  useEffect(() => {
    if (!selected) return;
    const controller = new AbortController();
    const base = (import.meta.env.VITE_ADMIN_API_BASE_URL || '/api/v1/admin').replace(/\/$/, '').replace(/\/admin$/, '');
    fetch(`${base}/jobs/${selected.id}?_fresh=${Date.now()}`, { signal: controller.signal, cache: 'no-store', credentials: 'omit' })
      .then(response => {
        if (response.status === 404) return { unavailable: true };
        if (!response.ok) throw new Error('public job unavailable');
        return response.json();
      })
      .then(job => setPublicTags(job.unavailable ? { unavailable: true } : jobCardTags(job)))
      .catch(error => { if (error.name !== 'AbortError') setPublicTags({ error: true }); });
    return () => controller.abort();
  }, [selected]);

  const loadRoleHistory = async () => {
    if (!selected || busy) return;
    setBusy(true); setError('');
    try { setRoleHistory(await adminRequest(`/jobs/${selected.id}/ai-role/history`)); setRoleLoaded(true); }
    catch (cause) { setError(cause.message); }
    finally { setBusy(false); }
  };

  const loadFields = async () => {
    if (!selected || busy) return;
    setBusy(true); setError('');
    try { setFieldHistory(await adminRequest(`/jobs/${selected.id}/ai-fields/history`)); setFieldLoaded(true); }
    catch (cause) { setError(cause.message); }
    finally { setBusy(false); }
  };

  const analyzeFields = async () => {
    if (!selected || busy) return;
    setBusy(true); setError(''); setFieldNotice('');
    try {
      const outcome = await adminRequest(`/jobs/${selected.id}/ai-fields/analyze`, { method: 'POST' });
      const messages = { STORED: 'AI 분야 제안을 저장했습니다. 원문 근거를 확인해 주세요.', STORED_PARTIAL: '근거가 부족한 계약직 제안을 제외하고 나머지 분야를 저장했습니다. 원문을 확인해 주세요.', DISABLED: 'AI 분야 분석이 비활성화되어 있습니다.',
        NOT_ELIGIBLE: '공고가 비활성, 숨김 또는 마감되어 분석할 수 없습니다.', SOURCE_NOT_ALLOWED: '이 공고의 출처는 직무 분야 분석에 허용되지 않았습니다.',
        INPUT_UNUSABLE: '공고 입력 정보나 본문을 분석에 사용할 수 없습니다.', INPUT_TOO_LARGE: '업무와 자격을 추출해도 AI 입력 한도를 초과합니다.',
        JOB_CHANGED: '공고가 변경되어 제안을 저장하지 않았습니다.', ALREADY_CLAIMED: '같은 입력은 처리됐거나 처리 중입니다.', DAILY_LIMIT: '오늘의 AI 분석 한도에 도달했습니다.',
        INVALID_PROPOSAL: 'AI 제안의 분야 또는 원문 근거를 확인할 수 없어 저장하지 않았습니다. 원문을 확인하고 다시 분석해 주세요.' };
      setFieldNotice(messages[outcome.status] || '분석 상태를 확인해 주세요.');
      setFieldHistory(await adminRequest(`/jobs/${selected.id}/ai-fields/history`)); setFieldLoaded(true);
    } catch (cause) { setError(cause.message); }
    finally { setBusy(false); }
  };

  const reviewFields = async (item, action) => {
    if (!selected || busy) return;
    setBusy(true); setError('');
    try {
      await adminRequest(`/jobs/${selected.id}/ai-fields/${item.id}/${action}`, { method: 'POST',
        body: JSON.stringify({ expectedVersion: selected.version, inputHash: item.input_hash }) });
      setFieldNotice(action === 'approve' ? '직무 분야를 승인했습니다.' : '직무 분야를 거절했습니다.');
      setFieldHistory(await adminRequest(`/jobs/${selected.id}/ai-fields/history`)); setFieldLoaded(true);
      setPublicTags(null); setSelected(current => current?.id === selected.id ? { ...current } : current);
    } catch (cause) { setError(cause.status === 409 ? '공고 또는 제안이 변경되었습니다. 다시 확인해 주세요.' : cause.message); }
    finally { setBusy(false); }
  };

  const analyzeRole = async () => {
    if (!selected || busy) return;
    setBusy(true); setError(''); setRoleNotice('');
    try {
      const result = await adminRequest(`/jobs/${selected.id}/ai-role/analyze`, { method: 'POST' });
      const messages = {
        STORED: 'AI 직무 제안을 저장했습니다. 원문 근거를 확인한 후 승인할 수 있습니다.',
        DISABLED: 'AI 직무 분석이 비활성화되어 있습니다.',
        NOT_ELIGIBLE: '공고가 비활성, 숨김 또는 마감되어 분석할 수 없습니다.',
        SOURCE_NOT_ALLOWED: '이 공고의 출처는 직무 제안 분석에 허용되지 않았습니다.',
        INPUT_UNUSABLE: '공고 입력 정보나 본문을 분석에 사용할 수 없습니다.',
        INPUT_TOO_LARGE: '업무와 자격을 추출해도 AI 입력 한도를 초과합니다.',
        JOB_CHANGED: '공고가 변경되어 제안을 저장하지 않았습니다. 다시 확인해 주세요.',
        ALREADY_CLAIMED: '이 입력은 이미 처리했거나 처리 중입니다.',
        DAILY_LIMIT: '오늘의 AI 직무 분석 한도에 도달했습니다.',
      };
      setRoleNotice(messages[result.status] || '분석 상태를 확인해 주세요.');
      setRoleHistory(await adminRequest(`/jobs/${selected.id}/ai-role/history`)); setRoleLoaded(true);
    } catch (cause) { setError(cause.message); }
    finally { setBusy(false); }
  };

  const reviewRole = async (item, action) => {
    if (!selected || busy) return;
    setBusy(true); setError(''); setRoleNotice('');
    try {
      await adminRequest(`/jobs/${selected.id}/ai-role/${item.id}/${action}`, {
        method: 'POST', body: JSON.stringify({ expectedVersion: selected.version, inputHash: item.input_hash }),
      });
      setRoleNotice(action === 'approve' ? '직무 제안을 승인했습니다.' : '직무 제안을 거절했습니다.');
      setRoleHistory(await adminRequest(`/jobs/${selected.id}/ai-role/history`)); setRoleLoaded(true);
      setPublicTags(null); setSelected(current => current?.id === selected.id ? { ...current } : current);
    } catch (cause) {
      setError(cause.status === 409 ? '공고 또는 제안이 변경되었습니다. 상세를 다시 열어 확인해 주세요.' : cause.message);
    } finally { setBusy(false); }
  };

  const previewSalary = async () => {
    if (!selected || busy) return;
    setBusy(true); setError(''); setSalaryPreview(null);
    try { setSalaryPreview(await adminRequest(`/jobs/${selected.id}/stripe-salary/preview`)); }
    catch (cause) { setError(cause.message); }
    finally { setBusy(false); }
  };

  const applyAiReview = async () => {
    if (!selected || !reviewDraft || busy) return;
    setBusy(true); setError('');
    try {
      const applied = await adminRequest(`/jobs/${selected.id}/ai-metadata/${reviewDraft.id}/apply`, {
        method: 'POST', body: JSON.stringify({ expectedVersion: selected.version,
          experienceCategory: reviewDraft.category || null, experienceLabel: reviewDraft.experience.trim() || null,
          location: reviewDraft.location.trim() || null }),
      });
      setSelected({ ...selected, version: applied.version }); setReviewDraft(null);
      setAiHistory(await adminRequest(`/jobs/${selected.id}/ai-metadata/history`));
      setAiResult({ status: 'APPLIED' });
    } catch (cause) {
      setError(cause.status === 409 ? '공고 또는 제안이 변경되었습니다. 상세를 다시 열어 확인해 주세요.' : cause.message);
    } finally { setBusy(false); }
  };

  const prepareAi = async () => {
    if (!selected || busy) return;
    setBusy(true); setError(''); setAiConfirmed(false); setAiResult(null);
    try {
      const [preview, history] = await Promise.all([
        adminRequest(`/jobs/${selected.id}/ai-metadata/preview`),
        adminRequest(`/jobs/${selected.id}/ai-metadata/history`),
      ]);
      setAiPreview(preview); setAiHistory(history);
    } catch (cause) { setError(cause.message); }
    finally { setBusy(false); }
  };

  const analyzeAi = async () => {
    if (!selected || !aiPreview || !aiConfirmed || busy) return;
    setBusy(true); setError('');
    try {
      const result = await adminRequest(`/jobs/${selected.id}/ai-metadata/analyze`, {
        method: 'POST', body: JSON.stringify({ inputHash: aiPreview.inputHash, confirmed: true }),
      });
      setAiResult(result); setAiConfirmed(false);
      setAiHistory(await adminRequest(`/jobs/${selected.id}/ai-metadata/history`));
    } catch (cause) {
      setError(cause.code === 'JOB_AI_DISABLED' ? 'AI 공고 분석이 비활성화되어 있습니다.'
        : cause.code === 'SOURCE_NOT_ALLOWED' ? '이 출처는 AI 분석을 허용하도록 설정되지 않았습니다.'
          : cause.code === 'JOB_METADATA_ALREADY_KNOWN' ? '경력과 근무지 정보가 이미 확인된 공고입니다.'
          : cause.code === 'PREVIEW_CHANGED' || cause.code === 'JOB_CHANGED' ? '공고가 변경되었습니다. 전송 내용을 다시 확인해 주세요.'
            : cause.message);
    } finally { setBusy(false); }
  };

  const changeStatus = async () => {
    if (!selected || !pendingStatus || busy) return;
    if (!reason.trim()) {
      setError('변경 사유를 입력해 주세요.');
      document.getElementById('job-moderation-reason')?.focus();
      return;
    }
    if (needsNewEndDate && !newEndDate) {
      setError('새 마감일을 입력해 주세요.');
      document.getElementById('job-new-end-date')?.focus();
      return;
    }
    setBusy(true); setError('');
    try {
      let updated;
      if (selected.sourcePlatform === 'COMPANY_DIRECT') {
        const companyJob = (await adminRequest(`/jobs/company-submissions/${selected.companyJob.id}/review`, {
          method: 'POST', body: JSON.stringify({ action: companyActions[pendingStatus], expectedVersion: selected.companyJob.version, reason: reason.trim() }),
        })).data;
        updated = { ...selected, companyJob, moderationStatus: companyJob.liveStatus, active: false };
      } else updated = (await adminApi.moderateJob(selected.id, {
        status: pendingStatus, expectedVersion: selected.version, reason: reason.trim(),
        ...(pendingStatus === 'ACTIVE' && newEndDate ? { newEndDate } : {}),
      })).data;
      setSelected(updated); setReason(''); setNewEndDate(''); setPendingStatus(null);
      await load();
    } catch (cause) {
      setError(cause.status === 409 ? '공고 상태가 변경되었습니다. 상세를 다시 열어 확인하세요.'
        : cause.code === 'NEW_END_DATE_REQUIRED' ? '새 마감일을 입력해 주세요.' : cause.message);
    } finally { setBusy(false); }
  };

  const actions = selected?.sourcePlatform === 'COMPANY_DIRECT'
    ? selected.companyJob?.state === 'DELETED' ? [] : [
      ...(selected.companyJob?.state !== 'SUSPENDED' ? ['HIDDEN'] : []),
      ...(selected.companyJob?.state !== 'CLOSED' ? ['CLOSED'] : []), 'DELETED',
    ] : selected?.moderationStatus === 'ACTIVE' ? ['HIDDEN', 'CLOSED']
    : selected?.moderationStatus === 'HIDDEN' ? ['ACTIVE', 'CLOSED']
      : selected?.moderationStatus === 'CLOSED' ? ['ACTIVE'] : [];
  const needsNewEndDate = pendingStatus === 'ACTIVE' && selected
    && (!selected.active || selected.endDate < new Date().toLocaleDateString('sv-SE'));
  const pageCount = result?.totalPages ?? 0;
  const firstPage = Math.max(0, Math.min(page - 2, pageCount - 5));
  const pageNumbers = Array.from({ length: Math.min(5, pageCount) }, (_, index) => firstPage + index);

  return <section className="users-page">
    <div className="page-heading"><div><p className="eyebrow">JOB POSTS</p><h2>공고 관리</h2>
      <p>공고 숨김·강제 마감·재활성을 관리합니다. 수집원이 공고를 다시 마감으로 판단하면 비활성화될 수 있습니다.</p></div></div>
    <form className="user-filters dashboard-panel" onSubmit={(event) => {
      event.preventDefault(); setPage(0); setPageInput('1'); setPageError(''); setSearch(keyword.trim());
    }}>
      <label>공고 또는 기업명<input value={keyword} onChange={(event) => setKeyword(event.target.value)} maxLength={100} /></label>
      <label>관리 상태<select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); setPageInput('1'); setPageError(''); }}>
        <option value="">전체</option>{Object.entries(statusText).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
      <button className="secondary-button" type="submit">검색</button>
    </form>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="dashboard-panel user-list">
      {!result ? <p role="status">공고를 불러오는 중입니다.</p> : result.content.length === 0
        ? <p>조건에 맞는 공고가 없습니다.</p> : <div className="table-scroll"><table>
          <thead><tr><th>공고</th><th>기업</th><th>직무 태그</th><th>관리 상태</th><th>마감일</th><th></th></tr></thead>
          <tbody>{result.content.map((job) => <tr key={job.id}>
            <td>{job.title}</td><td>{job.companyName}</td><td><div className="job-role-tags">{jobCardRoles(job).map(role => <span key={role}>{role}</span>)}</div></td><td>{statusText[job.moderationStatus]}</td>
            <td>{job.endDate || '—'}</td><td><button type="button" onClick={() => selectJob(job.id)}>상세</button></td>
          </tr>)}</tbody></table></div>}
      {result && <nav className="seed-pagination" aria-label="공고 목록 페이지">
        <div className="seed-pagination-summary"><span>전체 <strong>{result.totalElements.toLocaleString()}</strong>건</span>
          <span>{pageCount ? page + 1 : 0} / {pageCount.toLocaleString()} 페이지</span>
          <label>페이지당 <select value={size} onChange={(event) => {
            setPage(0); setPageInput('1'); setPageError(''); setSize(Number(event.target.value));
          }}><option value="20">20개</option><option value="50">50개</option><option value="100">100개</option></select></label>
        </div>
        <div className="seed-pagination-actions">
          <button type="button" aria-label="첫 페이지" disabled={page === 0 || !pageCount} onClick={() => goToPage(0)}>«</button>
          <button type="button" aria-label="이전 페이지" disabled={page === 0 || !pageCount} onClick={() => goToPage(page - 1)}>‹</button>
          {firstPage > 0 && <span aria-hidden="true">…</span>}
          {pageNumbers.map(number => <button type="button" key={number} aria-label={`${number + 1}페이지`}
            aria-current={number === page ? 'page' : undefined} onClick={() => goToPage(number)}>{number + 1}</button>)}
          {firstPage + pageNumbers.length < pageCount && <span aria-hidden="true">…</span>}
          <button type="button" aria-label="다음 페이지" disabled={page + 1 >= pageCount} onClick={() => goToPage(page + 1)}>›</button>
          <button type="button" aria-label="마지막 페이지" disabled={page + 1 >= pageCount} onClick={() => goToPage(pageCount - 1)}>»</button>
        </div>
        <form className="seed-pagination-jump" onSubmit={submitPage} noValidate>
          <label htmlFor="job-page-number">페이지 이동</label>
          <input id="job-page-number" type="number" min="1" max={pageCount || 1} inputMode="numeric"
            value={pageInput} disabled={!pageCount} onChange={(event) => { setPageInput(event.target.value); setPageError(''); }}
            aria-invalid={Boolean(pageError)} aria-describedby={pageError ? 'job-page-error' : undefined} />
          <button type="submit" disabled={!pageCount}>이동</button>
          {pageError && <span id="job-page-error" role="alert">{pageError}</span>}
        </form>
      </nav>}
    </div>
    {selected && <DetailModal title="공고 상세" onClose={() => setSelected(null)} closeDisabled={busy}>
      {error && <p className="form-error" role="alert">{error}</p>}
      <dl><div><dt>공고명</dt><dd>{selected.title}</dd></div><div><dt>기업</dt><dd>{selected.companyName}</dd></div>
        <div><dt>관리 상태</dt><dd>{selected.companyJob?.state === 'DELETED' ? '삭제됨' : statusText[selected.moderationStatus]}</dd></div>
        <div><dt>수집 활성</dt><dd>{selected.active ? '활성' : '비활성'}</dd></div>
        <div><dt>마감일</dt><dd>{selected.endDate}</dd></div>
        <div><dt>출처</dt><dd>{selected.sourcePlatform}</dd></div>
        <div><dt>원본</dt><dd><a href={selected.originalUrl} target="_blank" rel="noreferrer">원본 공고 확인</a></dd></div></dl>
      <section className="moderation-form" aria-label="사용자 화면 태그">
        <h3>사용자 화면 태그</h3>
        {!publicTags ? <p role="status">태그를 불러오는 중…</p>
          : publicTags.unavailable ? <p>현재 공개되지 않은 공고입니다.</p>
          : publicTags.error ? <p role="alert">사용자 화면 태그를 불러오지 못했습니다.</p>
          : <p>{publicTags.label}: {publicTags.tags.length ? publicTags.tags.join(' · ') : '표시된 태그 없음'}</p>}
      </section>
      {selected.sourcePlatform === 'COMPANY_DIRECT' && <p>기업 직접 등록 공고입니다. 이 화면에서 처리한 내용은 기업 공고 검수의 처리 이력에도 기록됩니다.</p>}
      {selected.companyJob?.state === 'DELETED' && <p>삭제 처리된 공고입니다. 공고와 감사 기록은 보존되며 수정·재게시할 수 없습니다.</p>}
      {admin.role !== 'REVIEWER' && selected.sourcePlatform === 'GREENHOUSE' && selected.originalUrl?.startsWith('https://stripe.com/') &&
        <section className="moderation-form" aria-label="급여 원문 미리보기">
          <h3>급여 원문 미리보기</h3>
          <p>Stripe 원문과 기존 급여 정보를 비교합니다. 확인만 하며 공고를 변경하지 않습니다.</p>
          <button type="button" className="secondary-button" disabled={busy} onClick={previewSalary}>급여 원문 확인</button>
          {salaryPreview && <div>
            <p>상태: {salaryPreview.status === 'PRESENT' ? '원문 확인됨' : '원문 확인 불가'}</p>
            <p>기존 정보: {salaryPreview.previousText || '없음'}</p>
            {salaryPreview.paragraphs?.map((paragraph, index) => <p key={index}>{paragraph}</p>)}
            <p>원본 해시: {salaryPreview.sourceHash || '없음'}</p>
            <a href={salaryPreview.sourceUrl} target="_blank" rel="noreferrer">Stripe 원문 열기</a>
          </div>}
        </section>}
      {admin.role !== 'REVIEWER' && <section className="moderation-form" aria-label="AI 직무 분야 검토">
        <h3>AI 직무 분야 검토</h3>
        <p>공고의 업무와 자격을 바탕으로 공통 직무 분야를 제안합니다. 원문을 확인하고 승인해 주세요.</p>
        <div className="job-actions">
          <button className="secondary-button" type="button" disabled={busy} onClick={loadFields}>직무 분야 조회</button>
          <button className="primary-button" type="button" disabled={busy} onClick={analyzeFields}>AI 직무 분야 분석</button>
        </div>
        {fieldNotice && <p role="status">{fieldNotice}</p>}
        {fieldLoaded && fieldHistory.length === 0 && <p>저장된 분야 제안이 없습니다.</p>}
        {fieldHistory.map(item => <div key={item.id}>
          {readFieldProposal(item.fields_json).map(field => <p key={field.field}>분야: {field.field} · 원문 근거: {field.evidence}</p>)}
          {readFieldProposal(item.fields_json).length === 0 && <p>판단 보류</p>}
          <p>검토 상태: {item.review_status} · 공고 버전 {item.job_version}</p>
          {item.review_status === 'PENDING_REVIEW' && item.job_version === selected.version && <div className="job-actions">
            <button className="primary-button" type="button" disabled={busy} onClick={() => reviewFields(item, 'approve')}>분야 승인</button>
            <button className="secondary-button" type="button" disabled={busy} onClick={() => reviewFields(item, 'reject')}>분야 거절</button>
          </div>}
        </div>)}
      </section>}
      {admin.role !== 'REVIEWER' && <section className="moderation-form" aria-label="AI 직무 제안 검토">
        <h3>AI 직무 제안 검토</h3>
        <p>공개 공고의 제목과 본문을 분석해 직무를 제안합니다. 기존 직무 분류가 우선하며, 제안은 검토 후 승인할 수 있습니다.</p>
        <div className="job-actions">
          <button className="secondary-button" type="button" disabled={busy} onClick={loadRoleHistory}>직무 제안 조회</button>
          <button className="primary-button" type="button" disabled={busy} onClick={analyzeRole}>AI 직무 분석</button>
        </div>
        {roleNotice && <p role="status">{roleNotice}</p>}
        {roleLoaded && roleHistory.length === 0 && <p>저장된 직무 제안이 없습니다.</p>}
        {roleHistory.map((item) => <div key={item.id}>
          <p>직무 제안: {item.suggested_role || '판단 보류'}</p>
          <p>원문 근거: {item.source_evidence || '없음'}</p>
          <p>검토 상태: {item.review_status} · 공고 버전 {item.job_version}</p>
          {item.review_status === 'PENDING_REVIEW' && item.job_version === selected.version && <div className="job-actions">
            <button className="primary-button" type="button" disabled={busy} onClick={() => reviewRole(item, 'approve')}>직무 승인</button>
            <button className="secondary-button" type="button" disabled={busy} onClick={() => reviewRole(item, 'reject')}>직무 거절</button>
          </div>}
        </div>)}
      </section>}
      {admin.role !== 'REVIEWER' && <section className="moderation-form" aria-label="AI 공고 조건 검토">
        <h3>AI 공고 조건 검토</h3>
        <p>경력·근무지 제안을 원문과 비교하고 검토한 조건을 검색 필터에 반영할 수 있습니다.</p>
        <button className="secondary-button" type="button" disabled={busy} onClick={prepareAi}>전송 내용 확인</button>
        {aiPreview && <div>
          <p>출처: {aiPreview.sourceKey} · {aiPreview.sourceAllowed ? 'AI 분석 허용 출처' : 'AI 분석 허용 미설정'}</p>
          <p>수집된 원본 근무지: {aiPreview.sourceLocation || '정보 없음'}</p>
          <p>아래 내용이 OpenAI에 전송됩니다. 공고 출처의 전송·분석 허용 여부와 내용을 확인해 주세요.</p>
          {aiPreview.truncated && <p>공고가 길어 앞부분만 전송됩니다. 빠진 내용은 원문에서 확인해 주세요.</p>}
          <textarea aria-label="AI에 전송할 공고 내용" value={aiPreview.content} readOnly rows={8} />
          <label><input type="checkbox" checked={aiConfirmed} disabled={!aiPreview.sourceAllowed || busy}
            onChange={(event) => setAiConfirmed(event.target.checked)} /> 전송 내용과 출처의 이용 허용 범위를 확인했습니다.</label>
          <button className="primary-button" type="button" disabled={!aiPreview.sourceAllowed || !aiConfirmed || busy} onClick={analyzeAi}>
            {busy ? '분석 중…' : '확인하고 AI 분석'}</button>
        </div>}
        {aiResult && <p role="status">{aiResult.status === 'APPLIED' ? '검토한 조건을 공고와 검색 필터에 반영했습니다.' : 'AI 제안을 저장했습니다. 검색 필터에는 아직 반영되지 않았습니다.'}</p>}
        {aiHistory.length > 0 && <details><summary>이전 AI 제안 {aiHistory.length}건</summary>
          {aiHistory.map((item) => <div key={item.id}>
            <p>경력 제안: {item.experience_suggestion || '확인 필요'}</p><p>근거: {item.experience_quote || '없음'}</p>
            <p>근무지 제안: {item.location_suggestion || '확인 필요'}</p><p>근거: {item.location_quote || '없음'}</p>
            <p>검토 상태: {item.review_status} · 공고 버전 {item.job_version}</p>
            {item.review_status === 'APPLIED' && <p>반영한 조건: {[item.applied_experience_label, item.applied_location].filter(Boolean).join(' · ')}</p>}
            {item.review_status === 'NEEDS_REVIEW' && item.job_version === selected.version &&
              <button className="secondary-button" type="button" disabled={busy} onClick={() => setReviewDraft({
                id: item.id, category: '', experience: '', location: '', hasExperience: Boolean(item.experience_quote), hasLocation: Boolean(item.location_quote),
              })}>원문 확인 후 조건 반영</button>}
            {reviewDraft?.id === item.id && <div role="group" aria-label="검토한 공고 조건 입력">
              <p>원문 근거를 확인하고 반영할 항목만 입력해 주세요. 근거에 없는 조건은 입력하지 마세요.</p>
              {reviewDraft.hasExperience && <><label>경력 분류<select value={reviewDraft.category} onChange={(event) => setReviewDraft({ ...reviewDraft, category: event.target.value })}>
                <option value="">반영하지 않음</option><option value="ENTRY">신입</option><option value="EXPERIENCED">경력</option><option value="BOTH">신입·경력</option><option value="ANY">경력무관</option>
              </select></label><label>경력 표시<input value={reviewDraft.experience} maxLength={255} onChange={(event) => setReviewDraft({ ...reviewDraft, experience: event.target.value })} placeholder="예: 경력 5년 이상" /></label></>}
              {reviewDraft.hasLocation && <label>근무지 표시<input value={reviewDraft.location} maxLength={255} onChange={(event) => setReviewDraft({ ...reviewDraft, location: event.target.value })} placeholder="예: 미국 뉴욕, 원격" /></label>}
              <button className="primary-button" type="button" disabled={busy || (!reviewDraft.category && !reviewDraft.location.trim()) || Boolean(reviewDraft.category) !== Boolean(reviewDraft.experience.trim())} onClick={applyAiReview}>확인한 조건 반영</button>
              <button className="secondary-button" type="button" disabled={busy} onClick={() => setReviewDraft(null)}>취소</button>
            </div>}
          </div>)}
        </details>}
      </section>}
      {admin.role !== 'REVIEWER' && actions.length > 0 && <div className="moderation-form">
        {!pendingStatus ? <div className="job-actions">{actions.map((action) =>
          <button className="secondary-button" key={action} type="button" disabled={busy}
            onClick={() => { setReason(''); setPendingStatus(action); }}>{actionText[action]}</button>)}</div>
          : <div className="moderation-confirm" role="group" aria-label="공고 상태 변경 확인">
            <label htmlFor="job-moderation-reason">변경 사유
              <textarea id="job-moderation-reason" value={reason}
                onChange={(event) => { setReason(event.target.value); setError(''); }} maxLength={500} rows={3} required />
            </label>
            {pendingStatus === 'ACTIVE' && <label htmlFor="job-new-end-date">새 마감일 {needsNewEndDate ? '(필수)' : '(선택)'}
              <input id="job-new-end-date" type="date" value={newEndDate}
                min={new Date().toLocaleDateString('sv-SE')}
                onChange={(event) => { setNewEndDate(event.target.value); setError(''); }} required={needsNewEndDate} />
            </label>}
            <p>{selected.title} 공고를 {actionText[pendingStatus]} 처리합니다. 변경 사유가 감사 기록에 남습니다.</p>
            {pendingStatus === 'DELETED' && <p>공개 노출을 중단하고 공고와 기록을 보존합니다. 삭제 후에는 수정·재게시할 수 없습니다.</p>}
            <button className="primary-button" type="button" disabled={busy} onClick={changeStatus}>
              {busy ? '처리 중…' : '변경 확정'}</button>
            <button className="secondary-button" type="button" disabled={busy}
              onClick={() => { setReason(''); setPendingStatus(null); }}>취소</button>
          </div>}</div>}
    </DetailModal>}
  </section>;
}
