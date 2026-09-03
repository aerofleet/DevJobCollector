import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { loginWithPassword } from '../api/authApi';
import { startAccountLink } from '../api/authenticatedApi';
import '../styles/LoginPage.css';

const PENDING_LINK_PROVIDER_KEY = 'pendingAccountLinkProvider';
const LINK_IN_PROGRESS_PROVIDER_KEY = 'accountLinkInProgressProvider';
const ACCOUNT_LINK_FLOW_TTL_MS = 10 * 60 * 1000;
const SUPPORTED_LINK_PROVIDERS = ['google', 'github'];

const storePendingLinkProvider = (provider) => {
  sessionStorage.setItem(PENDING_LINK_PROVIDER_KEY, JSON.stringify({
    provider,
    createdAt: Date.now(),
  }));
};

const readPendingLinkProvider = () => {
  try {
    const raw = sessionStorage.getItem(PENDING_LINK_PROVIDER_KEY);
    if (!raw) return '';
    const pending = JSON.parse(raw);
    const isValid = SUPPORTED_LINK_PROVIDERS.includes(pending.provider)
      && Number.isFinite(pending.createdAt)
      && Date.now() - pending.createdAt <= ACCOUNT_LINK_FLOW_TTL_MS;
    if (isValid) return pending.provider;
  } catch {
    // Invalid session state is discarded below.
  }
  sessionStorage.removeItem(PENDING_LINK_PROVIDER_KEY);
  sessionStorage.removeItem(LINK_IN_PROGRESS_PROVIDER_KEY);
  return '';
};

const clearAccountLinkFlow = () => {
  sessionStorage.removeItem(PENDING_LINK_PROVIDER_KEY);
  sessionStorage.removeItem(LINK_IN_PROGRESS_PROVIDER_KEY);
};

const LoginPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [linkProvider, setLinkProvider] = useState('');
  const [isLinking, setIsLinking] = useState(false);
  const linkStartAttemptRef = useRef(false);

  const authServerBaseUrl = useMemo(() => {
    const explicit = import.meta.env.VITE_AUTH_BASE_URL;
    if (explicit) {
      return explicit.replace(/\/$/, '');
    }
    const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';
    return apiBase.replace(/\/api\/v1\/?$/, '');
  }, []);

  const beginAccountLink = useCallback(async (provider) => {
    if (!provider || linkStartAttemptRef.current) return;
    linkStartAttemptRef.current = true;
    setIsLinking(true);
    setErrorMessage('');
    try {
      const data = await startAccountLink(provider);
      storePendingLinkProvider(provider);
      sessionStorage.setItem(LINK_IN_PROGRESS_PROVIDER_KEY, provider);
      window.location.assign(`${authServerBaseUrl}${data.authorizationPath}`);
    } catch (error) {
      linkStartAttemptRef.current = false;
      sessionStorage.removeItem(LINK_IN_PROGRESS_PROVIDER_KEY);
      setLinkProvider(provider);
      if (error.response?.status === 401) {
        localStorage.removeItem('accessToken');
        setErrorMessage('보안을 위해 기존 가입 방식으로 다시 로그인해주세요.');
      } else if (error.response?.status === 409) {
        clearAccountLinkFlow();
        setErrorMessage('이미 연결된 소셜 계정입니다. 해당 로그인 방식을 다시 이용해주세요.');
      } else {
        setErrorMessage('계정 연결을 시작하지 못했습니다. 잠시 후 다시 시도해주세요.');
      }
      setIsLinking(false);
    }
  }, [authServerBaseUrl]);

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
        const provider = SUPPORTED_LINK_PROVIDERS.includes(oauthProvider) ? oauthProvider : '';
        const linkWasInProgress = provider
          && sessionStorage.getItem(LINK_IN_PROGRESS_PROVIDER_KEY) === provider;
        if (provider) {
          storePendingLinkProvider(provider);
        }
        setLinkProvider(provider);
        if (localStorage.getItem('accessToken') && provider && !linkWasInProgress) {
          void beginAccountLink(provider);
          return;
        }
        if (linkWasInProgress) {
          sessionStorage.removeItem(LINK_IN_PROGRESS_PROVIDER_KEY);
          setErrorMessage('계정 연결 세션을 확인하지 못했습니다. 다시 시도해주세요.');
        } else {
          setErrorMessage('같은 이메일의 기존 가입 방식으로 로그인해주세요. 인증 후 계정 연결을 자동으로 계속합니다.');
        }
      } else if (oauthError === 'ACCOUNT_LINK_INVALID') {
        sessionStorage.removeItem(LINK_IN_PROGRESS_PROVIDER_KEY);
        setLinkProvider(readPendingLinkProvider() || oauthProvider || '');
        setErrorMessage('계정 연결 요청이 만료되었거나 올바르지 않습니다. 다시 시도해주세요.');
      } else if (oauthError === 'ACCOUNT_LINK_CONFLICT') {
        sessionStorage.removeItem(LINK_IN_PROGRESS_PROVIDER_KEY);
        setLinkProvider(readPendingLinkProvider() || oauthProvider || '');
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
    const pendingLinkProvider = readPendingLinkProvider();
    if (pendingLinkProvider) {
      const linkInProgressProvider = sessionStorage.getItem(LINK_IN_PROGRESS_PROVIDER_KEY);
      if (linkInProgressProvider === pendingLinkProvider) {
        clearAccountLinkFlow();
        navigate('/member', { replace: true });
        return;
      }
      void beginAccountLink(pendingLinkProvider);
      return;
    }
    navigate(redirectTo, { replace: true });
  }, [beginAccountLink, location.search, navigate]);

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
  const rememberNextPath = (selectedProvider) => {
    const query = new URLSearchParams(location.search);
    const next = query.get('next');
    if (next) {
      sessionStorage.setItem('postLoginNextPath', next);
    }
    if (linkProvider && selectedProvider !== linkProvider) {
      storePendingLinkProvider(linkProvider);
    }
  };

  const handleAccountLink = async () => {
    if (!linkProvider) {
      setErrorMessage('연결할 소셜 로그인 정보를 확인할 수 없습니다. 다시 로그인해주세요.');
      return;
    }
    await beginAccountLink(linkProvider);
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
            <a className="social_icon google" title="google" href={googleLoginUrl} onClick={() => rememberNextPath('google')}></a>
            {/* <a className="social_icon kakao" title="kakao" href="#"></a>
            <a className="social_icon naver" title="naver" href="#"></a> */}
            <a className="social_icon github" title="github" href={githubLoginUrl} onClick={() => rememberNextPath('github')}></a>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
