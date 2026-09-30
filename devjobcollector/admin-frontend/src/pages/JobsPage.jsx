import { useCallback, useEffect, useState } from 'react';
import { adminApi } from '../api/adminApi';
import { useAdminAuth } from '../auth/AdminAuthContext';
import DetailModal from '../components/DetailModal';

const statusText = { ACTIVE: '노출', HIDDEN: '숨김', CLOSED: '강제 마감' };
const actionText = { ACTIVE: '재활성', HIDDEN: '공고 숨김', CLOSED: '강제 마감' };

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
    try { setSelected((await adminApi.job(id)).data); }
    catch (cause) { setError(cause.message); }
  };

  const changeStatus = async () => {
    if (!selected || !pendingStatus || !reason.trim() || busy || (needsNewEndDate && !newEndDate)) return;
    setBusy(true); setError('');
    try {
      const updated = (await adminApi.moderateJob(selected.id, {
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

  const actions = selected?.moderationStatus === 'ACTIVE' ? ['HIDDEN', 'CLOSED']
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
        <div><dt>관리 상태</dt><dd>{statusText[selected.moderationStatus]}</dd></div>
        <div><dt>수집 활성</dt><dd>{selected.active ? '활성' : '비활성'}</dd></div>
        <div><dt>마감일</dt><dd>{selected.endDate}</dd></div>
        <div><dt>출처</dt><dd>{selected.sourcePlatform}</dd></div>
        <div><dt>원본</dt><dd><a href={selected.originalUrl} target="_blank" rel="noreferrer">원본 공고 확인</a></dd></div></dl>
      {admin.role !== 'REVIEWER' && actions.length > 0 && <div className="moderation-form">
        <label htmlFor="job-moderation-reason">변경 사유</label>
        <textarea id="job-moderation-reason" value={reason} onChange={(event) => {
          setReason(event.target.value); setPendingStatus(null);
        }} maxLength={500} rows={3} />
        {!pendingStatus ? <div className="job-actions">{actions.map((action) =>
          <button className="secondary-button" key={action} type="button" disabled={!reason.trim()}
            onClick={() => setPendingStatus(action)}>{actionText[action]}</button>)}</div>
          : <div className="moderation-confirm" role="group" aria-label="공고 상태 변경 확인">
            {pendingStatus === 'ACTIVE' && <label htmlFor="job-new-end-date">새 마감일 {needsNewEndDate ? '(필수)' : '(선택)'}
              <input id="job-new-end-date" type="date" value={newEndDate}
                min={new Date().toLocaleDateString('sv-SE')}
                onChange={(event) => setNewEndDate(event.target.value)} required={needsNewEndDate} />
            </label>}
            <p>{selected.title} 공고를 {actionText[pendingStatus]} 처리합니다. 변경 사유가 감사 기록에 남습니다.</p>
            <button className="primary-button" type="button" disabled={busy || (needsNewEndDate && !newEndDate)} onClick={changeStatus}>
              {busy ? '처리 중…' : '변경 확정'}</button>
            <button className="secondary-button" type="button" disabled={busy} onClick={() => setPendingStatus(null)}>취소</button>
          </div>}</div>}
    </DetailModal>}
  </section>;
}
