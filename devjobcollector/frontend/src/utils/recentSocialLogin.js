export const SOCIAL_LOGIN_PROVIDERS = ['google', 'kakao', 'github'];

const RECENT_PROVIDER_KEY = 'recentSocialLoginProvider';
const PENDING_PROVIDER_KEY = 'pendingSocialLoginProvider';
const PENDING_PROVIDER_TTL_MS = 10 * 60 * 1000;

const isSupportedProvider = (provider) => SOCIAL_LOGIN_PROVIDERS.includes(provider);

export const readRecentSocialLoginProvider = () => {
  const provider = localStorage.getItem(RECENT_PROVIDER_KEY);
  return isSupportedProvider(provider) ? provider : '';
};

export const rememberPendingSocialLoginProvider = (provider) => {
  if (isSupportedProvider(provider)) {
    sessionStorage.setItem(PENDING_PROVIDER_KEY, JSON.stringify({
      provider,
      createdAt: Date.now(),
    }));
  }
};

export const clearPendingSocialLoginProvider = () => {
  sessionStorage.removeItem(PENDING_PROVIDER_KEY);
};

export const commitSuccessfulSocialLogin = (callbackProvider = '') => {
  let pendingProvider = '';
  try {
    const pending = JSON.parse(sessionStorage.getItem(PENDING_PROVIDER_KEY));
    const isFresh = Number.isFinite(pending?.createdAt)
      && Date.now() - pending.createdAt <= PENDING_PROVIDER_TTL_MS;
    if (isFresh && isSupportedProvider(pending.provider)) {
      pendingProvider = pending.provider;
    }
  } catch {
    // Invalid or legacy pending state is discarded below.
  }
  const provider = isSupportedProvider(callbackProvider) ? callbackProvider : pendingProvider;

  clearPendingSocialLoginProvider();
  if (!isSupportedProvider(provider)) return '';

  localStorage.setItem(RECENT_PROVIDER_KEY, provider);
  return provider;
};
