import banner from '../../assets/withbuddy-banner.png';
import { trackWithBuddyBannerClick } from '../../utils/googleAnalytics';
import '../../styles/WithBuddyBanner.css';

const url = new URL('https://withbuddy.ai.kr/');
url.searchParams.set('utm_source', 'devjobs');
url.searchParams.set('utm_medium', 'banner');
url.searchParams.set('utm_campaign', 'company_onboarding');

export default function WithBuddyBanner() {
  return (
    <a className="withbuddy-banner" href={url.href} target="_blank" rel="noopener noreferrer" onClick={trackWithBuddyBannerClick}>
      <img src={banner} alt="신입사원 온보딩을 돕는 사내 문서 기반 AI 비서 WithBuddy 알아보기" />
      <span className="withbuddy-banner-mobile" aria-hidden="true">
        <strong>WithBuddy · 기업용 AI 온보딩</strong>
        <span>개발자 채용 후, 온보딩도 준비하고 계신가요?</span>
        <small>사내 문서 기반 AI 비서 알아보기 →</small>
      </span>
    </a>
  );
}
