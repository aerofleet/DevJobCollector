const TOKEN_KEY = 'accessToken';
const CHANGE_EVENT = 'access-token-change';

function expiresAt(token) {
  try {
    const parts = token.split('.');
    if (parts.length !== 3 || parts.some((part) => !part)) return 0;
    const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
    return typeof payload.exp === 'number' && Number.isFinite(payload.exp) ? payload.exp * 1000 : 0;
  } catch {
    return 0;
  }
}

// This only controls client UI lifetime. The API still verifies signature and permissions.
export function getAccessToken() {
  const token = localStorage.getItem(TOKEN_KEY);
  return token && expiresAt(token) > Date.now() ? token : null;
}

export function setAccessToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
  window.dispatchEvent(new Event(CHANGE_EVENT));
}

export function clearAccessToken() {
  localStorage.removeItem(TOKEN_KEY);
  window.dispatchEvent(new Event(CHANGE_EVENT));
}

export function subscribeAccessToken(onChange) {
  let timer;
  const sync = () => {
    window.clearTimeout(timer);
    const token = getAccessToken();
    if (!token) localStorage.removeItem(TOKEN_KEY);
    else timer = window.setTimeout(sync, Math.min(expiresAt(token) - Date.now(), 2147483647));
    onChange();
  };
  const onStorage = (event) => {
    if (event.key === TOKEN_KEY || event.key === null) sync();
  };
  window.addEventListener(CHANGE_EVENT, sync);
  window.addEventListener('storage', onStorage);
  window.addEventListener('focus', sync);
  window.addEventListener('pageshow', sync);
  document.addEventListener('visibilitychange', sync);
  sync();
  return () => {
    window.clearTimeout(timer);
    window.removeEventListener(CHANGE_EVENT, sync);
    window.removeEventListener('storage', onStorage);
    window.removeEventListener('focus', sync);
    window.removeEventListener('pageshow', sync);
    document.removeEventListener('visibilitychange', sync);
  };
}
