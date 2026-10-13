import { useCallback, useEffect, useState } from 'react';
import { adminRequest } from '../api/adminApi';
import { useAdminAuth } from '../auth/AdminAuthContext';
import DetailModal from '../components/DetailModal';

const labels = { IN_REVIEW: '검수 대기', CHANGES_REQUESTED: '수정 요청', PUBLISHED: '승인 완료', DRAFT: '임시저장', CLOSED: '마감', SUSPENDED: '게시 중단' };
export default function CompanyJobReviewsPage() {
  const { admin } = useAdminAuth();
  const [state, setState] = useState('IN_REVIEW');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [detail, setDetail] = useState(null);
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const load = useCallback(async () => {
    try { setResult((await adminRequest(`/jobs/company-submissions?${new URLSearchParams({ page, ...(state ? { state } : {}) })}`)).data); }
    catch (e) { setError(e.message); }
  }, [page, state]);
  useEffect(() => { load(); }, [load]);
  const open = async (id) => {
    setError(''); setReason('');
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
  return <section className="users-page"><div className="page-heading"><div><p className="eyebrow">COMPANY JOB REVIEW</p><h2>기업 공고 검수</h2><p>직접 등록 공고와 수정본을 확인합니다. 승인 시 공개 검색에 반영됩니다.</p></div></div>
    <div className="user-filters dashboard-panel"><label>검수 상태<select value={state} onChange={(e) => { setState(e.target.value); setPage(0); }}><option value="">전체</option>{Object.entries(labels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label><button onClick={load}>새로고침</button></div>
    {error && <p role="alert" className="form-error">{error}</p>}
    <div className="dashboard-panel user-list">{!result ? <p role="status">불러오는 중…</p> : !result.content.length ? <p>검수할 공고가 없습니다.</p> : <div className="table-scroll"><table><thead><tr><th>공고</th><th>기업</th><th>상태</th><th>마감일</th><th>검수</th></tr></thead><tbody>{result.content.map((job) => <tr key={job.id}><td>{job.content.title || '제목 없음'}</td><td>{job.companyName}</td><td>{labels[job.state]}</td><td>{job.content.endDate}</td><td><button onClick={() => open(job.id)}>상세 검수</button></td></tr>)}</tbody></table></div>}
      <div className="user-pagination"><button disabled={page === 0} onClick={() => setPage(page - 1)}>이전</button><span>{page + 1} / {Math.max(1, pages)}</span><button disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>다음</button></div></div>
    {detail && <DetailModal title="기업 공고 검수" onClose={() => setDetail(null)} closeDisabled={busy}>
      {error && <p role="alert" className="form-error">{error}</p>}
      <h3>{detail.job.content.title}</h3><p>{detail.job.companyName} · {labels[detail.job.state]}</p>
      <dl>{[['jobCategory', '직무'], ['experience', '경력'], ['location', '근무지'], ['hireType', '고용형태'], ['techStacks', '기술스택'], ['endDate', '마감일'], ['applyUrl', '지원 URL']].map(([key, name]) => <div key={key}><dt>{name}</dt><dd style={{ overflowWrap: 'anywhere' }}>{detail.job.content[key] || '미입력'}</dd></div>)}</dl>
      <div style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{detail.job.content.description}</div>
      <h3>기존 수집 공고 중복 후보</h3><p>동일 기업명·지원 URL 기준 후보입니다. 직무를 대조한 뒤 판단해주세요.</p>
      {detail.duplicates.length ? <ul>{detail.duplicates.map((d) => <li key={d.id}>#{d.id} {d.title} — {d.companyName}</li>)}</ul> : <p>중복 후보가 없습니다.</p>}
      {admin.role !== 'REVIEWER' && detail.job.state !== 'SUSPENDED' && <div className="moderation-form"><label htmlFor="company-job-review-reason">처리 사유<textarea id="company-job-review-reason" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} /></label><div className="job-actions">
        {detail.job.state === 'IN_REVIEW' && <><button disabled={busy} onClick={() => review('APPROVE')}>승인 및 게시</button><button disabled={busy} onClick={() => review('REQUEST_CHANGES')}>수정 요청</button></>}
        <button disabled={busy} onClick={() => review('SUSPEND')}>게시 중단</button></div></div>}
      <h3>처리 이력</h3><ul>{detail.history.map((h) => <li key={h.id}>{h.createdAt} · {h.actorType} · {h.action}{h.reason ? ` — ${h.reason}` : ''}</li>)}</ul>
    </DetailModal>}
  </section>;
}
