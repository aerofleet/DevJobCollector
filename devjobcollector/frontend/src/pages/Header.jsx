// src/pages/Header.jsx
import React, { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useScrollDirection } from '../hooks/useScrollDirection';
import '../styles/Header.css';

const Header = ({ onSearch }) => {
  const location = useLocation();
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const isNavVisible = useScrollDirection(100); // 100px 이상 스크롤 시 작동

  const isMobile = () => window.innerWidth <= 768;

  const isActivePath = (path) => {
    if (path === '/') {
      return location.pathname === '/' || location.pathname.startsWith('/job/');
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

  useEffect(() => {
    setIsMenuOpen(false);
    setIsSearchOpen(false);
  }, [location.pathname]);

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
            <a href="/" aria-label="홈으로 이동">
              <svg xmlns="http://www.w3.org/2000/svg" width="62" height="28" viewBox="0 0 62 28">
                <text x="0" y="20" fontSize="20" fontWeight="bold">LOGO</text>
              </svg>
            </a>
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
            <li><Link to="/">채용 공고</Link></li>
            <li><Link to="/resume">이력서</Link></li>
            <li><Link to="/feed">#꿀 피드</Link></li>
            <li><Link to="/job-interview">개발자 인터뷰</Link></li>
          </ul>

          <ul className="nav-right">
            <li><Link to="/login">로그인</Link></li>
            <li><Link to="/register">회원가입</Link></li>
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
            <li><Link className={isActivePath('/') ? 'active' : ''} to="/">채용 공고</Link></li>
            <li><Link className={isActivePath('/resume') ? 'active' : ''} to="/resume">이력서</Link></li>
            <li><Link className={isActivePath('/feed') ? 'active' : ''} to="/feed">#꿀 피드</Link></li>
            <li><Link className={isActivePath('/job-interview') ? 'active' : ''} to="/job-interview">개발자 인터뷰</Link></li>
          </ul>

          <div className="mobile-menu-auth">
            <Link className={isActivePath('/login') ? 'active' : ''} to="/login">로그인</Link>
            <Link className={isActivePath('/register') ? 'active' : ''} to="/register">회원가입</Link>
          </div>
        </aside>
      </div>

      <nav className="mobile-tabbar" aria-label="모바일 빠른 메뉴">
        <Link className={`mobile-tab-link ${isActivePath('/') ? 'active' : ''}`} to="/">채용</Link>
        <Link className={`mobile-tab-link ${isActivePath('/resume') ? 'active' : ''}`} to="/resume">이력서</Link>
        <Link className={`mobile-tab-link ${isActivePath('/feed') ? 'active' : ''}`} to="/feed">피드</Link>
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
