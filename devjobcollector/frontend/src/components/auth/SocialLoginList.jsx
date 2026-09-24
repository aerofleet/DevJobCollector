import '../../styles/SocialLoginList.css';
import { rememberPendingSocialLoginProvider } from '../../utils/recentSocialLogin';

const PROVIDERS = [
  { id: 'google', label: 'Google' },
  { id: 'kakao', label: '카카오' },
  { id: 'github', label: 'GitHub' },
];

const SocialLoginList = ({ authServerBaseUrl, onProviderClick, recentProvider = '' }) => (
  <div
    className={`social_login_list${recentProvider ? ' has_recent_provider' : ''}`}
    aria-label="소셜 로그인"
  >
    {PROVIDERS.map(({ id, label }) => (
      <span className="social_login_item" key={id}>
        {recentProvider === id && (
          <span className="recent_social_badge" aria-hidden="true">최근 사용</span>
        )}
        <a
          className={`social_icon ${id}`}
          title={id}
          aria-label={`${label}로 계속${recentProvider === id ? ', 최근 사용' : ''}`}
          href={`${authServerBaseUrl}/oauth2/authorization/${id}`}
          onClick={() => {
            rememberPendingSocialLoginProvider(id);
            onProviderClick?.(id);
          }}
        />
      </span>
    ))}
  </div>
);

export default SocialLoginList;
