import { useCallback, useEffect, useRef, useState } from 'react';
import { adminRequest } from '../api/adminApi';
import { useAdminAuth } from '../auth/AdminAuthContext';
import DetailModal from '../components/DetailModal';

const labels = { IN_REVIEW: '검수 대기', CHANGES_REQUESTED: '수정 요청', PUBLISHED: '승인 완료', DRAFT: '임시저장', CLOSED: '마감', SUSPENDED: '숨김', DELETED: '삭제됨' };
const actions = { CREATED: '임시저장', EDITED: '수정', SUBMITTED: '검수 요청', APPROVE: '검수 승인', REQUEST_CHANGES: '수정 요청', SUSPEND: '숨김', FORCE_CLOSE: '강제마감', DELETE: '삭제', CLOSED: '기업 마감', EXPIRED: '기간 만료' };
const tabs = { QUEUE: '공고 목록', ALL: '전체 이력', APPROVE: '승인 내역', DELETE: '삭제 내역', SUSPEND: '숨김 내역', FORCE_CLOSE: '강제마감 내역' };
const actorLabel = (h) => `${{ ADMIN: '관리자', MEMBER: '기업회원', SYSTEM: '시스템' }[h.actorType] || h.actorType}${h.actorId == null ? '' : ` #${h.actorId}`}`;
export default function CompanyJobReviewsPage() {
  const { admin } = useAdminAuth();
  const [state, setState] = useState('IN_REVIEW');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [detail, setDetail] = useState(null);
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [tab, setTab] = useState('QUEUE');
  const [jobId, setJobId] = useState(null);
  const [pendingAction, setPendingAction] = useState(null);
  const requestSequence = useRef(0);
  const load = useCallback(async () => {
    const sequence = ++requestSequence.current;
    setResult(null); setError('');
    const history = tab !== 'QUEUE';
    const params = { page, ...(history ? { ...(tab !== 'ALL' ? { action: tab } : {}), ...(jobId ? { jobId } : {}) } : (state ? { state } : {})) };
    try {
      const response = await adminRequest(`/jobs/company-submissions${history ? '/history' : ''}?${new URLSearchParams(params)}`);
      if (sequence === requestSequence.current) setResult(response.data);
    } catch (e) { if (sequence === requestSequence.current) setError(e.message); }
  }, [page, state, tab, jobId]);
  useEffect(() => { load(); }, [load]);
  const open = async (id) => {
    setError(''); setReason(''); setPendingAction(null);
    try { setDetail((await adminRequest(`/jobs/company-submissions/${id}`)).data); }
    catch (e) { setError(e.message); }
  };
  const review = async (action) => {
    if (busy) return;
    if (!reason.trim()) { setError('처리 사유를 입력해주세요.'); return; }
    setBusy(true); setError('');
    try {
      await adminRequest(`/jobs/company-submissions/${detail.job.id}/review`, { method: 'POST', body: JSON.stringify({ expectedVersion: detail.job.version, action, reason }) });
      setDetail(null); await load();
    } catch (e) { setError(e.status === 409 ? '공고 상태가 변경되었습니다. 닫은 뒤 상세를 다시 열어주세요.' : e.status === 400 ? '필수 항목과 마감일, 지원 URL을 확인해주세요.' : e.message); }
    finally { setBusy(false); }
  };
  const pages = result?.page?.totalPages ?? result?.totalPages ?? 1;
  return <section className="users-page"><div className="page-heading"><div><p className="eyebrow">COMPANY JOB REVIEW</p><h2>기업 공고 검수</h2><p>직접 등록 공고의 검수와 승인·삭제·숨김·강제마감 이력을 관리합니다.</p></div></div>
    <nav className="user-filters dashboard-panel" aria-label="기업 공고 목록 및 이력">{Object.entries(tabs).map(([key, label]) => <button key={key} className={tab === key ? 'primary-button' : 'secondary-button'} aria-pressed={tab === key} onClick={() => { setTab(key); setPage(0); setJobId(null); }}>{label}</button>)}</nav>
    <div className="user-filters dashboard-panel">{tab === 'QUEUE' ? <label>검수 상태<select value={state} onChange={(e) => { setState(e.target.value); setPage(0); }}><option value="">전체</option>{Object.entries(labels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label> : <p>{tabs[tab]}{jobId ? ` · 공고 #${jobId}` : ''} · 최근 처리 순 · 시각은 서버 기록 기준</p>}<button onClick={load}>새로고침</button></div>
    {error && <p role="alert" className="form-error">{error}</p>}
    <div className="dashboard-panel user-list">{!result ? !error && <p role="status">불러오는 중…</p> : !result.content.length ? <p>{tab === 'QUEUE' ? '조건에 맞는 공고가 없습니다.' : '조건에 맞는 처리 내역이 없습니다.'}</p> : tab === 'QUEUE' ? <div className="table-scroll"><table><thead><tr><th>공고</th><th>기업</th><th>상태</th><th>마감일</th><th>검수</th></tr></thead><tbody>{result.content.map((job) => <tr key={job.id}><td>{job.content.title || '제목 없음'}</td><td>{job.companyName}</td><td>{labels[job.state]}</td><td>{job.content.endDate}</td><td><button onClick={() => open(job.id)}>상세 검수</button></td></tr>)}</tbody></table></div> : <div className="table-scroll"><table><thead><tr><th>처리 시각</th><th>공고 / 기업 (현재 명칭)</th><th>처리</th><th>담당자</th><th>사유</th><th>상세</th></tr></thead><tbody>{result.content.map((h) => <tr key={h.id}><td>{h.createdAt?.replace('T', ' ')}</td><td>#{h.companyJobId} {h.title || '제목 없음'}<br />{h.companyName}</td><td>{actions[h.action] || h.action}</td><td>{actorLabel(h)}</td><td style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', maxWidth: 320 }}>{h.reason || '—'}</td><td><button onClick={() => open(h.companyJobId)}>공고 상세</button></td></tr>)}</tbody></table></div>}
      <div className="user-pagination"><button disabled={!result || page === 0} onClick={() => setPage(page - 1)}>이전</button><span>총 {result?.page?.totalElements ?? result?.totalElements ?? 0}건 · {page + 1} / {Math.max(1, pages)}</span><button disabled={!result || page + 1 >= pages} onClick={() => setPage(page + 1)}>다음</button></div></div>
    {detail && <DetailModal title="기업 공고 검수" onClose={() => setDetail(null)} closeDisabled={busy}>
      {error && <p role="alert" className="form-error">{error}</p>}
      <h3>{detail.job.content.title}</h3><p>{detail.job.companyName} · {labels[detail.job.state]}</p>
      <dl>{[['jobCategory', '직무'], ['experience', '경력'], ['location', '근무지'], ['hireType', '고용형태'], ['techStacks', '기술스택'], ['endDate', '마감일'], ['applyUrl', '지원 URL']].map(([key, name]) => <div key={key}><dt>{name}</dt><dd style={{ overflowWrap: 'anywhere' }}>{detail.job.content[key] || '미입력'}</dd></div>)}</dl>
      <div style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{detail.job.content.description}</div>
      <h3>기존 수집 공고 중복 후보</h3><p>동일 기업명·지원 URL 기준 후보입니다. 직무를 대조한 뒤 판단해주세요.</p>
      {detail.duplicates.length ? <ul>{detail.duplicates.map((d) => <li key={d.id}>#{d.id} {d.title} — {d.companyName}</li>)}</ul> : <p>중복 후보가 없습니다.</p>}
      {detail.job.state === 'DELETED' && <p>삭제 처리된 공고입니다. 공개 노출은 중단되었으며 공고와 처리 기록은 보존됩니다.</p>}
      {admin.role !== 'REVIEWER' && detail.job.state !== 'DELETED' && <div className="moderation-form"><label htmlFor="company-job-review-reason">처리 사유<textarea id="company-job-review-reason" disabled={busy} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} /></label><div className="job-actions">
        {detail.job.state === 'IN_REVIEW' && <><button disabled={busy} onClick={() => review('APPROVE')}>승인 및 게시</button><button disabled={busy} onClick={() => review('REQUEST_CHANGES')}>수정 요청</button></>}
        {detail.job.state !== 'SUSPENDED' && <button disabled={busy} onClick={() => setPendingAction('SUSPEND')}>공고 숨김</button>}
        {detail.job.state !== 'CLOSED' && <button disabled={busy} onClick={() => setPendingAction('FORCE_CLOSE')}>강제마감</button>}
        <button disabled={busy} onClick={() => setPendingAction('DELETE')}>공고 삭제</button></div>
        {pendingAction && <div role="group" aria-label="공고 처리 확인"><p>이 공고를 {actions[pendingAction]} 처리합니다. 공개 노출은 중단되고 처리 기록은 보존됩니다.{pendingAction === 'DELETE' ? ' 삭제 후에는 수정·재게시할 수 없습니다.' : ''}</p><button disabled={busy} onClick={() => review(pendingAction)}>처리 확정</button><button disabled={busy} onClick={() => setPendingAction(null)}>취소</button></div>}
      </div>}
      <h3>처리 이력 (최근 50건)</h3><ul>{detail.history.map((h) => <li key={h.id}>{h.createdAt} · {actorLabel(h)} · {actions[h.action] || h.action}{h.reason ? ` — ${h.reason}` : ''}</li>)}</ul>
      <button disabled={busy} onClick={() => { setJobId(detail.job.id); setTab('ALL'); setPage(0); setDetail(null); }}>이 공고 전체 이력</button>
    </DetailModal>}
  </section>;
}
