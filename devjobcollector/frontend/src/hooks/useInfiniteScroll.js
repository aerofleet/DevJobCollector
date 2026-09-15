import { useState, useEffect, useCallback } from "react";

/**
 * 무한 스크롤 커스텀 훅
 * @param {Fuction} fetchData - 데이터를 가져오는 함수
 * @param {number} threshold - 스크롤 트리거 임계값 (픽셀)
 */

const useInfiniteScroll = (fetchData, threshold = 100, enabled = true) => {
    const [loading, setLoading] = useState(false);
    const [hasMore, setHasMore] = useState(true);

    const handleScroll = useCallback(() => {
        if (!enabled || loading || !hasMore) return;

        const scrollTop = window.scrollY;
        const scrollHeight = document.documentElement.scrollHeight;
        const clientHeight = window.innerHeight;

        if (scrollHeight - scrollTop - clientHeight < threshold) {
            setLoading(true);
            fetchData()
                .then((hasMoreData) => {
                    setHasMore(hasMoreData);                
                })
                .finally(() => {
                    setLoading(false);
                });
        }
    }, [enabled, loading, hasMore, fetchData, threshold]);

    useEffect(() => {
        if (!enabled) return undefined;

        window.addEventListener('scroll', handleScroll);
        const frameId = window.requestAnimationFrame(handleScroll);
        return () => {
            window.cancelAnimationFrame(frameId);
            window.removeEventListener('scroll', handleScroll);
        };
    }, [enabled, handleScroll]);

    return { loading, hasMore };
};

export default useInfiniteScroll;