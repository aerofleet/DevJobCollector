import { useEffect, useState, useSyncExternalStore } from 'react';
import { useLocation } from 'react-router-dom';
import { analyticsConsent, setAnalyticsConsent, subscribeAnalyticsConsent, trackGrowthPage } from '../../utils/growthAnalytics';
import '../../styles/GrowthAnalytics.css';

export default function GrowthAnalytics() {
  const location = useLocation();
  const consent = useSyncExternalStore(subscribeAnalyticsConsent, analyticsConsent, () => null);
  const [open, setOpen] = useState(false);
  useEffect(() => {
    trackGrowthPage();
  }, [location.pathname, location.search, consent]);
  const choose = (value) => { setAnalyticsConsent(value); setOpen(false); };
  return <aside className="growth-consent" aria-label="이용 통계 설정">
    <button type="button" onClick={() => setOpen(!open)} aria-expanded={open}>이용 통계 {consent === 'granted' ? '허용 중' : '설정'}</button>
    {open && <div className="growth-consent-panel">
      <strong>서비스 개선을 위한 선택적 이용 통계</strong>
      <p>동의하면 임의의 브라우저 식별자, 유입 채널, 페이지 종류, 공고 번호와 검색·저장·지원 클릭 등을 최대 90일간 수집합니다. 이메일·검색어 원문·인증 정보는 보내지 않습니다. 동의하지 않아도 모든 기능을 이용할 수 있습니다.</p>
      <p>철회하면 이후 수집을 중단하고 이 기기의 통계 식별자를 삭제합니다. 기존 기록은 보유기간 후 삭제됩니다. 브라우저 추적 거부 설정을 우선 적용합니다.</p>
      <a href="/privacy">개인정보 처리방침</a>
      <div><button type="button" onClick={() => choose('granted')}>통계 수집 허용</button><button type="button" onClick={() => choose('denied')}>거부 / 철회</button></div>
    </div>}
  </aside>;
}
