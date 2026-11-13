import { useEffect, useState } from 'react';
import { adminApi } from '../api/adminApi';
import { useAdminAuth } from '../auth/AdminAuthContext';
import PublicCompanySeeds from '../components/PublicCompanySeeds';

const statuses = {
  DISCOVERED: '발견', VERIFYING: '확인 중', VERIFIED: '확인 완료', APPROVED: '승인',
  TARGET_CREATED: '수집 대상 생성', REJECTED: '거절', DEAD: '접속 불가',
  DUPLICATE: '중복', BLOCKED: '차단',
};
const PAGE_SIZE = 20;

export default function DiscoveryCandidatesPage() {
  const { admin } = useAdminAuth();
  const [status, setStatus] = useState('');
  const [cursors, setCursors] = useState([0]);
  const [rows, setRows] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState(null);
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState('');
  const [seedFile, setSeedFile] = useState(null);
  const [seedEncoding, setSeedEncoding] = useState('CP949');
  const [importing, setImporting] = useState(false);
  const [seedRefresh, setSeedRefresh] = useState(0);
  const cursor = cursors[cursors.length - 1];

  useEffect(() => {
    let active = true;
    const params = { afterId: String(cursor), size: String(PAGE_SIZE + 1) };
    if (status) params.status = status;
    adminApi.discoveryCandidates(params).then((response) => {
      if (active) { setRows(response.data); setError(''); setLoading(false); }
    }).catch((cause) => {
      if (active) { setRows(null); setError(cause.message); setLoading(false); }
    });
    return () => { active = false; };
  }, [status, cursor]);

  const changePage = (next) => { setLoading(true); setRows(null); setCursors(next); };
  const visible = rows?.slice(0, PAGE_SIZE) || [];
  const reject = async () => {
    if (saving || !reason.trim()) { setError('거절 사유를 입력해주세요.'); return; }
    setSaving(true); setError('');
    try {
      await adminApi.rejectDiscoveryCandidate(selectedId, reason.trim());
      const params = { afterId: String(cursor), size: String(PAGE_SIZE + 1) };
      if (status) params.status = status;
      setSelectedId(null); setReason(''); setNotice('후보를 거절했습니다.');
      setRows((await adminApi.discoveryCandidates(params)).data);
    } catch (cause) {
      setError(cause.status === 409 ? '이미 처리된 후보입니다. 목록을 다시 확인해주세요.' : cause.message);
    } finally { setSaving(false); }
  };
  const importSeeds = async (event) => {
    event.preventDefault();
    if (!seedFile || importing) return;
    if (seedFile.size > 10_000_000) { setError('CSV 파일은 10MB 이하여야 합니다.'); return; }
    setImporting(true); setError(''); setNotice('');
    try {
      const result = await adminApi.importPublicSeeds(seedFile, seedEncoding);
      setNotice(`CSV 처리 완료: 유효 행 ${result.data.accepted}건, 제외 행 ${result.data.skipped}건. 중복 기업 행이 포함될 수 있습니다.`);
      setSeedRefresh((value) => value + 1);
    } catch (cause) { setError(cause.message); }
    finally { setImporting(false); }
  };

  return <section className="users-page">
    <div className="page-heading"><div><p className="eyebrow">DISCOVERY</p><h2>채용 소스 후보</h2>
      <p>수집 후보로 등록된 정보입니다. 기업과 채용 페이지의 관계 및 현재 채용 여부를 확인해야 합니다.</p>
    </div></div>
    {admin?.role !== 'REVIEWER' && <form className="dashboard-panel user-filters" onSubmit={importSeeds}>
      <label>중진공 구인기업 CSV (10MB 이하)
        <input type="file" accept=".csv,text/csv" onChange={(event) => setSeedFile(event.target.files[0] || null)} />
      </label>
      <label>파일 인코딩<select value={seedEncoding} onChange={(event) => setSeedEncoding(event.target.value)}>
        <option value="CP949">CP949 (공공 원본)</option><option value="UTF-8">UTF-8</option>
      </select></label>
      <button type="submit" className="secondary-button" disabled={!seedFile || importing}>
        {importing ? '처리 중...' : '기업 Seed 수입'}
      </button>
      <p>공공데이터 원본 열 형식이 필요합니다. 수입한 기업은 채용 소스 승인을 거치지 않은 별도 후보입니다.</p>
    </form>}
    <PublicCompanySeeds refresh={seedRefresh} />
    <div className="user-filters dashboard-panel">
      <label>후보 상태<select value={status} onChange={(event) => {
        setLoading(true); setRows(null); setStatus(event.target.value); setCursors([0]); setSelectedId(null);
      }}>
        <option value="">전체</option>
        {Object.entries(statuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
    </div>
    {error && <p className="form-error" role="alert">{error}</p>}
    {notice && <p role="status">{notice}</p>}
    <div className="dashboard-panel user-list">
      {loading ? <p role="status">후보를 불러오는 중입니다.</p> : !rows ? null : visible.length === 0
        ? <p>조건에 맞는 후보가 없습니다.</p> : <div className="table-scroll"><table>
          <thead><tr><th>기업</th><th>홈페이지</th><th>ATS</th><th>발견된 주소</th><th>발견 방식</th><th>상태</th><th>마지막 발견 (UTC)</th><th>검수</th></tr></thead>
          <tbody>{visible.map((candidate) => <tr key={candidate.id}>
            <td>{candidate.companyName}</td><td>{candidate.homepageUrl || '—'}</td>
            <td>{candidate.provider} · {candidate.sourceIdentifier}</td><td>{candidate.sourceUrl}</td>
            <td>{candidate.discoverySource === 'AUTO_HOMEPAGE' ? '홈페이지 자동 탐색' : '관리자 제공 HTML'}</td>
            <td>{statuses[candidate.status] || candidate.status}</td><td>{candidate.lastSeenAt || '—'}</td>
            <td>{candidate.status === 'DISCOVERED' && <button type="button" onClick={() => {
              setSelectedId(candidate.id); setReason(''); setError(''); setNotice('');
            }}>거절</button>}</td>
          </tr>)}</tbody></table></div>}
      {rows && <div className="user-pagination"><span>{cursors.length} 페이지</span>
        <button type="button" disabled={cursors.length === 1}
          onClick={() => changePage(cursors.slice(0, -1))}>이전</button>
        <button type="button" disabled={rows.length <= PAGE_SIZE}
          onClick={() => changePage([...cursors, visible[visible.length - 1].id])}>다음</button>
      </div>}
    </div>
    {selectedId !== null && <div className="dashboard-panel company-review-confirm">
      <p>후보 #{selectedId}의 거절 사유를 확인한 뒤 처리합니다.</p>
      <label>거절 사유<textarea value={reason} onChange={(event) => setReason(event.target.value)}
        maxLength={500} rows={3} disabled={saving} /></label>
      <div><button type="button" className="primary-button" disabled={saving} onClick={reject}>거절 확정</button>
        <button type="button" disabled={saving} onClick={() => setSelectedId(null)}>취소</button></div>
    </div>}
  </section>;
}
