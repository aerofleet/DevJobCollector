import { useCallback, useEffect, useState } from 'react';
import { adminApi } from '../api/adminApi';
import { useAdminAuth } from '../auth/AdminAuthContext';
import DetailModal from '../components/DetailModal';

const statusText = {
  ACTIVE: '활성', SUSPENDED: '정지', PENDING_EMAIL: '이메일 대기', WITHDRAWN: '탈퇴',
};

export default function UsersPage() {
  const { admin } = useAdminAuth();
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [selected, setSelected] = useState(null);
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);

  const load = useCallback(async () => {
    setError('');
    try {
      const params = { page: String(page), size: '20' };
      if (search) params.keyword = search;
      if (status) params.status = status;
      setResult((await adminApi.users(params)).data);
    } catch (cause) {
      setError(cause.message);
    }
  }, [page, search, status]);

  useEffect(() => { load(); }, [load]);

  const selectUser = async (id) => {
    setError('');
    setReason('');
    setConfirming(false);
    try { setSelected((await adminApi.user(id)).data); }
    catch (cause) { setError(cause.message); }
  };

  const changeStatus = async () => {
    if (!selected || !reason.trim() || busy) return;
    setBusy(true);
    setError('');
    try {
      const target = selected.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
      const updated = (await adminApi.moderateUser(selected.id, {
        status: target, expectedVersion: selected.version, reason: reason.trim(),
      })).data;
      setSelected(updated);
      setReason('');
      setConfirming(false);
      await load();
    } catch (cause) {
      setError(cause.status === 409 ? '회원 상태가 변경되었습니다. 상세를 다시 열어 확인하세요.' : cause.message);
    } finally { setBusy(false); }
  };

  return (
    <section className="users-page">
      <div className="page-heading"><div><p className="eyebrow">MEMBERS</p><h2>회원 관리</h2>
        <p>회원 상태를 확인하고 변경 사유를 남깁니다.</p></div></div>
      <form className="user-filters dashboard-panel" onSubmit={(event) => {
        event.preventDefault(); setPage(0); setSearch(keyword.trim());
      }}>
        <label>이름 또는 이메일<input value={keyword} onChange={(event) => setKeyword(event.target.value)} maxLength={100} /></label>
        <label>상태<select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
          <option value="">전체</option>{Object.entries(statusText).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select></label>
        <button className="secondary-button" type="submit">검색</button>
      </form>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="dashboard-panel user-list">
        {!result ? <p role="status">회원을 불러오는 중입니다.</p> : result.content.length === 0
          ? <p>조건에 맞는 회원이 없습니다.</p> : <div className="table-scroll"><table>
            <thead><tr><th>회원</th><th>이메일</th><th>상태</th><th>가입일</th><th></th></tr></thead>
            <tbody>{result.content.map((user) => <tr key={user.id}>
              <td>{user.name}</td><td>{user.email}</td><td>{statusText[user.status] || user.status}</td>
              <td>{user.createdAt?.slice(0, 10) || '—'}</td>
              <td><button type="button" onClick={() => selectUser(user.id)}>상세</button></td>
            </tr>)}</tbody></table></div>}
        {result && <div className="user-pagination"><span>총 {result.totalElements}명 · {page + 1} / {Math.max(1, result.totalPages)} 페이지</span>
          <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>이전</button>
          <button type="button" disabled={page + 1 >= result.totalPages} onClick={() => setPage(page + 1)}>다음</button></div>}
      </div>
      {selected && <DetailModal title="회원 상세" onClose={() => setSelected(null)} closeDisabled={busy}>
        {error && <p className="form-error" role="alert">{error}</p>}
        <dl><div><dt>이름</dt><dd>{selected.name}</dd></div><div><dt>이메일</dt><dd>{selected.email}</dd></div>
          <div><dt>상태</dt><dd>{statusText[selected.status]}</dd></div><div><dt>가입 경로</dt><dd>{selected.provider}</dd></div></dl>
        {admin.role !== 'REVIEWER' && ['ACTIVE', 'SUSPENDED'].includes(selected.status) && <div className="moderation-form">
          <label htmlFor="moderation-reason">변경 사유</label>
          <textarea id="moderation-reason" value={reason} onChange={(event) => {
            setReason(event.target.value); setConfirming(false);
          }} maxLength={500} rows={3} />
          {!confirming ? <button className="primary-button" type="button" disabled={!reason.trim()}
            onClick={() => setConfirming(true)}>
            {selected.status === 'ACTIVE' ? '회원 정지' : '정지 해제'}
          </button> : <div className="moderation-confirm" role="group" aria-label="상태 변경 확인">
            <p>{selected.name} 회원을 {selected.status === 'ACTIVE' ? '정지' : '정지 해제'}합니다. 입력한 사유가 감사 기록에 남습니다.</p>
            <button className="primary-button" type="button" disabled={busy} onClick={changeStatus}>
              {busy ? '처리 중…' : '변경 확정'}
            </button>
            <button className="secondary-button" type="button" disabled={busy} onClick={() => setConfirming(false)}>취소</button>
          </div>}</div>}
      </DetailModal>}
    </section>
  );
}
