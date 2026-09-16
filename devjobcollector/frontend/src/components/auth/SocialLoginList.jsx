import '../../styles/SocialLoginList.css';

const PROVIDERS = [
  { id: 'google', label: 'Google' },
  { id: 'kakao', label: '카카오' },
  { id: 'naver', label: '네이버' },
  { id: 'github', label: 'GitHub' },
];

const SocialLoginList = ({ authServerBaseUrl, onProviderClick }) => (
  <div className="social_login_list" aria-label="소셜 로그인">
    {PROVIDERS.map(({ id, label }) => (
      <a
        key={id}
        className={`social_icon ${id}`}
        title={id}
        aria-label={`${label}로 계속`}
        href={`${authServerBaseUrl}/oauth2/authorization/${id}`}
        onClick={() => onProviderClick?.(id)}
      />
    ))}
  </div>
);

export default SocialLoginList;
