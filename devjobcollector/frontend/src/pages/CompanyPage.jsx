import { useEffect, useMemo, useState } from 'react';
import {
  Building2,
  CheckCircle2,
  Clock3,
  FileCheck2,
  RefreshCw,
  ShieldAlert,
} from 'lucide-react';
import { createCompany, fetchMyCompanies, requestCompanyVerification } from '../api/companyApi';
import MemberSidebar from '../components/member/MemberSidebar';
import '../styles/CompanyPage.css';
import '../styles/MemberPages.css';

const COMPANY_STATUS = {
  PENDING_VERIFICATION: ['인증 준비', '기업 정보를 등록했습니다. 증빙을 제출하면 관리자가 검토합니다.'],
  VERIFIED: ['인증 완료', '기업 인증이 완료되어 기업 기능을 사용할 수 있습니다.'],
  REJECTED: ['인증 반려', '기존 요청이 반려되었습니다. 증빙을 확인한 뒤 다시 요청해주세요.'],
  SUSPENDED: ['이용 정지', '현재 기업 기능 이용이 정지된 상태입니다.'],
  CLOSED: ['운영 종료', '운영이 종료된 기업입니다.'],
};

const VERIFICATION_STATUS = {
  PENDING: '관리자 검토 중',
  APPROVED: '승인 완료',
  REJECTED: '반려됨',
  CANCELLED: '요청 취소',
};

const ERROR_MESSAGES = {
  COMPANY_ALREADY_EXISTS: '이미 등록된 사업자번호입니다.',
  COMPANY_OWNER_REQUIRED: '기업 인증은 OWNER만 요청할 수 있습니다.',
  COMPANY_VERIFICATION_PENDING_EXISTS: '이미 검토 중인 인증 요청이 있습니다.',
  COMPANY_VERIFICATION_COMPANY_STATUS_INVALID: '현재 기업 상태에서는 인증을 요청할 수 없습니다.',
};

const formatDate = (value) => {
  if (!value) return '';
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
    .format(new Date(value));
};

const errorMessageFor = (error, fallback) => {
  const code = error.response?.data?.code || error.response?.data?.error;
  return ERROR_MESSAGES[code] || error.response?.data?.message || fallback;
};

const CompanyPage = () => {
  const [companies, setCompanies] = useState([]);
  const [selectedCompanyId, setSelectedCompanyId] = useState(null);
  const [companyForm, setCompanyForm] = useState({
    legalName: '', displayName: '', businessNumber: '', websiteUrl: '',
  });
  const [evidenceObjectKey, setEvidenceObjectKey] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [notice, setNotice] = useState('');

  const selectedCompany = useMemo(() => (
    companies.find((company) => company.companyId === selectedCompanyId) || companies[0] || null
  ), [companies, selectedCompanyId]);

  const loadCompanies = async () => {
    setLoading(true);
    setErrorMessage('');
    try {
      const result = await fetchMyCompanies();
      setCompanies(result);
      setSelectedCompanyId((current) => (
        result.some((company) => company.companyId === current) ? current : result[0]?.companyId ?? null
      ));
    } catch (error) {
      if (error.response?.status !== 401) {
        setErrorMessage('기업 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCompanies();
  }, []);

  const updateCompanyForm = (event) => {
    const { name, value } = event.target;
    const nextValue = name === 'businessNumber'
      ? value.replace(/[^0-9-]/g, '').slice(0, 12)
      : value;
    setCompanyForm((current) => ({ ...current, [name]: nextValue }));
  };

  const submitCompany = async (event) => {
    event.preventDefault();
    setSubmitting('company');
    setErrorMessage('');
    setNotice('');
    try {
      const created = await createCompany({
        ...companyForm,
        legalName: companyForm.legalName.trim(),
        displayName: companyForm.displayName.trim(),
        businessNumber: companyForm.businessNumber.trim(),
        websiteUrl: companyForm.websiteUrl.trim(),
      });
      await loadCompanies();
      setSelectedCompanyId(created.companyId);
      setNotice('기업 등록이 완료되었습니다. 이어서 인증 증빙을 제출해주세요.');
    } catch (error) {
      setErrorMessage(errorMessageFor(error, '기업 등록 중 오류가 발생했습니다.'));
    } finally {
      setSubmitting('');
    }
  };

  const submitVerification = async (event) => {
    event.preventDefault();
    if (!selectedCompany) return;
    setSubmitting('verification');
    setErrorMessage('');
    setNotice('');
    try {
      await requestCompanyVerification(selectedCompany.companyId, evidenceObjectKey.trim());
      setEvidenceObjectKey('');
      await loadCompanies();
      setNotice('기업 인증 요청을 접수했습니다. 관리자 검토 결과를 이 화면에서 확인할 수 있습니다.');
    } catch (error) {
      setErrorMessage(errorMessageFor(error, '기업 인증 요청 중 오류가 발생했습니다.'));
    } finally {
      setSubmitting('');
    }
  };

  const canRequestVerification = selectedCompany
    && selectedCompany.role === 'OWNER'
    && ['PENDING_VERIFICATION', 'REJECTED'].includes(selectedCompany.companyStatus)
    && selectedCompany.verificationStatus !== 'PENDING';

  return (
    <main className="member-page company-page">
      <div className="member-layout">
        <MemberSidebar />
        <div className="member-main">
          <header className="member-page-heading company-heading">
            <span className="member-eyebrow">COMPANY CENTER</span>
            <h1>기업 서비스</h1>
            <p>기업 정보를 등록하고 인증 진행 상태를 확인하세요.</p>
          </header>

          {loading && <section className="company-state-card" aria-live="polite">기업 정보를 불러오는 중입니다.</section>}
          {!loading && errorMessage && companies.length === 0 && (
            <section className="company-state-card error" role="alert">
              <ShieldAlert size={28} />
              <p>{errorMessage}</p>
              <button type="button" onClick={loadCompanies}><RefreshCw size={16} /> 다시 시도</button>
            </section>
          )}

          {!loading && companies.length === 0 && !errorMessage && (
            <section className="company-panel">
              <div className="company-panel-heading">
                <span><Building2 size={22} /></span>
                <div><h2>기업 정보 등록</h2><p>로그인 계정이 등록 기업의 첫 OWNER로 연결됩니다.</p></div>
              </div>
              <form className="company-form" onSubmit={submitCompany}>
                <label>법인명<input name="legalName" value={companyForm.legalName} onChange={updateCompanyForm} maxLength="200" autoComplete="organization" required /></label>
                <label>서비스 표시명<input name="displayName" value={companyForm.displayName} onChange={updateCompanyForm} maxLength="150" required /></label>
                <label>사업자등록번호<input name="businessNumber" value={companyForm.businessNumber} onChange={updateCompanyForm} inputMode="numeric" pattern="\d{3}-?\d{2}-?\d{5}" placeholder="123-45-67890" required /><small>중복 확인에 사용되며 원문은 저장하지 않습니다.</small></label>
                <label><span>기업 웹사이트 <em>선택</em></span><input type="url" name="websiteUrl" value={companyForm.websiteUrl} onChange={updateCompanyForm} maxLength="500" placeholder="https://example.com" /></label>
                <button type="submit" disabled={submitting === 'company'}>{submitting === 'company' ? '등록 중...' : '기업 등록하기'}</button>
              </form>
            </section>
          )}

          {!loading && companies.length > 0 && selectedCompany && (
            <>
              {companies.length > 1 && (
                <div className="company-switcher">
                  <label htmlFor="company-select">관리할 기업</label>
                  <select id="company-select" value={selectedCompany.companyId} onChange={(event) => setSelectedCompanyId(Number(event.target.value))}>
                    {companies.map((company) => <option key={company.companyId} value={company.companyId}>{company.displayName}</option>)}
                  </select>
                </div>
              )}
              <section className={`company-status-card status-${selectedCompany.companyStatus.toLowerCase()}`}>
                <div className="company-status-topline">
                  <span className="company-logo"><Building2 size={25} /></span>
                  <div>
                    <p>{selectedCompany.legalName}</p>
                    <h2>{selectedCompany.displayName}</h2>
                  </div>
                  <span className="company-status-badge">{COMPANY_STATUS[selectedCompany.companyStatus]?.[0] || selectedCompany.companyStatus}</span>
                </div>
                <p className="company-status-description">{COMPANY_STATUS[selectedCompany.companyStatus]?.[1]}</p>
                <dl className="company-summary-list">
                  <div><dt>사업자번호</dt><dd>{selectedCompany.businessNumberMasked}</dd></div>
                  <div><dt>내 역할</dt><dd>{selectedCompany.role}</dd></div>
                  <div><dt>소속 상태</dt><dd>{selectedCompany.membershipStatus}</dd></div>
                  {selectedCompany.websiteUrl && <div><dt>웹사이트</dt><dd><a href={selectedCompany.websiteUrl} target="_blank" rel="noreferrer">방문하기</a></dd></div>}
                </dl>
              </section>

              {selectedCompany.verificationStatus && (
                <section className="verification-timeline" aria-label="최근 기업 인증 요청">
                  <span className={selectedCompany.verificationStatus === 'APPROVED' ? 'approved' : ''}>
                    {selectedCompany.verificationStatus === 'PENDING' ? <Clock3 size={21} /> : <CheckCircle2 size={21} />}
                  </span>
                  <div>
                    <small>최근 인증 요청</small>
                    <h2>{VERIFICATION_STATUS[selectedCompany.verificationStatus] || selectedCompany.verificationStatus}</h2>
                    <p>{formatDate(selectedCompany.verificationReviewedAt || selectedCompany.verificationRequestedAt)}</p>
                  </div>
                </section>
              )}

              {canRequestVerification && (
                <section className="company-panel verification-panel">
                  <div className="company-panel-heading">
                    <span><FileCheck2 size={22} /></span>
                    <div><h2>기업 인증 요청</h2><p>사업자등록증 증빙의 Object Storage 키를 입력해주세요.</p></div>
                  </div>
                  <form className="company-form" onSubmit={submitVerification}>
                    <label>증빙 문서 키<input value={evidenceObjectKey} onChange={(event) => setEvidenceObjectKey(event.target.value)} maxLength="500" pattern="^(?!/)(?!.*\.\.)(?!.*://).+$" placeholder="company-verification/.../evidence.pdf" required /><small>파일 원문이나 공개 URL이 아닌 발급된 비공개 저장소 키만 입력합니다.</small></label>
                    <button type="submit" disabled={submitting === 'verification'}>{submitting === 'verification' ? '요청 중...' : '인증 요청 제출'}</button>
                  </form>
                </section>
              )}
            </>
          )}

          {notice && <p className="company-feedback notice" role="status">{notice}</p>}
          {errorMessage && companies.length > 0 && <p className="company-feedback error" role="alert">{errorMessage}</p>}
        </div>
      </div>
    </main>
  );
};

export default CompanyPage;
