import { useEffect, useState } from 'react';
import { adminApi } from '../api/adminApi';
import { useAdminAuth } from '../auth/AdminAuthContext';

export default function PublicCompanySeeds({ refresh }) {
  const { admin } = useAdminAuth();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [pageCount, setPageCount] = useState(0);
  const [pageInput, setPageInput] = useState('1');
  const [pageError, setPageError] = useState('');
  const [rows, setRows] = useState(null);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState(null);
  const [url, setUrl] = useState('');
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState('');
  const [revision, setRevision] = useState(0);
  const [analysisSeed, setAnalysisSeed] = useState(null);
  const [analysisError, setAnalysisError] = useState('');
  const [ats, setAts] = useState([]);
  const [rejectId, setRejectId] = useState(null);
  const [rejectReason, setRejectReason] = useState('');
  const [reviewId, setReviewId] = useState(null);
  const [reviewNote, setReviewNote] = useState('');
  const [reference, setReference] = useState('');
  const [approvalId, setApprovalId] = useState(null);
  const [confirmCompany, setConfirmCompany] = useState('');
  const [confirmOwnership, setConfirmOwnership] = useState(false);
  useEffect(() => {
    let active = true;
    adminApi.publicSeedPage({ page: String(page), size: String(size) })
      .then((result) => { if (active) {
        setRows(result.data.items); setTotal(result.data.totalElements); setPageCount(result.data.totalPages);
        setPageInput(String(result.data.page + 1)); setPage(result.data.page); setError('');
      } })
      .catch((cause) => { if (active) { setRows(null); setError(cause.message); } });
    return () => { active = false; };
  }, [page, size, refresh, revision]);

  const goToPage = (next) => {
    if (next < 0 || next >= pageCount || next === page) return;
    setRows(null); setPageError(''); setSelected(null); setAnalysisSeed(null); setPage(next);
  };
  const submitPage = (event) => {
    event.preventDefault();
    const target = Number(pageInput);
    if (!/^\d+$/.test(pageInput) || !Number.isSafeInteger(target) || target < 1 || target > pageCount) {
      setPageError(`1부터 ${pageCount}까지 입력해 주세요.`); return;
    }
    setPageError('');
    if (target - 1 === page) setPageInput(String(page + 1));
    else goToPage(target - 1);
  };

  const saveHomepage = async (event) => {
    event.preventDefault();
    if (!selected || saving) return;
    setSaving(true); setError(''); setNotice('');
    try {
      await adminApi.updatePublicSeedHomepage(selected.id, url.trim(), selected.version);
      setSelected(null); setNotice('홈페이지 후보를 저장했습니다. 공식 여부는 확인이 필요합니다.');
      setRevision((value) => value + 1);
    } catch (cause) {
      setError(cause.status === 409 ? '다른 관리자가 변경했습니다. 목록을 다시 확인해주세요.' : cause.message);
    } finally { setSaving(false); }
  };

  const openAnalysis = async (seed) => {
    setAnalysisSeed(seed); setAnalysisError(''); setAts([]); setError(''); setNotice(''); setRejectId(null); setReviewId(null); setApprovalId(null);
    try { setAts((await adminApi.publicSeedAtsCandidates(seed.id)).data); }
    catch (cause) { setError(cause.message); }
  };
  const startReview = async () => {
    if (saving || !analysisSeed || reviewId === null) return;
    if (!reviewNote.trim()) { setError('검수 메모를 입력해주세요.'); return; }
    setSaving(true); setError(''); setNotice('');
    try {
      await adminApi.reviewPublicSeedAts(analysisSeed.id, reviewId, reviewNote.trim(), reference.trim() || null, analysisSeed.version);
      setReviewId(null); setReviewNote(''); setReference(''); setNotice('검수 메모를 저장했습니다. 소유권과 활성 공고는 확인되지 않았습니다.');
      setAts((await adminApi.publicSeedAtsCandidates(analysisSeed.id)).data);
    } catch (cause) { setError(cause.status === 409 ? '후보 또는 기업 정보가 변경됐습니다. 다시 열어 확인해주세요.' : cause.message); }
    finally { setSaving(false); }
  };
  const verifyBoard = async (candidateId) => {
    if (saving || !analysisSeed) return;
    setSaving(true); setError(''); setNotice('');
    try {
      const result = await adminApi.verifyPublicSeedBoard(analysisSeed.id, candidateId, analysisSeed.version);
      setNotice(`채용 게시판에서 공고 ${result.data.jobCount}건을 확인했습니다. 기업 소유권은 별도 확인이 필요합니다.`);
      setAts((await adminApi.publicSeedAtsCandidates(analysisSeed.id)).data);
    } catch (cause) { setError(cause.status === 409 ? '공고를 확인하지 못했거나 후보가 변경됐습니다.' : cause.message); }
    finally { setSaving(false); }
  };
  const approveTarget = async () => {
    if (saving || !analysisSeed || approvalId === null) return;
    if (!confirmOwnership || confirmCompany.trim() !== analysisSeed.companyName) {
      setError('기업명을 정확히 입력하고 소유권 확인 항목을 선택해주세요.'); return;
    }
    setSaving(true); setError(''); setNotice('');
    try {
      const result = await adminApi.approvePublicSeedTarget(analysisSeed.id, approvalId, analysisSeed.version,
        confirmCompany.trim(), confirmOwnership);
      setApprovalId(null); setNotice(`수집 대상 #${result.data.targetId}을 생성했습니다. 공고 수집이 시작될 수 있습니다.`);
      setAts((await adminApi.publicSeedAtsCandidates(analysisSeed.id)).data);
    } catch (cause) { setError(cause.status === 409 ? '검증이 만료됐거나 근거·상태가 변경됐습니다. 다시 확인해주세요.' : cause.message); }
    finally { setSaving(false); }
  };
  const rejectAts = async () => {
    if (saving || !analysisSeed || rejectId === null) return;
    if (!rejectReason.trim()) { setError('거절 사유를 입력해주세요.'); return; }
    setSaving(true); setError(''); setNotice('');
    try {
      await adminApi.rejectPublicSeedAts(analysisSeed.id, rejectId, rejectReason.trim());
      setRejectId(null); setRejectReason(''); setNotice('ATS 후보를 거절했습니다.');
      setAts((await adminApi.publicSeedAtsCandidates(analysisSeed.id)).data);
    } catch (cause) { setError(cause.status === 409 ? '이미 처리된 후보입니다. 다시 열어 확인해주세요.' : cause.message); }
    finally { setSaving(false); }
  };
  const analyze = async () => {
    if (!analysisSeed || saving) return;
    setSaving(true); setAnalysisError(''); setError(''); setNotice('');
    try {
      let seed = analysisSeed;
      let result;
      try {
        result = await adminApi.discoverPublicSeedHomepage(seed.id, seed.version);
      } catch (cause) {
        if (cause.status !== 409) throw cause;
        const latestPage = await adminApi.publicSeedPage({ page: String(page), size: String(size) });
        const latest = latestPage.data.items.find((item) => item.id === seed.id);
        if (!latest || latest.status !== 'DISCOVERED' || latest.homepageCandidateUrl !== seed.homepageCandidateUrl) {
          throw new Error('기업 또는 홈페이지 정보가 변경됐습니다. 목록을 새로고침한 뒤 다시 열어주세요.');
        }
        seed = latest;
        setAnalysisSeed(latest);
        setRows(latestPage.data.items);
        setTotal(latestPage.data.totalElements); setPageCount(latestPage.data.totalPages);
        result = await adminApi.discoverPublicSeedHomepage(seed.id, seed.version);
      }
      setAts((await adminApi.publicSeedAtsCandidates(analysisSeed.id)).data);
      setNotice(result.data.candidateCount > 0
        ? `검색 완료: 채용 게시판 후보 ${result.data.candidateCount}건을 찾았습니다. 후보의 공식 여부와 기업 소유권은 별도 확인이 필요합니다.`
        : '검색은 완료됐지만 인식 가능한 ATS 링크를 찾지 못했습니다. 이 홈페이지는 일반 게시판일 수 있습니다.');
    } catch (cause) {
      const message = cause.status === 502
        ? '홈페이지에 접근할 수 없거나 robots.txt에서 조회를 허용하지 않았습니다. 홈페이지 주소를 확인해주세요.'
        : cause.message || 'ATS 후보 검색 요청에 실패했습니다.';
      setAnalysisError(message);
    }
    finally { setSaving(false); }
  };

  const visible = rows || [];
  const firstPage = Math.max(0, Math.min(page - 2, pageCount - 5));
  const pageNumbers = Array.from({ length: Math.min(5, pageCount) }, (_, index) => firstPage + index);
  return <div className="dashboard-panel user-list">
    <h3>공공 기업 Seed</h3>
    <p>공공 구인기업 목록에서 수입한 기업입니다. 개발·IT 관련 Seed는 검색 API 설정 시 공식 홈페이지 후보를 자동으로 찾고, 기존 탐색기가 ATS 링크를 이어서 확인합니다. 검색 결과는 소유권이 검증되지 않은 후보이며 공고 수집 승인은 별도로 필요합니다.</p>
    {notice && <p role="status">{notice}</p>}
    {error ? <p className="form-error" role="alert">{error}</p> : !rows ? <p role="status">기업을 불러오는 중입니다.</p>
      : visible.length === 0 ? <p>수입된 공공 기업이 없습니다.</p> : <div className="table-scroll"><table>
        <thead><tr><th>공공 기업명</th><th>출처</th><th>직무 우선순위</th><th>Seed 상태</th><th>일치하는 회원 기업</th><th>회원 기업 상태</th><th>홈페이지 후보</th><th>입력</th></tr></thead>
        <tbody>{visible.map((seed) => <tr key={seed.id}>
          <td>{seed.companyName}</td><td>{seed.source === 'KOSME_JOB_COMPANIES' ? '중진공 구인기업' : seed.source}</td>
          <td>{seed.itRelatedPosting ? `개발·IT · ${seed.relevantPostingTitle || '관련 공고'}` : '일반'}</td>
          <td>{seed.status}</td><td>{seed.matchedCompanyId ? `${seed.matchedCompanyName} (#${seed.matchedCompanyId})` : '미확인'}</td>
          <td>{seed.companyStatus || '—'}</td><td>{seed.homepageCandidateUrl || '미입력'}
            {seed.homepageCandidateSource === 'NAVER_WEB_SEARCH' && <div>네이버 검색 후보 · 검색 점수 {seed.homepageCandidateScore}/100 · 공식 여부 미확인</div>}</td>
          <td>{seed.status === 'DISCOVERED' && <button type="button" onClick={() => {
            setSelected(seed); setUrl(seed.homepageCandidateUrl || ''); setNotice(''); setError('');
          }}>홈페이지 입력</button>}
          {seed.homepageCandidateUrl && <button type="button" onClick={() => openAnalysis(seed)}>ATS 후보 확인</button>}</td>
        </tr>)}</tbody></table></div>}
    {rows && <nav className="seed-pagination" aria-label="공공 기업 페이지">
      <div className="seed-pagination-summary"><span>전체 <strong>{total.toLocaleString()}</strong>개</span>
        <span>{pageCount ? page + 1 : 0} / {pageCount.toLocaleString()} 페이지</span>
        <label>페이지당 <select value={size} onChange={(event) => {
          setRows(null); setSelected(null); setAnalysisSeed(null); setPage(0); setSize(Number(event.target.value)); setPageError('');
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
        <label htmlFor="seed-page-number">페이지 이동</label>
        <input id="seed-page-number" type="number" min="1" max={pageCount || 1} inputMode="numeric"
          value={pageInput} disabled={!pageCount} onChange={(event) => { setPageInput(event.target.value); setPageError(''); }}
          aria-invalid={Boolean(pageError)} aria-describedby={pageError ? 'seed-page-error' : undefined} />
        <button type="submit" disabled={!pageCount}>이동</button>
        {pageError && <span id="seed-page-error" role="alert">{pageError}</span>}
      </form>
    </nav>}
    {selected && <form className="company-review-confirm" onSubmit={saveHomepage}>
      <p>{selected.companyName}의 홈페이지 후보를 입력합니다. 저장 후에도 공식 여부 확인이 필요합니다.</p>
      <label>홈페이지 후보 URL<input type="url" value={url} onChange={(event) => setUrl(event.target.value)}
        maxLength={500} required disabled={saving} placeholder="https://example.com" /></label>
      <div><button type="submit" disabled={saving}>후보 저장</button>
        <button type="button" disabled={saving} onClick={() => setSelected(null)}>취소</button></div>
    </form>}
    {analysisSeed && <div className="company-review-confirm">
      <p>{analysisSeed.companyName} 홈페이지의 공개 페이지에서 ATS 링크와 지원되는 채용 게시판을 검색합니다. 로봇 배제 규칙을 따르며, 후보의 공식 여부와 소유권은 별도 확인해야 합니다.</p>
      {analysisError && <p className="form-error" role="alert">{analysisError}</p>}
      <div><button type="button" disabled={saving} onClick={analyze}>{saving ? '홈페이지 확인 중…' : '홈페이지에서 ATS 자동 검색'}</button>
        <button type="button" disabled={saving} onClick={() => setAnalysisSeed(null)}>닫기</button></div>
      <p>저장된 채용 게시판 후보 (최대 100건)</p>
      <ul>{ats.map((item) => <li key={item.id}>{item.provider} · {item.sourceIdentifier} · {item.sourceUrl} · {item.status}
        {' · '}{item.discoverySource === 'AUTO_HOMEPAGE' ? '홈페이지 자동 탐색 후보' : '관리자 제공 HTML 후보'}
        <div>최초 입력 기준 홈페이지: {item.firstHomepageCandidateUrl || '기록 없음'}
          {' · '}{item.firstSeedVersion == null ? '기준 이력 없음' : item.firstSeedVersion !== item.currentSeedVersion
            ? '최초 발견 이후 홈페이지 입력 이력 변경됨' : '현재 홈페이지 입력과 동일'}</div>
        {item.reviewNote && <div>검수 메모: {item.reviewNote} · 참고 주소: {item.reviewReferenceUrl || '없음'}</div>}
        {item.boardVerifiedAt && <div>공고 확인: {item.boardJobCount}건 · {item.boardVerifiedAt} UTC
          {item.boardSeedVersion !== item.currentSeedVersion && ' · 기업 정보가 변경되어 재확인 필요'}</div>}
        {['VERIFYING', 'BOARD_VERIFIED'].includes(item.status) && <button type="button" disabled={saving} onClick={() => verifyBoard(item.id)}>{item.provider === 'COMPANY_PAGE' ? '게시판 공고 확인' : 'ATS 공고 확인'}</button>}
        {item.status === 'BOARD_VERIFIED' && admin?.role !== 'REVIEWER' && <button type="button" onClick={() => {
          setApprovalId(item.id); setConfirmCompany(''); setConfirmOwnership(false); setReviewId(null); setRejectId(null); setNotice(''); setError('');
        }}>수집 대상 승인</button>}
        {item.status === 'DISCOVERED' && <button type="button" onClick={() => {
          setReviewId(item.id); setReviewNote(''); setReference(''); setRejectId(null); setError(''); setNotice('');
        }}>검수 시작</button>}
        {['DISCOVERED', 'VERIFYING', 'BOARD_VERIFIED'].includes(item.status) && <button type="button" onClick={() => {
          setRejectId(item.id); setRejectReason(''); setError(''); setNotice('');
          setReviewId(null);
        }}>ATS 후보 거절</button>}
      </li>)}</ul>
      {rejectId !== null && <div>
        <label>ATS 후보 거절 사유<textarea value={rejectReason} onChange={(event) => setRejectReason(event.target.value)}
          maxLength={500} rows={3} disabled={saving} /></label>
        <button type="button" disabled={saving} onClick={rejectAts}>ATS 거절 확정</button>
        <button type="button" disabled={saving} onClick={() => setRejectId(null)}>거절 취소</button>
      </div>}
      {reviewId !== null && <div>
        <p>검수 기록만 저장합니다. 소유권과 활성 공고는 별도 확인해야 합니다.</p>
        <label>검수 메모<textarea value={reviewNote} onChange={(event) => setReviewNote(event.target.value)}
          maxLength={500} rows={3} disabled={saving} /></label>
        <label>참고 URL (선택)<input type="url" value={reference} onChange={(event) => setReference(event.target.value)}
          maxLength={1000} disabled={saving} /></label>
        <button type="button" disabled={saving} onClick={startReview}>검수 기록 저장</button>
        <button type="button" disabled={saving} onClick={() => setReviewId(null)}>검수 취소</button>
      </div>}
      {approvalId !== null && <div>
        <p>승인하면 수집기가 이 채용 게시판의 공고를 수집할 수 있습니다. 공식 홈페이지와 게시판이 같은 기업 소유인지 직접 확인해주세요.</p>
        <label>기업명 확인<input value={confirmCompany} onChange={(event) => setConfirmCompany(event.target.value)} disabled={saving} /></label>
        <label><input type="checkbox" checked={confirmOwnership} onChange={(event) => setConfirmOwnership(event.target.checked)} disabled={saving} />공식 홈페이지와 채용 게시판의 기업 소유권을 확인했습니다.</label>
        <button type="button" disabled={saving} onClick={approveTarget}>승인 및 수집 대상 생성</button>
        <button type="button" disabled={saving} onClick={() => setApprovalId(null)}>승인 취소</button>
      </div>}
    </div>}
  </div>;
}
