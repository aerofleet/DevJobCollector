import { useCallback, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  resendPersonalVerification,
  signupPersonal,
  verifyPersonalEmail,
} from '../api/authApi';
import SocialLoginList from '../components/auth/SocialLoginList';
import TurnstileWidget from '../components/auth/TurnstileWidget';
import '../styles/SignupPage.css';

const messageFor = (error, fallback) => error.response?.data?.detail
  || error.response?.data?.message
  || fallback;

const SignupPage = () => {
  const navigate = useNavigate();
  const [memberType, setMemberType] = useState('personal');
  const [step, setStep] = useState('form');
  const [form, setForm] = useState({
    name: '', email: '', password: '', passwordConfirm: '', termsAccepted: false, privacyAccepted: false,
  });
  const [code, setCode] = useState('');
  const [turnstileToken, setTurnstileToken] = useState('');
  const [turnstileVersion, setTurnstileVersion] = useState(0);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [notice, setNotice] = useState('');
  const siteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY || '';

  const authServerBaseUrl = useMemo(() => {
    const explicit = import.meta.env.VITE_AUTH_BASE_URL;
    if (explicit) return explicit.replace(/\/$/, '');
    const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';
    return apiBase.replace(/\/api\/v1\/?$/, '');
  }, []);

  const onTurnstileToken = useCallback((token) => setTurnstileToken(token), []);

  const selectMemberType = (type) => {
    setMemberType(type);
    if (type === 'company') {
      sessionStorage.setItem('postLoginNextPath', '/company');
    } else if (sessionStorage.getItem('postLoginNextPath') === '/company') {
      sessionStorage.removeItem('postLoginNextPath');
    }
  };

  const update = (event) => {
    const { name, value, checked, type } = event.target;
    setForm((current) => ({ ...current, [name]: type === 'checkbox' ? checked : value }));
  };

  const submitSignup = async (event) => {
    event.preventDefault();
    setErrorMessage('');
    setNotice('');
    if (form.password !== form.passwordConfirm) {
      setErrorMessage('비밀번호 확인이 일치하지 않습니다.');
      return;
    }
    if (siteKey && !turnstileToken) {
      setErrorMessage('봇 방지 인증을 완료해주세요.');
      return;
    }
    setIsSubmitting(true);
    try {
      const result = await signupPersonal({
        email: form.email.trim(),
        name: form.name.trim(),
        password: form.password,
        termsAccepted: form.termsAccepted,
        privacyAccepted: form.privacyAccepted,
        turnstileToken,
      });
      setForm((current) => ({ ...current, email: result.email }));
      if (result.developmentVerificationCode) {
        setCode(result.developmentVerificationCode);
        setNotice(`로컬 개발용 인증 코드: ${result.developmentVerificationCode}`);
      } else {
        setNotice(`${result.verificationExpiresMinutes}분 안에 이메일 인증 코드를 입력해주세요.`);
      }
      setTurnstileToken('');
      setStep('verify');
    } catch (error) {
      setErrorMessage(messageFor(error, '회원가입 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  const submitVerification = async (event) => {
    event.preventDefault();
    setErrorMessage('');
    setIsSubmitting(true);
    try {
      const result = await verifyPersonalEmail({ email: form.email, code });
      localStorage.setItem('accessToken', result.accessToken);
      const destination = memberType === 'company' ? '/company' : '/member';
      sessionStorage.removeItem('postLoginNextPath');
      navigate(destination, { replace: true });
    } catch (error) {
      setErrorMessage(messageFor(error, '인증 코드 확인 중 오류가 발생했습니다.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  const resend = async () => {
    setErrorMessage('');
    if (siteKey && !turnstileToken) {
      setErrorMessage('재발송 전에 봇 방지 인증을 완료해주세요.');
      return;
    }
    setIsSubmitting(true);
    try {
      const result = await resendPersonalVerification({ email: form.email, turnstileToken });
      setCode(result.developmentVerificationCode || '');
      setNotice(result.developmentVerificationCode
        ? `로컬 개발용 인증 코드: ${result.developmentVerificationCode}`
        : '새 인증 코드를 발송했습니다.');
    } catch (error) {
      setErrorMessage(messageFor(error, '인증 코드 재발송에 실패했습니다.'));
    } finally {
      if (siteKey) {
        setTurnstileToken('');
        setTurnstileVersion((current) => current + 1);
      }
      setIsSubmitting(false);
    }
  };

  return (
    <main className="signup-page">
      <section className="signup-card">
        <div className="signup-heading">
          <span>DEVJOBS ACCOUNT</span>
          <h1>회원가입</h1>
          <p>관심 있는 개발자 채용공고를 저장하고 커리어 탐색을 이어가세요.</p>
        </div>

        <div className="member-type-tabs" role="group" aria-label="회원 유형">
          <button type="button" className={memberType === 'personal' ? 'active' : ''} aria-pressed={memberType === 'personal'} onClick={() => selectMemberType('personal')}>개인회원</button>
          <button type="button" className={memberType === 'company' ? 'active' : ''} aria-pressed={memberType === 'company'} onClick={() => selectMemberType('company')}>기업회원 <small>기업 등록</small></button>
        </div>

        {memberType === 'company' && step === 'form' && (
          <p className="company-signup-guide" role="status">
            담당자 계정을 만든 뒤 기업 정보 등록과 인증을 이어서 진행합니다.
          </p>
        )}

        {step === 'form' ? (
          <>
            <SocialLoginList authServerBaseUrl={authServerBaseUrl} />
            <div className="signup-divider"><span>또는 이메일로 가입</span></div>
            <form className="signup-form" onSubmit={submitSignup}>
              <div className="signup-field"><label htmlFor="signup-name">이름</label><input id="signup-name" name="name" value={form.name} onChange={update} minLength="2" maxLength="50" autoComplete="name" required /></div>
              <div className="signup-field"><label htmlFor="signup-email">이메일</label><input id="signup-email" type="email" name="email" value={form.email} onChange={update} autoComplete="email" required /></div>
              <div className="signup-field"><label htmlFor="signup-password">비밀번호</label><input id="signup-password" type="password" name="password" value={form.password} onChange={update} minLength="8" maxLength="72" autoComplete="new-password" aria-describedby="signup-password-hint" required /><small id="signup-password-hint">8자 이상 입력해주세요.</small></div>
              <div className="signup-field"><label htmlFor="signup-password-confirm">비밀번호 확인</label><input id="signup-password-confirm" type="password" name="passwordConfirm" value={form.passwordConfirm} onChange={update} minLength="8" maxLength="72" autoComplete="new-password" required /></div>
              <div className="signup-consents">
                <label><input type="checkbox" name="termsAccepted" checked={form.termsAccepted} onChange={update} required /> (필수) <Link to="/terms" target="_blank" rel="noreferrer">이용약관</Link> 동의</label>
                <label><input type="checkbox" name="privacyAccepted" checked={form.privacyAccepted} onChange={update} required /> (필수) <Link to="/privacy" target="_blank" rel="noreferrer">개인정보 처리방침</Link> 동의</label>
              </div>
              <TurnstileWidget key={turnstileVersion} siteKey={siteKey} onToken={onTurnstileToken} />
              <button className="signup-submit" disabled={isSubmitting}>{isSubmitting ? '가입 처리 중...' : memberType === 'company' ? '가입 후 기업 등록하기' : '이메일로 가입하기'}</button>
            </form>
          </>
        ) : (
          <form className="verification-form" onSubmit={submitVerification}>
            <div className="mail-illustration">✉</div>
            <h2>이메일을 확인해주세요</h2>
            <p><strong>{form.email}</strong>로 보낸 6자리 코드를 입력하세요.</p>
            <input aria-label="이메일 인증 코드" inputMode="numeric" pattern="[0-9]{6}" maxLength="6" value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))} placeholder="000000" required />
            <button className="signup-submit" disabled={isSubmitting || code.length !== 6}>{isSubmitting ? '확인 중...' : '인증하고 시작하기'}</button>
            <TurnstileWidget key={turnstileVersion} siteKey={siteKey} onToken={onTurnstileToken} />
            <button type="button" className="resend-button" onClick={resend} disabled={isSubmitting}>인증 코드 다시 받기</button>
          </form>
        )}

        {notice && <p className="signup-notice" role="status">{notice}</p>}
        {errorMessage && <p className="signup-error" role="alert">{errorMessage}</p>}
        <p className="login-link">이미 계정이 있나요? <Link to={memberType === 'company' ? '/login?next=/company' : '/login'}>로그인</Link></p>
      </section>
    </main>
  );
};

export default SignupPage;
