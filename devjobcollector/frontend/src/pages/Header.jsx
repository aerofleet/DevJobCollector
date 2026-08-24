// src/pages/Header.jsx
import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useScrollDirection } from '../hooks/useScrollDirection';
import '../styles/Header.css';

const Header = ({ onSearch }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const isNavVisible = useScrollDirection(100); // 100px 이상 스크롤 시 작동
  const isAuthenticated = Boolean(localStorage.getItem('accessToken'));

  const isMobile = () => window.innerWidth <= 768;

  const isActivePath = (path) => {
    if (path === '/') {
      return location.pathname === '/';
    }
    return location.pathname === path;
  };

  const handleSearch = (e) => {
    e.preventDefault();
    const keyword = searchQuery.trim();
    onSearch?.(keyword);
    // 모바일에서는 검색 후 패널 닫기
    if (isMobile()) {
      setIsSearchOpen(false);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    sessionStorage.removeItem('postLoginNextPath');
    setIsMenuOpen(false);
    navigate('/', { replace: true });
  };

  useEffect(() => {
    const handleEscape = (e) => {
      if (e.key === 'Escape') {
        setIsMenuOpen(false);
        setIsSearchOpen(false);
      }
    };

    window.addEventListener('keydown', handleEscape);
    return () => window.removeEventListener('keydown', handleEscape);
  }, []);

  useEffect(() => {
    const handleResize = () => {
      if (!isMobile()) {
        setIsMenuOpen(false);
        setIsSearchOpen(false);
      }
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  useEffect(() => {
    const shouldLock = isMobile() && (isMenuOpen || isSearchOpen);
    document.body.style.overflow = shouldLock ? 'hidden' : '';

    return () => {
      document.body.style.overflow = '';
    };
  }, [isMenuOpen, isSearchOpen]);

  return (
    <header className={`header ${isNavVisible ? '' : 'nav-collapsed'}`}>
      <div className="header-container">
        <div className="container g-0">
          {/* 로고 영역 */}
          <div className="logo-wrap">
            <Link to="/" className="brand-logo" aria-label="데브잡스 홈으로 이동">
              <span className="brand-mark" aria-hidden="true">D</span>
              <span>DevJobs</span>
            </Link>
          </div>

          {/* 검색 영역 */}
          <div className={`search-wrap ${isSearchOpen ? 'active' : ''}`}>
            <div className="search-box">
              <form onSubmit={handleSearch}>
                <input
                  type="text"
                  placeholder="검색어를 입력해주세요"
                  maxLength={50}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  autoComplete="off"
                />
                <button 
                  className="search-button" 
                  type="submit"
                  aria-label="검색"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24">
                    <g fill="none" fillRule="evenodd" stroke="#222" strokeWidth="2">
                      <circle cx="11.111" cy="11.111" r="7.111" strokeLinecap="round" strokeLinejoin="round" />
                      <path d="m20 20-3.867-3.867" />
                    </g>
                  </svg>
                </button>
              </form>
            </div>
            <button 
              className="close-button"
              onClick={() => setIsSearchOpen(false)}
            >
              닫기
            </button>
          </div>

          <div className="mobile-actions">
            {/* 모바일 검색 버튼 */}
            <button
              className="mobile-search-toggle"
              onClick={() => {
                setIsSearchOpen(!isSearchOpen);
                setIsMenuOpen(false);
              }}
              aria-label="검색 열기"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24">
                <g fill="none" fillRule="evenodd" stroke="#222" strokeWidth="2">
                  <circle cx="11.111" cy="11.111" r="7.111" strokeLinecap="round" strokeLinejoin="round" />
                  <path d="m20 20-3.867-3.867" />
                </g>
              </svg>
            </button>

            <button
              className="mobile-menu-toggle"
              onClick={() => {
                setIsMenuOpen(!isMenuOpen);
                setIsSearchOpen(false);
              }}
              aria-label="메뉴 열기"
              aria-expanded={isMenuOpen}
              aria-controls="mobile-menu-drawer"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" stroke="#222" strokeWidth="2" strokeLinecap="round">
                <path d="M4 7h16" />
                <path d="M4 12h16" />
                <path d="M4 17h16" />
              </svg>
            </button>
          </div>
        </div>

        {/* 네비게이션 */}
        <nav className={`nav ${isNavVisible ? 'nav-visible' : 'nav-hidden'}`} aria-label="Main navigation">
          <ul className="nav-left">
            <li><Link to="/jobs">개발자 채용</Link></li>
            <li><Link to={isAuthenticated ? '/resumes' : '/resume'}>이력서</Link></li>
            <li><a href="/#discovery-title">테마별 채용</a></li>
          </ul>

          <ul className="nav-right">
            {isAuthenticated ? (
              <>
                <li><Link className="nav-login-link" to="/my-devjobs">마이데브잡</Link></li>
                <li><button className="nav-logout-button" type="button" onClick={handleLogout}>로그아웃</button></li>
              </>
            ) : (
              <>
                <li><Link className="nav-login-link" to="/login">로그인</Link></li>
                <li><Link className="nav-signup-button" to="/signup">회원가입</Link></li>
              </>
            )}
          </ul>
        </nav>

        {isMenuOpen && (
          <button
            type="button"
            className="mobile-menu-backdrop"
            onClick={() => setIsMenuOpen(false)}
            aria-label="메뉴 닫기"
          />
        )}

        <aside
          id="mobile-menu-drawer"
          className={`mobile-menu-drawer ${isMenuOpen ? 'open' : ''}`}
          aria-hidden={!isMenuOpen}
        >
          <div className="mobile-menu-header">
            <strong>메뉴</strong>
            <button type="button" onClick={() => setIsMenuOpen(false)} aria-label="닫기">닫기</button>
          </div>

          <ul className="mobile-menu-list">
            <li><Link onClick={() => setIsMenuOpen(false)} className={isActivePath('/jobs') ? 'active' : ''} to="/jobs">개발자 채용</Link></li>
            <li><Link onClick={() => setIsMenuOpen(false)} className={isActivePath('/resumes') || isActivePath('/resume') ? 'active' : ''} to={isAuthenticated ? '/resumes' : '/resume'}>이력서</Link></li>
            <li><a onClick={() => setIsMenuOpen(false)} href="/#discovery-title">테마별 채용</a></li>
          </ul>

          <div className="mobile-menu-auth">
            {isAuthenticated ? (
              <>
                <Link onClick={() => setIsMenuOpen(false)} className={isActivePath('/my-devjobs') ? 'active' : ''} to="/my-devjobs">마이데브잡</Link>
                <button type="button" className="mobile-logout-button" onClick={handleLogout}>로그아웃</button>
              </>
            ) : (
              <>
                <Link onClick={() => setIsMenuOpen(false)} className={isActivePath('/login') ? 'active' : ''} to="/login">로그인</Link>
                <Link onClick={() => setIsMenuOpen(false)} className={isActivePath('/signup') ? 'active signup' : 'signup'} to="/signup">회원가입</Link>
              </>
            )}
          </div>
        </aside>
      </div>

      <nav className="mobile-tabbar" aria-label="모바일 빠른 메뉴">
        <Link className={`mobile-tab-link ${isActivePath('/jobs') ? 'active' : ''}`} to="/jobs">채용</Link>
        <Link className={`mobile-tab-link ${isActivePath('/resumes') || isActivePath('/resume') ? 'active' : ''}`} to={isAuthenticated ? '/resumes' : '/resume'}>이력서</Link>
        <Link className={`mobile-tab-link ${isActivePath(isAuthenticated ? '/my-devjobs' : '/login') ? 'active' : ''}`} to={isAuthenticated ? '/my-devjobs' : '/login'}>{isAuthenticated ? 'MY' : '로그인'}</Link>
        <button
          type="button"
          className={`mobile-tab-more ${isMenuOpen ? 'active' : ''}`}
          onClick={() => {
            setIsMenuOpen(!isMenuOpen);
            setIsSearchOpen(false);
          }}
          aria-expanded={isMenuOpen}
          aria-controls="mobile-menu-drawer"
        >
          메뉴
        </button>
      </nav>
    </header>
  );
};

export default Header;
