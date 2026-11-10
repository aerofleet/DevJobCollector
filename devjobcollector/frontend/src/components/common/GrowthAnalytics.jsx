import { useEffect, useState, useSyncExternalStore } from 'react';
import { useLocation } from 'react-router-dom';
import { analyticsConsentSnapshot, setAnalyticsConsent, subscribeAnalyticsConsent, trackGrowthPage } from '../../utils/growthAnalytics';
import { updateGoogleAnalytics } from '../../utils/googleAnalytics';
import { updateClarityAnalytics } from '../../utils/clarityAnalytics';
import '../../styles/GrowthAnalytics.css';

export default function GrowthAnalytics() {
  const location = useLocation();
  const consentSnapshot = useSyncExternalStore(subscribeAnalyticsConsent, analyticsConsentSnapshot, () => ':');
  const consent = consentSnapshot.split(':')[0];
  const [open, setOpen] = useState(false);
  useEffect(() => {
    trackGrowthPage();
    updateGoogleAnalytics();
    updateClarityAnalytics();
  }, [location.pathname, location.search, consentSnapshot]);
  const choose = (value) => { setAnalyticsConsent(value); setOpen(false); };
  return <aside className="growth-consent" aria-label="이용 통계 설정">
    <button type="button" onClick={() => setOpen(!open)} aria-expanded={open}>이용 통계 {consent === 'granted' ? '허용 중' : '설정'}</button>
    {open && <div className="growth-consent-panel">
      <strong>서비스 개선을 위한 선택적 이용 통계</strong>
      <p>동의하면 자체 이용 통계, Google Analytics와 Microsoft Clarity를 사용합니다. 자체 통계는 임의의 식별자, 유입 채널, 페이지 종류와 공고 활동을 최대 90일간 수집합니다. 외부 통계에는 쿠키 식별자와 페이지·기기 정보, 화면 이용 정보 등이 전송될 수 있습니다. 동의하지 않아도 모든 기능을 이용할 수 있습니다.</p>
      <p>이전에 통계를 허용한 경우 Clarity 사용을 시작하려면 다시 허용해 주세요.</p>
      <p>Clarity는 주소나 유입 경로에 검색값이 없는 첫 화면에서만 작동하며, 화면 내 주소가 바뀌거나 검색값이 포함된 링크를 선택하면 중단됩니다.</p>
      <p>철회하면 이후 수집을 중단하고 이 기기의 통계 식별자를 삭제합니다. 기존 기록은 보유기간 후 삭제됩니다. 브라우저 추적 거부 설정을 우선 적용합니다.</p>
      <a href="/privacy">개인정보 처리방침</a>
      <div><button type="button" onClick={() => choose('granted')}>통계 수집 허용</button><button type="button" onClick={() => choose('denied')}>거부 / 철회</button></div>
    </div>}
  </aside>;
}
