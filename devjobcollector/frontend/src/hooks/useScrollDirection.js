// hooks/useScrollDirection.js
import { useState, useEffect } from 'react';

export const useScrollDirection = (threshold = 100) => {
  const [isVisible, setIsVisible] = useState(true);
  const [lastScrollY, setLastScrollY] = useState(0);

  useEffect(() => {
    let ticking = false;

    const handleScroll = () => {
      if (!ticking) {
        window.requestAnimationFrame(() => {
          const currentScrollY = window.scrollY;

          if (currentScrollY > lastScrollY && currentScrollY > threshold) {
            // 스크롤 다운
            setIsVisible(false);
          } else if (currentScrollY < lastScrollY) {
            // 스크롤 업
            setIsVisible(true);
          }

          setLastScrollY(currentScrollY);
          ticking = false;
        });

        ticking = true;
      }
    };

    window.addEventListener('scroll', handleScroll, { passive: true });

    return () => {
      window.removeEventListener('scroll', handleScroll);
    };
  }, [lastScrollY, threshold]);

  return isVisible;
};

export default useScrollDirection;