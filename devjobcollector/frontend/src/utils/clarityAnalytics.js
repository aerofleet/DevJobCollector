import { analyticsConsent } from './growthAnalytics';

const CLARITY_ID = 'zbh7shexgg';
let started = false;
let retired = false;

// Stop before a client-side URL change, while the old URL is still current.
// Clarity's own history observer is installed after this wrapper.
function protectNavigation() {
  const stop = () => {
    if (retired) return;
    retired = true;
    if (window.clarity?.v) window.clarity('stop');
  };
  for (const method of ['pushState', 'replaceState']) {
    const original = history[method];
    history[method] = function (state, unused, url) {
      if (!retired && url != null && new URL(url, location.href).href !== location.href) {
        if (!window.clarity?.v) {
          retired = true;
          location.assign(new URL(url, location.href).href);
          return;
        }
        stop();
      }
      return original.call(this, state, unused, url);
    };
  }
  document.addEventListener('click', event => {
    const anchor = event.target instanceof Element ? event.target.closest('a[href]') : null;
    if (!anchor || retired) return;
    const target = new URL(anchor.href, location.href);
    if (!target.search && !target.hash) return;
    if (!window.clarity?.v) { retired = true; location.reload(); }
    else stop();
  }, true);
  for (const event of ['popstate', 'hashchange']) window.addEventListener(event, () => {
    if (retired) return;
    if (!window.clarity?.v) { retired = true; location.reload(); }
    else stop();
  }, true);
}

function hasSensitiveUrlContext() {
  if (location.search || location.hash) return true;
  try {
    const referrer = document.referrer && new URL(document.referrer);
    return Boolean(referrer && (referrer.search || referrer.hash));
  } catch { return true; }
}

function clearClarityCookies() {
  const domains = ['', ...location.hostname.split('.').map((_, index, parts) => parts.slice(index).join('.')).filter(domain => domain.includes('.'))];
  for (const cookie of document.cookie.split(';')) {
    const name = cookie.trim().split('=')[0];
    if (!/^_(?:clck|clsk)$/.test(name)) continue;
    for (const domain of domains) document.cookie = `${name}=; Max-Age=0; path=/${domain ? `; domain=${domain}` : ''}; SameSite=Lax`;
  }
}

export function updateClarityAnalytics() {
  let consent = false;
  try { consent = localStorage.getItem('djc-clarity-consent') === 'granted'; } catch { /* Restricted storage. */ }
  const enabled = analyticsConsent() === 'granted' && consent && !navigator.globalPrivacyControl && navigator.doNotTrack !== '1' && !hasSensitiveUrlContext();
  if (!enabled) {
    if (!consent || analyticsConsent() !== 'granted') clearClarityCookies();
    if (started) {
      window.clarity('consent', false);
      // A loaded Clarity script can continue in cookieless mode; reload without it.
      location.reload();
    }
    return;
  }
  if (started || retired) return;
  protectNavigation();
  window.clarity = window.clarity || function (...args) { (window.clarity.q = window.clarity.q || []).push(args); };
  window.clarity('consentv2', { ad_Storage: 'denied', analytics_Storage: 'granted' });
  const script = document.createElement('script');
  script.async = true;
  script.src = `https://www.clarity.ms/tag/${CLARITY_ID}?ref=bwt`;
  document.head.appendChild(script);
  started = true;
}
