import { LockKeyhole, ShieldCheck } from 'lucide-react';
import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAdminAuth } from '../auth/AdminAuthContext';

const LoginPage = () => {
  const { admin, isLoading, login } = useAdminAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  if (!isLoading && admin) return <Navigate to="/" replace />;

  const submit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      await login({ email: email.trim(), password, mfaCode: mfaCode.trim() });
      navigate(location.state?.from?.pathname || '/', { replace: true });
    } catch (requestError) {
      if (requestError.status === 429) {
        setError('로그인 시도가 제한되었습니다. 잠시 후 다시 시도해주세요.');
      } else if (requestError.status === 423) {
        setError('관리자 계정이 잠겼습니다. SUPER_ADMIN에게 문의해주세요.');
      } else {
        setError('관리자 인증 정보를 확인해주세요.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="login-page">
      <section className="login-story" aria-label="관리자 보안 안내">
        <div className="story-content">
          <span className="story-kicker"><ShieldCheck size={16} /> SECURE OPERATIONS</span>
          <h1>채용 운영의 모든 결정을<br />하나의 기록으로.</h1>
          <p>회원, 기업, 공고의 상태 변경은 권한 검증과 감사 기록을 거쳐 처리됩니다.</p>
          <div className="security-note"><LockKeyhole size={18} /> 일반 회원 계정과 분리된 관리자 인증 영역입니다.</div>
        </div>
      </section>
      <section className="login-panel">
        <form className="login-card" onSubmit={submit}>
          <div className="login-logo">D</div>
          <p className="eyebrow">DEVJOBS ADMIN</p>
          <h2>관리자 로그인</h2>
          <p className="form-intro">승인된 운영 계정으로만 접근할 수 있습니다.</p>
          <label htmlFor="admin-email">관리자 이메일</label>
          <input id="admin-email" type="email" autoComplete="username" value={email} onChange={(event) => setEmail(event.target.value)} required />
          <label htmlFor="admin-password">비밀번호</label>
          <input id="admin-password" type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required />
          <label htmlFor="admin-mfa">인증 코드</label>
          <input id="admin-mfa" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength="6" placeholder="6자리 코드" value={mfaCode} onChange={(event) => setMfaCode(event.target.value.replace(/\D/g, ''))} required />
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="primary-button" type="submit" disabled={submitting}>
            {submitting ? '인증 중...' : '보안 로그인'}
          </button>
          <p className="form-footnote">접근 및 실패 기록은 보안 감사 대상으로 저장됩니다.</p>
        </form>
      </section>
    </main>
  );
};

export default LoginPage;
