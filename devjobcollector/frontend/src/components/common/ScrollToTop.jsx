import React, { useState, useEffect, useRef } from 'react';
import '../../styles/ScrollToTop.css';

const ScrollToTop = () => {
  const [isVisible, setIsVisible] = useState(false);
  const [scrollProgress, setScrollProgress] = useState(0);
  const [clickCount, setClickCount] = useState(0); // ref 대신 state 사용
  const scrollingRef = useRef(false);

  useEffect(() => {
    const toggleVisibility = () => {
      const scrollY = window.scrollY;
      const windowHeight = window.innerHeight;
      const documentHeight = document.documentElement.scrollHeight;

      // 350px 이상 스크롤 시 버튼 표시
      setIsVisible(scrollY > 350);

      // 스크롤 진행률 계산 (0-100%)
      const progress = (scrollY / (documentHeight - windowHeight)) * 100;
      setScrollProgress(progress);

      // 스크롤이 맨 위면 클릭 카운트 초기화
      if (scrollY === 0) {
        setClickCount(0); // state 업데이트
      }
    };

    window.addEventListener('scroll', toggleVisibility);
    return () => window.removeEventListener('scroll', toggleVisibility);
  }, []);

  // 부드러운 스크롤 애니메이션
  const smoothScrollTo = (targetY, duration = 300) => {
    const startY = window.scrollY;
    const distance = targetY - startY;
    const startTime = performance.now();

    scrollingRef.current = true;

    const easeInOutCubic = (t) => {
      return t < 0.5
        ? 4 * t * t * t
        : 1 - Math.pow(-2 * t + 2, 3) / 2;
    };

    const scroll = (currentTime) => {
      const elapsed = currentTime - startTime;
      const progress = Math.min(elapsed / duration, 1);
      const ease = easeInOutCubic(progress);

      window.scrollTo(0, startY + distance * ease);

      if (progress < 1) {
        requestAnimationFrame(scroll);
      } else {
        scrollingRef.current = false;
      }
    };

    requestAnimationFrame(scroll);
  };

  const handleClick = () => {
    if (scrollingRef.current) return;

    const currentY = window.scrollY;

    // ✅ clickCount state 사용
    const nextClickCount = clickCount + 1;
    setClickCount(nextClickCount);

    let targetY;
    let duration;

    // 스크롤 위치에 따라 단계적 이동
    if (nextClickCount === 1) {
      // 첫 번째 클릭: 현재 위치의 절반으로
      targetY = currentY / 2;
      duration = 300;
    } else if (nextClickCount === 2) {
      // 두 번째 클릭: 1/4 지점으로
      targetY = currentY / 4;
      duration = 300;
    } else {
      // 세 번째 클릭: 최상단으로
      targetY = 0;
      duration = 300;
      setClickCount(0);
    }

    smoothScrollTo(targetY, duration);
  };

  return (
    <>
      {isVisible && (
        <div 
          id="scroll-to-top" 
          className="scroll-to-top"
          onClick={handleClick}
          aria-label="위로 이동"
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              handleClick();
            }
          }}
        >
          {/* 진행률 표시 원 */}
          <svg className="progress-ring" width="45" height="45">
            <circle
              className="progress-ring-bg"
              cx="22.5"
              cy="22.5"
              r="20"
            />
            <circle
              className="progress-ring-progress"
              cx="22.5"
              cy="22.5"
              r="20"
              style={{
                strokeDasharray: `${2 * Math.PI * 20}`,
                strokeDashoffset: `${2 * Math.PI * 20 * (1 - scrollProgress / 100)}`
              }}
            />
          </svg>

          {/* state로 클릭 단계 표시 */}
          <div className="click-indicator">
            {clickCount === 0 && (
              <i className="fa fa-angle-double-up up-arrow" aria-hidden="true"></i>
            )}
            {clickCount === 1 && (
              <i className="fa fa-angle-up up-arrow" aria-hidden="true"></i>
            )}
            {clickCount >= 2 && (
              <i className="fas fa-arrow-up up-arrow pulse" aria-hidden="true"></i>
            )} 
          </div>
         
        </div>
      )}
    </>
  );
};

export default ScrollToTop;