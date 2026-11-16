import { useEffect, useState, useSyncExternalStore } from 'react';
import { useLocation } from 'react-router-dom';
import { analyticsConsentSnapshot, setAnalyticsConsent, subscribeAnalyticsConsent, trackGrowthPage } from '../../utils/growthAnalytics';
import { updateGoogleAnalytics } from '../../utils/googleAnalytics';
import { updateClarityAnalytics } from '../../utils/clarityAnalytics';
import '../../styles/GrowthAnalytics.css';

export default function GrowthAnalytics() {
  const location = useLocation();
  const consentSnapshot = useSyncExternalStore(subscribeAnalyticsConsent, analyticsConsentSnapshot, () => ':server');
  const consent = consentSnapshot.split(':')[0];
  const [openOverride, setOpen] = useState(null);
  const open = openOverride ?? (consentSnapshot !== ':server' && !consent);
  useEffect(() => {
    const showSettings = () => setOpen(true);
    window.addEventListener('open-analytics-settings', showSettings);
    return () => window.removeEventListener('open-analytics-settings', showSettings);
  }, []);
  useEffect(() => {
    trackGrowthPage();
    updateGoogleAnalytics();
    updateClarityAnalytics();
  }, [location.pathname, location.search, consentSnapshot]);
  const choose = (value) => { setAnalyticsConsent(value); setOpen(false); };
  if (!open) return null;
  return <aside className="growth-consent growth-consent--prompt" aria-label="이용 통계 설정">
    <div className="growth-consent-panel">
      <div className="growth-consent-copy">
        <strong>선택적 이용 통계 및 쿠키</strong>
        <p>서비스 개선을 위해 동의 시 자체 통계, Google Analytics와 Microsoft Clarity를 사용합니다. 동의하지 않아도 서비스를 이용할 수 있습니다. 기본 페이지 조회 건수는 쿠키나 지속 식별자 없이 집계합니다. <a href="/privacy">자세히 보기</a></p>
      </div>
      <div className="growth-consent-actions"><button type="button" onClick={() => choose('granted')}>통계 수집 허용</button><button type="button" onClick={() => choose('denied')}>거부 / 철회</button></div>
      <button type="button" className="growth-consent-close" aria-label="이용 통계 안내 닫기" onClick={() => setOpen(false)}>×</button>
    </div>
  </aside>;
}
