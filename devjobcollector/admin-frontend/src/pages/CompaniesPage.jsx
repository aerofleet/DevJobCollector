import { useCallback, useEffect, useState } from 'react';
import { adminApi } from '../api/adminApi';
import DetailModal from '../components/DetailModal';

const statusText = {
  PENDING_VERIFICATION: '인증 대기', VERIFIED: '인증 완료',
  REJECTED: '반려', SUSPENDED: '정지', CLOSED: '종료',
};

export default function CompaniesPage() {
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [previewUrl, setPreviewUrl] = useState('');
  const [previewError, setPreviewError] = useState('');
  const [expanded, setExpanded] = useState(false);
  const [reviewAction, setReviewAction] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');
  const [reviewing, setReviewing] = useState(false);
  const selectedCompanyId = selected?.company.id;
  const latestRequestId = selected?.latestRequest?.id;
  const latestEvidenceContentType = selected?.latestRequest?.evidenceContentType;

  const load = useCallback(async () => {
    setError('');
    try {
      const params = { page: String(page), size: '20' };
      if (search) params.keyword = search;
      if (status) params.status = status;
      setResult((await adminApi.companies(params)).data);
    } catch (cause) { setError(cause.message); }
  }, [page, search, status]);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    setPreviewUrl('');
    setPreviewError('');
    if (!latestEvidenceContentType?.startsWith('image/')) return undefined;
    let active = true;
    let objectUrl = '';
    adminApi.companyEvidence(selectedCompanyId, latestRequestId)
      .then(({ blob }) => {
        if (!active) return;
        objectUrl = URL.createObjectURL(blob);
        setPreviewUrl(objectUrl);
      })
      .catch(() => { if (active) setPreviewError('이미지 미리보기를 불러오지 못했습니다.'); });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [selectedCompanyId, latestRequestId, latestEvidenceContentType]);

  const selectCompany = async (id) => {
    setError('');
    setNotice('');
    setExpanded(false);
    setReviewAction('');
    try { setSelected((await adminApi.company(id)).data); }
    catch (cause) { setError(cause.message); }
  };

  const downloadEvidence = async () => {
    setError('');
    try {
      const { blob, contentType } = await adminApi.companyEvidence(
        selected.company.id, selected.latestRequest.id,
      );
      const extension = contentType?.includes('pdf') ? 'pdf' : contentType?.includes('png') ? 'png' : 'jpg';
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `business-registration.${extension}`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (cause) { setError(cause.message); }
  };

  const submitReview = async () => {
    if (!selected || reviewing) return;
    if (reviewAction === 'reject' && !rejectionReason.trim()) {
      setError('반려 사유를 입력해주세요.');
      return;
    }
    setReviewing(true);
    setError('');
    try {
      const result = reviewAction === 'approve'
        ? await adminApi.approveCompany(selected.company.id, selected.latestRequest.id)
        : await adminApi.rejectCompany(selected.company.id, selected.latestRequest.id, rejectionReason.trim());
      setSelected(result.data);
      setNotice(reviewAction === 'approve' ? '기업 인증을 승인했습니다.' : '기업 인증을 반려했습니다.');
      setReviewAction('');
      setRejectionReason('');
      await load();
    } catch (cause) {
      setError(cause.status === 409
        ? '이미 처리된 요청이거나 증빙 파일을 확인할 수 없습니다. 기업 상세를 다시 열어주세요.'
        : cause.message);
    } finally { setReviewing(false); }
  };

  const closeDetail = () => {
    if (expanded) { setExpanded(false); return; }
    setSelected(null);
    setReviewAction('');
    setNotice('');
  };

  return <section className="users-page">
    <div className="page-heading"><div><p className="eyebrow">COMPANIES</p><h2>기업 심사</h2>
      <p>기업 상태와 최근 인증 요청을 확인합니다.</p></div></div>
    <form className="user-filters dashboard-panel" onSubmit={(event) => {
      event.preventDefault(); setPage(0); setSearch(keyword.trim());
    }}>
      <label>기업명<input value={keyword} onChange={(event) => setKeyword(event.target.value)} maxLength={100} /></label>
      <label>상태<select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
        <option value="">전체</option>{Object.entries(statusText).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
      <button className="secondary-button" type="submit">검색</button>
    </form>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="dashboard-panel user-list">
      {!result ? <p role="status">기업을 불러오는 중입니다.</p> : result.content.length === 0
        ? <p>조건에 맞는 기업이 없습니다.</p> : <div className="table-scroll"><table>
          <thead><tr><th>기업</th><th>법인명</th><th>사업자번호</th><th>상태</th><th></th></tr></thead>
          <tbody>{result.content.map((company) => <tr key={company.id}>
            <td>{company.displayName}</td><td>{company.legalName}</td>
            <td>{company.businessNumberMasked}</td><td>{statusText[company.status]}</td>
            <td><button type="button" onClick={() => selectCompany(company.id)}>상세</button></td>
          </tr>)}</tbody></table></div>}
      {result && <div className="user-pagination"><span>총 {result.totalElements}건 · {page + 1} / {Math.max(1, result.totalPages)} 페이지</span>
        <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>이전</button>
        <button type="button" disabled={page + 1 >= result.totalPages} onClick={() => setPage(page + 1)}>다음</button></div>}
    </div>
    {selected && <DetailModal title={expanded ? '사업자등록증 확대 보기' : '기업 상세'} onClose={closeDetail}
      closeDisabled={reviewing}>
      {error && <p className="form-error" role="alert">{error}</p>}
      {notice && <p className="company-review-notice" role="status">{notice}</p>}
      {expanded ? <div className="company-evidence-expanded">
        <button type="button" className="secondary-button" onClick={() => setExpanded(false)}>기업 상세로 돌아가기</button>
        <img src={previewUrl} alt="사업자등록증 확대 이미지" />
      </div> : <><dl><div><dt>기업명</dt><dd>{selected.company.displayName}</dd></div>
        <div><dt>법인명</dt><dd>{selected.company.legalName}</dd></div>
        <div><dt>사업자번호</dt><dd>{selected.company.businessNumberMasked}</dd></div>
        <div><dt>상태</dt><dd>{statusText[selected.company.status]}</dd></div>
        <div><dt>웹사이트</dt><dd>{selected.company.websiteUrl || '—'}</dd></div>
        <div><dt>최근 요청</dt><dd>{selected.latestRequest?.status || '없음'}</dd></div>
        <div><dt>요청 방식</dt><dd>{selected.latestRequest?.method || '—'}</dd></div>
        <div><dt>요청일</dt><dd>{selected.latestRequest?.requestedAt || '—'}</dd></div>
        <div><dt>반려 사유</dt><dd>{selected.latestRequest?.rejectionReason || '—'}</dd></div></dl>
      {selected.latestRequest?.evidenceContentType?.startsWith('image/') && (
        <div className="company-evidence-preview">
          <h4>첨부 이미지</h4>
          {previewUrl ? <button type="button" onClick={() => setExpanded(true)} aria-label="사업자등록증 이미지 확대">
            <img src={previewUrl} alt="사업자등록증 미리보기" />
            <span>클릭해서 크게 보기</span>
          </button> : <p role="status">{previewError || '이미지를 불러오는 중입니다.'}</p>}
        </div>
      )}
      {selected.latestRequest?.evidenceAvailable && <button type="button" className="secondary-button" onClick={downloadEvidence}>
        사업자등록증 다운로드
      </button>}
      {selected.company.status === 'PENDING_VERIFICATION'
        && selected.latestRequest?.status === 'PENDING'
        && selected.latestRequest.evidenceAvailable && <div className="company-review-actions">
          <button type="button" onClick={() => { setReviewAction('approve'); setError(''); }}>승인</button>
          <button type="button" onClick={() => { setReviewAction('reject'); setError(''); }}>반려</button>
        </div>}
      {reviewAction && <div className="company-review-confirm">
        <p>{reviewAction === 'approve' ? '첨부된 사업자등록증을 확인했나요? 승인하면 기업 기능을 사용할 수 있습니다.'
          : '반려 사유를 입력해주세요. 기업 담당자에게 결과가 표시됩니다.'}</p>
        {reviewAction === 'reject' && <label>반려 사유
          <textarea value={rejectionReason} onChange={(event) => setRejectionReason(event.target.value)}
            maxLength={500} rows={3} required />
        </label>}
        <div>
          <button type="button" className="primary-button" disabled={reviewing}
            onClick={submitReview}>{reviewing ? '처리 중...' : reviewAction === 'approve' ? '승인 확정' : '반려 확정'}</button>
          <button type="button" disabled={reviewing} onClick={() => setReviewAction('')}>취소</button>
        </div>
      </div>}
      </>}
    </DetailModal>}
  </section>;
}
