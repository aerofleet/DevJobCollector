import { analyticsConsent, analyticsPage } from './growthAnalytics';

const MEASUREMENT_ID = 'G-CWT9EYZSQF';
let configured = false;
let lastPage;
let consentInitialized = false;
let lastConsent;
const deniedConsent = { analytics_storage: 'denied', ad_storage: 'denied', ad_user_data: 'denied', ad_personalization: 'denied' };

export function updateGoogleAnalytics() {
  let googleConsent = false;
  try { googleConsent = localStorage.getItem('djc-google-analytics-consent') === 'granted'; } catch { /* Restricted storage. */ }
  const enabled = analyticsConsent() === 'granted' && googleConsent && !navigator.globalPrivacyControl && navigator.doNotTrack !== '1';
  window.dataLayer = window.dataLayer || [];
  window.gtag = window.gtag || function () { window.dataLayer.push(arguments); };
  if (!consentInitialized) {
    window.gtag('consent', 'default', { ...deniedConsent });
    consentInitialized = true;
  }
  if (enabled) window[`ga-disable-${MEASUREMENT_ID}`] = false;
  if (lastConsent !== enabled) {
    window.gtag('consent', 'update', { ...deniedConsent, analytics_storage: enabled ? 'granted' : 'denied' });
    lastConsent = enabled;
  }
  if (!enabled) window[`ga-disable-${MEASUREMENT_ID}`] = true;
  if (!enabled) {
    lastPage = undefined;
    // Remove first-party GA cookies at the current host and parent-domain scope.
    const host = location.hostname.split('.');
    const domains = ['', ...host.map((_, index) => host.slice(index).join('.')).filter(domain => domain.includes('.'))];
    for (const cookie of document.cookie.split(';')) {
      const name = cookie.trim().split('=')[0];
      if (!/^_ga(?:_|$)/.test(name)) continue;
      for (const domain of domains) document.cookie = `${name}=; Max-Age=0; path=/${domain ? `; domain=${domain}` : ''}; SameSite=Lax`;
    }
    return;
  }

  const page = analyticsPage();
  const pageLocation = `${location.origin}/${page === 'home' ? '' : page}`;
  if (!configured) {
    window.gtag('js', new Date());
    window.gtag('config', MEASUREMENT_ID, {
      send_page_view: false, page_location: pageLocation, page_referrer: '',
      allow_google_signals: false, allow_ad_personalization_signals: false,
    });
    const script = document.createElement('script');
    script.async = true;
    script.src = `https://www.googletagmanager.com/gtag/js?id=${MEASUREMENT_ID}`;
    document.head.appendChild(script);
    configured = true;
  }
  const key = location.pathname + location.search;
  if (lastPage === key) return;
  lastPage = key;
  window.gtag('event', 'page_view', { send_to: MEASUREMENT_ID, page_location: pageLocation, page_referrer: '', page_title: `DevJobs - ${page}` });
}
