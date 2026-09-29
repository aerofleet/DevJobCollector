import { useCallback, useEffect, useState } from 'react';
import { adminApi } from '../api/adminApi';
import { useAdminAuth } from '../auth/AdminAuthContext';
import DetailModal from '../components/DetailModal';

const roleText = { SUPER_ADMIN: '최고 관리자', ADMIN: '운영 관리자', REVIEWER: '심사 담당' };
const statusText = { ACTIVE: '활성', LOCKED: '로그인 잠금', DISABLED: '비활성' };
const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
const passwordAlphabet = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_';

const generateSecret = () => {
  const bytes = crypto.getRandomValues(new Uint8Array(20));
  let bits = 0;
  let buffer = 0;
  let result = '';
  for (const byte of bytes) {
    buffer = (buffer << 8) | byte;
    bits += 8;
    while (bits >= 5) {
      result += alphabet[(buffer >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  return result;
};

const generatePassword = () => Array.from(crypto.getRandomValues(new Uint8Array(32)),
  (value) => passwordAlphabet[value & 63]).join('');

const emptyForm = { email: '', name: '', role: 'ADMIN', password: '', mfaSecret: '' };

export default function AdminsPage() {
  const { admin } = useAdminAuth();
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [role, setRole] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [selected, setSelected] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [showCreate, setShowCreate] = useState(false);
  const [created, setCreated] = useState(false);
  const [reason, setReason] = useState('');
  const [newRole, setNewRole] = useState('');
  const [pending, setPending] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (admin.role !== 'SUPER_ADMIN') return;
    setError('');
    try {
      const params = { page: String(page), size: '20' };
      if (search) params.keyword = search;
      if (role) params.role = role;
      if (status) params.status = status;
      setResult((await adminApi.admins(params)).data);
    } catch (cause) { setError(cause.message); }
  }, [admin.role, page, search, role, status]);

  useEffect(() => { load(); }, [load]);

  const selectAdmin = async (id) => {
    setError(''); setReason(''); setPending(null);
    try { setSelected((await adminApi.admin(id)).data); }
    catch (cause) { setError(cause.message); }
  };

  const create = async (event) => {
    event.preventDefault();
    if (busy || created) return;
    setBusy(true); setError('');
    try {
      await adminApi.createAdmin(form);
      setCreated(true);
      setSelected(null);
      await load();
    } catch (cause) { setError(cause.message); }
    finally { setBusy(false); }
  };

  const closeCreate = () => {
    setForm(emptyForm);
    setCreated(false);
    setShowCreate(false);
  };

  const update = async () => {
    if (!selected || !pending || !reason.trim() || busy) return;
    setBusy(true); setError('');
    try {
      const body = { expectedVersion: selected.version, reason: reason.trim() };
      const response = pending === 'role'
        ? await adminApi.changeAdminRole(selected.id, { ...body, role: newRole })
        : await adminApi.changeAdminStatus(selected.id, { ...body, status: pending });
      setSelected(response.data);
      setReason(''); setPending(null);
      await load();
    } catch (cause) {
      setError(cause.status === 409 ? '계정이 변경되었습니다. 상세를 다시 열어 확인하세요.' : cause.message);
    } finally { setBusy(false); }
  };

  if (admin.role !== 'SUPER_ADMIN') return <p role="alert">관리자 계정은 최고 관리자만 관리할 수 있습니다.</p>;
  const editable = selected && selected.id !== admin.id && selected.role !== 'SUPER_ADMIN';

  return <section className="users-page">
    <div className="page-heading"><div><p className="eyebrow">ADMIN ACCOUNTS</p><h2>관리자 계정</h2>
      <p>계정 권한과 상태를 관리하고 모든 변경에 사유를 기록합니다.</p></div>
      <button className="primary-button" type="button" onClick={() => setShowCreate(true)}>관리자 추가</button></div>
    {showCreate && <section className="dashboard-panel user-detail" aria-label="관리자 추가">
      <div className="panel-header"><h3>{created ? '계정 생성 완료' : '관리자 추가'}</h3>
        <button type="button" onClick={closeCreate}>닫기</button></div>
      {created ? <p>비밀번호와 MFA 비밀키를 안전한 채널로 전달한 뒤 닫기를 눌러 화면에서 지우세요. 이 값은 다시 표시되지 않습니다.</p>
        : <form className="moderation-form" onSubmit={create}>
          <label>이메일<input type="email" required maxLength={255} value={form.email}
            onChange={(event) => setForm({ ...form, email: event.target.value })} /></label>
          <label>이름<input required maxLength={100} value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>
          <label>역할<select value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value })}>
            <option value="ADMIN">운영 관리자</option><option value="REVIEWER">심사 담당</option>
          </select></label>
          <button className="secondary-button" type="button" onClick={() => setForm({
            ...form, password: generatePassword(), mfaSecret: generateSecret(),
          })}>보안값 생성</button>
          <label>초기 비밀번호<input required minLength={16} maxLength={128} autoComplete="new-password"
            value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} /></label>
          <label>MFA 비밀키 (32자리 Base32)<input required minLength={32} maxLength={32}
            value={form.mfaSecret} onChange={(event) => setForm({ ...form, mfaSecret: event.target.value })} /></label>
          <p>생성 전 두 값을 보관하세요. 계정 생성 후 비밀키는 서버에서 다시 조회할 수 없습니다.</p>
          <button className="primary-button" type="submit" disabled={busy}>{busy ? '생성 중…' : '계정 생성'}</button>
        </form>}
      {created && <dl><div><dt>초기 비밀번호</dt><dd>{form.password}</dd></div>
        <div><dt>MFA 비밀키</dt><dd>{form.mfaSecret}</dd></div></dl>}
    </section>}
    <form className="user-filters dashboard-panel" onSubmit={(event) => {
      event.preventDefault(); setPage(0); setSearch(keyword.trim());
    }}>
      <label>이름 또는 이메일<input value={keyword} onChange={(event) => setKeyword(event.target.value)} maxLength={100} /></label>
      <label>역할<select value={role} onChange={(event) => { setRole(event.target.value); setPage(0); }}>
        <option value="">전체</option>{Object.entries(roleText).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
      <label>상태<select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
        <option value="">전체</option>{Object.entries(statusText).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
      <button className="secondary-button" type="submit">검색</button>
    </form>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="dashboard-panel user-list">
      {!result ? <p role="status">관리자를 불러오는 중입니다.</p> : result.content.length === 0
        ? <p>조건에 맞는 관리자가 없습니다.</p> : <div className="table-scroll"><table>
          <thead><tr><th>이름</th><th>이메일</th><th>역할</th><th>상태</th><th>MFA</th><th></th></tr></thead>
          <tbody>{result.content.map((account) => <tr key={account.id}>
            <td>{account.name}</td><td>{account.email}</td><td>{roleText[account.role]}</td>
            <td>{statusText[account.status]}</td><td>{account.mfaConfigured ? '설정' : '미설정'}</td>
            <td><button type="button" onClick={() => selectAdmin(account.id)}>상세</button></td>
          </tr>)}</tbody></table></div>}
      {result && <div className="user-pagination"><span>총 {result.totalElements}명 · {page + 1} / {Math.max(1, result.totalPages)} 페이지</span>
        <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>이전</button>
        <button type="button" disabled={page + 1 >= result.totalPages} onClick={() => setPage(page + 1)}>다음</button></div>}
    </div>
    {selected && <DetailModal title="관리자 상세" onClose={() => setSelected(null)} closeDisabled={busy}>
      {error && <p className="form-error" role="alert">{error}</p>}
      <dl><div><dt>이름</dt><dd>{selected.name}</dd></div><div><dt>이메일</dt><dd>{selected.email}</dd></div>
        <div><dt>역할</dt><dd>{roleText[selected.role]}</dd></div><div><dt>상태</dt><dd>{statusText[selected.status]}</dd></div>
        <div><dt>MFA</dt><dd>{selected.mfaConfigured ? '설정' : '미설정'}</dd></div>
        <div><dt>마지막 로그인</dt><dd>{selected.lastLoginAt || '—'}</dd></div></dl>
      {editable && <div className="moderation-form">
        <label htmlFor="admin-change-reason">변경 사유</label>
        <textarea id="admin-change-reason" value={reason} maxLength={500} rows={3}
          onChange={(event) => { setReason(event.target.value); setPending(null); }} />
        <label>새 역할<select value={newRole} onChange={(event) => { setNewRole(event.target.value); setPending(null); }}>
          <option value="">선택</option><option value="ADMIN">운영 관리자</option><option value="REVIEWER">심사 담당</option>
        </select></label>
        {!pending ? <div className="job-actions">
          {['ACTIVE', 'LOCKED'].includes(selected.status) && <button className="secondary-button" type="button" disabled={!reason.trim()}
            onClick={() => setPending('DISABLED')}>비활성화</button>}
          {selected.status === 'DISABLED' && <button className="secondary-button" type="button" disabled={!reason.trim()}
            onClick={() => setPending('ACTIVE')}>재활성화</button>}
          <button className="secondary-button" type="button"
            disabled={!reason.trim() || !newRole || newRole === selected.role}
            onClick={() => setPending('role')}>역할 변경</button>
        </div> : <div className="moderation-confirm" role="group" aria-label="관리자 계정 변경 확인">
          <p>{selected.name} 계정을 {pending === 'role' ? `${roleText[newRole]} 역할로 변경` :
            pending === 'ACTIVE' ? '재활성화' : '비활성화'}합니다. 활성 세션이 폐기되고 사유가 감사 기록에 남습니다.</p>
          <button className="primary-button" type="button" disabled={busy} onClick={update}>
            {busy ? '처리 중…' : '변경 확정'}</button>
          <button className="secondary-button" type="button" disabled={busy} onClick={() => setPending(null)}>취소</button>
        </div>}
      </div>}
    </DetailModal>}
  </section>;
}
