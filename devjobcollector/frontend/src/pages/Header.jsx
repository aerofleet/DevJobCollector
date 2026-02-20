// src/pages/Header.jsx
import React, { useState } from 'react';
import { useScrollDirection } from '../hooks/useScrollDirection';
import '../styles/Header.css';

const Header = ({ onSearch }) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const isNavVisible = useScrollDirection(100); // 100px 이상 스크롤 시 작동

  const handleSearch = (e) => {
    e.preventDefault();
    const keyword = searchQuery.trim();
    onSearch?.(keyword);
    // 모바일에서는 검색 후 패널 닫기
    if (window.innerWidth <= 768) {
      setIsSearchOpen(false);
    }
  };

  return (
    <header className={`header ${isNavVisible ? '' : 'nav-collapsed'}`}>
      <div className="header-container">
        <div className="container">
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

          {/* 모바일 검색 버튼 */}
          <button 
            className="mobile-search-toggle"
            onClick={() => setIsSearchOpen(!isSearchOpen)}
            aria-label="검색 열기"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24">
              <g fill="none" fillRule="evenodd" stroke="#222" strokeWidth="2">
                <circle cx="11.111" cy="11.111" r="7.111" strokeLinecap="round" strokeLinejoin="round" />
                <path d="m20 20-3.867-3.867" />
              </g>
            </svg>
          </button>
        </div>

        {/* 네비게이션 */}
        <nav className={`nav ${isNavVisible ? 'nav-visible' : 'nav-hidden'}`} aria-label="Main navigation">
           
          <ul className="nav-left">
            <li><a href="/positions?sort=popular">개발자 채용</a></li>
            <li><a href="/resumes">이력서</a></li>
            <li><a href="/feed">#꿀 피드</a></li>
            <li><a href="/job-interview">개발자 인터뷰</a></li>
          </ul>

          <ul className="nav-right">
            <li><a href="/login">회원가입/로그인</a></li>
            <li><a href="/business">기업 서비스</a></li>
          </ul>
        </nav>
      </div>
    </header>
  );
};

export default Header;
