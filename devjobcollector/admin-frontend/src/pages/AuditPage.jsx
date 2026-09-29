import { useCallback, useEffect, useState } from 'react';
import { adminApi } from '../api/adminApi';
import { useAdminAuth } from '../auth/AdminAuthContext';

export default function AuditPage() {
  const { admin } = useAdminAuth();
  const [actorId, setActorId] = useState('');
  const [action, setAction] = useState('');
  const [targetType, setTargetType] = useState('');
  const [targetId, setTargetId] = useState('');
  const [resultFilter, setResultFilter] = useState('');
  const [filters, setFilters] = useState({});
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (admin.role !== 'SUPER_ADMIN') return;
    setError('');
    try { setResult((await adminApi.audit({ page: String(page), size: '20', ...filters })).data); }
    catch (cause) { setError(cause.message); }
  }, [admin.role, page, filters]);

  useEffect(() => { load(); }, [load]);

  if (admin.role !== 'SUPER_ADMIN') return <p role="alert">감사 기록은 최고 관리자만 볼 수 있습니다.</p>;

  return <section className="users-page">
    <div className="page-heading"><div><p className="eyebrow">AUDIT TRAIL</p><h2>감사 기록</h2>
      <p>관리자 업무 변경과 인증 이력을 검색합니다.</p></div></div>
    <form className="user-filters dashboard-panel" onSubmit={(event) => {
      event.preventDefault(); setPage(0);
      setFilters(Object.fromEntries(Object.entries({ actorId, action, targetType, targetId, result: resultFilter })
        .filter(([, value]) => value.trim())));
    }}>
      <label>관리자 ID<input type="number" min="1" value={actorId} onChange={(event) => setActorId(event.target.value)} /></label>
      <label>작업<input value={action} maxLength={100} onChange={(event) => setAction(event.target.value)} /></label>
      <label>대상 종류<input value={targetType} maxLength={50} onChange={(event) => setTargetType(event.target.value)} /></label>
      <label>대상 ID<input value={targetId} maxLength={100} onChange={(event) => setTargetId(event.target.value)} /></label>
      <label>결과<select value={resultFilter} onChange={(event) => setResultFilter(event.target.value)}>
        <option value="">전체</option><option value="SUCCESS">성공</option><option value="FAILURE">실패</option><option value="DENIED">거부</option>
      </select></label>
      <button className="secondary-button" type="submit">검색</button>
    </form>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="dashboard-panel user-list">
      {!result ? <p role="status">기록을 불러오는 중입니다.</p> : result.content.length === 0
        ? <p>조건에 맞는 기록이 없습니다.</p> : <div className="table-scroll"><table>
          <thead><tr><th>시각</th><th>관리자 ID</th><th>작업</th><th>대상</th><th>결과</th><th>사유</th><th>요청 ID</th></tr></thead>
          <tbody>{result.content.map((log) => <tr key={log.id}>
            <td>{log.occurredAt}</td><td>{log.actorAdminId || '—'}</td><td>{log.action}</td>
            <td>{log.targetType} {log.targetId || ''}</td><td>{log.result}</td>
            <td>{log.reason || '—'}</td><td>{log.requestId}</td>
          </tr>)}</tbody></table></div>}
      {result && <div className="user-pagination"><span>총 {result.totalElements}건 · {page + 1} / {Math.max(1, result.totalPages)} 페이지</span>
        <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>이전</button>
        <button type="button" disabled={page + 1 >= result.totalPages} onClick={() => setPage(page + 1)}>다음</button></div>}
    </div>
  </section>;
}
