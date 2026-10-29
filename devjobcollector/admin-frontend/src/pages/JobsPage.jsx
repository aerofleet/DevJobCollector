import { useCallback, useEffect, useState } from 'react';
import { adminApi, adminRequest } from '../api/adminApi';
import { useAdminAuth } from '../auth/AdminAuthContext';
import DetailModal from '../components/DetailModal';

const statusText = { ACTIVE: '노출', HIDDEN: '숨김', CLOSED: '강제 마감' };
const actionText = { ACTIVE: '재활성', HIDDEN: '공고 숨김', CLOSED: '강제 마감', DELETED: '공고 삭제' };
const companyActions = { HIDDEN: 'SUSPEND', CLOSED: 'FORCE_CLOSE', DELETED: 'DELETE' };

export default function JobsPage() {
  const { admin } = useAdminAuth();
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [selected, setSelected] = useState(null);
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

  const load = useCallback(async () => {
    setError('');
    try {
      const params = { page: String(page), size: '20' };
      if (search) params.keyword = search;
      if (status) params.status = status;
      setResult((await adminApi.jobs(params)).data);
    } catch (cause) { setError(cause.message); }
  }, [page, search, status]);

  useEffect(() => { load(); }, [load]);

  const selectJob = async (id) => {
    setError(''); setReason(''); setNewEndDate(''); setPendingStatus(null);
    setAiPreview(null); setAiResult(null); setAiHistory([]); setAiConfirmed(false); setReviewDraft(null);
    try {
      const job = (await adminApi.job(id)).data;
      if (job.sourcePlatform === 'COMPANY_DIRECT') {
        job.companyJob = (await adminRequest(`/jobs/company-submissions/published/${id}`)).data;
      }
      setSelected(job);
    }
    catch (cause) { setError(cause.message); }
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

  return <section className="users-page">
    <div className="page-heading"><div><p className="eyebrow">JOB POSTS</p><h2>공고 관리</h2>
      <p>공고 숨김·강제 마감·재활성을 관리합니다. 수집원이 공고를 다시 마감으로 판단하면 비활성화될 수 있습니다.</p></div></div>
    <form className="user-filters dashboard-panel" onSubmit={(event) => {
      event.preventDefault(); setPage(0); setSearch(keyword.trim());
    }}>
      <label>공고 또는 기업명<input value={keyword} onChange={(event) => setKeyword(event.target.value)} maxLength={100} /></label>
      <label>관리 상태<select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
        <option value="">전체</option>{Object.entries(statusText).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
      <button className="secondary-button" type="submit">검색</button>
    </form>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="dashboard-panel user-list">
      {!result ? <p role="status">공고를 불러오는 중입니다.</p> : result.content.length === 0
        ? <p>조건에 맞는 공고가 없습니다.</p> : <div className="table-scroll"><table>
          <thead><tr><th>공고</th><th>기업</th><th>관리 상태</th><th>마감일</th><th></th></tr></thead>
          <tbody>{result.content.map((job) => <tr key={job.id}>
            <td>{job.title}</td><td>{job.companyName}</td><td>{statusText[job.moderationStatus]}</td>
            <td>{job.endDate || '—'}</td><td><button type="button" onClick={() => selectJob(job.id)}>상세</button></td>
          </tr>)}</tbody></table></div>}
      {result && <div className="user-pagination"><span>총 {result.totalElements}건 · {page + 1} / {Math.max(1, result.totalPages)} 페이지</span>
        <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>이전</button>
        <button type="button" disabled={page + 1 >= result.totalPages} onClick={() => setPage(page + 1)}>다음</button></div>}
    </div>
    {selected && <DetailModal title="공고 상세" onClose={() => setSelected(null)} closeDisabled={busy}>
      {error && <p className="form-error" role="alert">{error}</p>}
      <dl><div><dt>공고명</dt><dd>{selected.title}</dd></div><div><dt>기업</dt><dd>{selected.companyName}</dd></div>
        <div><dt>관리 상태</dt><dd>{selected.companyJob?.state === 'DELETED' ? '삭제됨' : statusText[selected.moderationStatus]}</dd></div>
        <div><dt>수집 활성</dt><dd>{selected.active ? '활성' : '비활성'}</dd></div>
        <div><dt>마감일</dt><dd>{selected.endDate}</dd></div>
        <div><dt>출처</dt><dd>{selected.sourcePlatform}</dd></div>
        <div><dt>원본</dt><dd><a href={selected.originalUrl} target="_blank" rel="noreferrer">원본 공고 확인</a></dd></div></dl>
      {selected.sourcePlatform === 'COMPANY_DIRECT' && <p>기업 직접 등록 공고입니다. 이 화면에서 처리한 내용은 기업 공고 검수의 처리 이력에도 기록됩니다.</p>}
      {selected.companyJob?.state === 'DELETED' && <p>삭제 처리된 공고입니다. 공고와 감사 기록은 보존되며 수정·재게시할 수 없습니다.</p>}
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
