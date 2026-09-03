import React, { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { loginWithPassword } from '../api/authApi';
import { startAccountLink } from '../api/authenticatedApi';
import '../styles/LoginPage.css';

const LoginPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [linkProvider, setLinkProvider] = useState('');
  const [isLinking, setIsLinking] = useState(false);

  const authServerBaseUrl = useMemo(() => {
    const explicit = import.meta.env.VITE_AUTH_BASE_URL;
    if (explicit) {
      return explicit.replace(/\/$/, '');
    }
    const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';
    return apiBase.replace(/\/api\/v1\/?$/, '');
  }, []);

  useEffect(() => {
    const query = new URLSearchParams(location.search);
    const token = query.get('token');
    const oauthError = query.get('error');
    const oauthProvider = query.get('provider');
    const next = query.get('next') || sessionStorage.getItem('postLoginNextPath');
    const fallbackPath = '/member';
    const redirectTo = next || fallbackPath;

    const authFailureReason = sessionStorage.getItem('authFailureReason');
    if (authFailureReason) {
      sessionStorage.removeItem('authFailureReason');
      setErrorMessage(`로그인 세션 확인에 실패했습니다. (${authFailureReason})`);
    }

    if (oauthError) {
      if (oauthError === 'ACCOUNT_LINK_REQUIRED') {
        const provider = ['google', 'github'].includes(oauthProvider) ? oauthProvider : '';
        setLinkProvider(provider);
        setErrorMessage(localStorage.getItem('accessToken')
          ? '현재 로그인한 기존 계정에 소셜 계정을 연결할 수 있습니다.'
          : '같은 이메일의 기존 계정으로 먼저 로그인해주세요. 로그인 후 소셜 계정을 연결할 수 있습니다.');
      } else if (oauthError === 'ACCOUNT_LINK_INVALID') {
        setErrorMessage('계정 연결 요청이 만료되었거나 올바르지 않습니다. 다시 시도해주세요.');
      } else if (oauthError === 'ACCOUNT_LINK_CONFLICT') {
        setErrorMessage('해당 소셜 계정은 연결할 수 없습니다. 이메일과 기존 연결 상태를 확인해주세요.');
      } else {
        setErrorMessage('소셜 로그인에 실패했습니다. 잠시 후 다시 시도해주세요.');
      }
      return;
    }

    if (!token) {
      return;
    }

    localStorage.setItem('accessToken', token);
    sessionStorage.removeItem('postLoginNextPath');
    navigate(redirectTo, { replace: true });
  }, [location.search, navigate]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setErrorMessage('');
    setIsSubmitting(true);
    try {
      const data = await loginWithPassword({
        identifier: identifier.trim(),
        password,
      });
      localStorage.setItem('accessToken', data.accessToken);

      const query = new URLSearchParams(location.search);
      const next = query.get('next') || '/';
      sessionStorage.removeItem('postLoginNextPath');
      navigate(next, { replace: true });
    } catch (error) {
      if (error.response?.status === 401) {
        setErrorMessage('아이디(이메일) 또는 비밀번호가 올바르지 않습니다.');
      } else {
        setErrorMessage('로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const googleLoginUrl = `${authServerBaseUrl}/oauth2/authorization/google`;
  const githubLoginUrl = `${authServerBaseUrl}/oauth2/authorization/github`;
  const rememberNextPath = () => {
    const query = new URLSearchParams(location.search);
    const next = query.get('next');
    if (next) {
      sessionStorage.setItem('postLoginNextPath', next);
    }
  };

  const handleAccountLink = async () => {
    if (!linkProvider) {
      setErrorMessage('연결할 소셜 로그인 정보를 확인할 수 없습니다. 다시 로그인해주세요.');
      return;
    }
    setIsLinking(true);
    setErrorMessage('');
    try {
      const data = await startAccountLink(linkProvider);
      window.location.assign(`${authServerBaseUrl}${data.authorizationPath}`);
    } catch (error) {
      if (error.response?.status === 401) {
        localStorage.removeItem('accessToken');
        setErrorMessage('기존 계정 로그인 세션이 만료되었습니다. 다시 로그인해주세요.');
      } else if (error.response?.status === 409) {
        setErrorMessage('이미 연결된 소셜 계정입니다. 기존 로그인 방식을 이용해주세요.');
      } else {
        setErrorMessage('계정 연결을 시작하지 못했습니다. 잠시 후 다시 시도해주세요.');
      }
      setIsLinking(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login_input_wrap">
        <div className="login-form-container">
          <h2 className="login_title">로그인</h2>
          <p className="login_subtitle">
            데브잡스 계정으로 로그인하고 커리어 탐색을 이어가세요.
          </p>

          <form className="login-form" onSubmit={handleSubmit}>
            <div className="id-input-box">
              <label className="sr-only" htmlFor="identifier">
                아이디 또는 이메일
              </label>
              <input
                type="text"
                id="identifier"
                name="identifier"
                placeholder="아이디 또는 이메일"
                autoComplete="username"
                value={identifier}
                onChange={(event) => setIdentifier(event.target.value)}
                required
              />
            </div>

            <div className="pw-input-box">
              <label className="sr-only" htmlFor="password">
                비밀번호
              </label>
              <input
                type="password"
                id="password"
                name="password"
                placeholder="비밀번호"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
              />
            </div>

            <div className="setting">
              <div className="InpBox">
                <input type="checkbox" id="autologin" name="autologin" />
                <label htmlFor="autologin">로그인 유지</label>
              </div>
              <div className="InpBox">
                <input type="checkbox" id="id_save" name="id_save" />
                <label htmlFor="id_save">아이디 저장</label>
              </div>
            </div>

            <button type="submit" className="btn_login" disabled={isSubmitting}>
              {isSubmitting ? '로그인 중...' : '로그인'}
            </button>
            {errorMessage && <p style={{ color: '#d64545', marginTop: '12px' }}>{errorMessage}</p>}
            {linkProvider && localStorage.getItem('accessToken') && (
              <button
                type="button"
                className="btn_account_link"
                onClick={handleAccountLink}
                disabled={isLinking}
              >
                {isLinking
                  ? '계정 연결 준비 중...'
                  : `${linkProvider === 'google' ? 'Google' : 'GitHub'} 계정 연결`}
              </button>
            )}
          </form>

          <div className="signup-forgotten">
            <a href="/find-id">아이디 찾기</a>
            <span className="divider">|</span>
            <a href="/find-pw">비밀번호 찾기</a>
          </div>
          <div className="login-signup-section">
            <p>아직 데브잡스 계정이 없나요?</p>
            <Link to="/signup" className="login-signup-button">회원가입</Link>
          </div>
          <div className="social_login_list ">
            <a className="social_icon google" title="google" href={googleLoginUrl} onClick={rememberNextPath}></a>
            {/* <a className="social_icon kakao" title="kakao" href="#"></a>
            <a className="social_icon naver" title="naver" href="#"></a> */}
            <a className="social_icon github" title="github" href={githubLoginUrl} onClick={rememberNextPath}></a>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
