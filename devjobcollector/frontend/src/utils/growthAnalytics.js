const API = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';
const CONSENT = 'djc-analytics-consent';
const VISITOR = 'djc-analytics-visitor';
const SESSION = 'djc-analytics-session';
const allowed = new Set(['PAGE_VIEW', 'SEARCH', 'JOB_VIEW', 'BOOKMARK', 'APPLY_CLICK', 'SIGNUP', 'LOGIN', 'SHARE']);
let lastPage;

export function analyticsConsent() {
  try { return localStorage.getItem(CONSENT); } catch { return 'denied'; }
}

export function setAnalyticsConsent(value) {
  try {
    localStorage.setItem(CONSENT, value === 'granted' ? 'granted' : 'denied');
    if (value !== 'granted') { localStorage.removeItem(VISITOR); sessionStorage.removeItem(SESSION); }
    lastPage = undefined;
    window.dispatchEvent(new Event('analytics-consent-change'));
  } catch { /* Storage restrictions must not block job browsing. */ }
}

const label = (value, fallback = '') => /^[a-z0-9_-]{1,48}$/.test(value || '') ? value : fallback;
export const analyticsPage = () => {
  const segment = location.pathname.split('/')[1] || 'home';
  return /^(home|jobs|job|login|signup|member|my-devjobs|resumes|resume|company|terms|privacy)$/.test(segment) ? segment : 'other';
};

function identity() {
  const now = Date.now();
  let visitor;
  let session;
  try { visitor = JSON.parse(localStorage.getItem(VISITOR)); } catch { /* New visitor. */ }
  try { session = JSON.parse(sessionStorage.getItem(SESSION)); } catch { /* New session. */ }
  if (!visitor?.id || !Number.isFinite(visitor.created) || now - visitor.created >= 90 * 86400000) {
    visitor = { id: crypto.randomUUID(), created: now };
    localStorage.setItem(VISITOR, JSON.stringify(visitor));
    session = null;
  }
  if (!session?.id || !Number.isFinite(session.last) || now - session.last >= 30 * 60000) {
    const query = new URLSearchParams(location.search);
    let referral = 'direct';
    try {
      const host = new URL(document.referrer).hostname;
      if (host !== location.hostname) referral = ['google', 'naver', 'github', 'velog', 'okky'].find((name) => host.split('.').includes(name)) || 'referral';
    } catch { /* No referrer. */ }
    session = { id: crypto.randomUUID(), source: label(query.get('utm_source'), referral),
      medium: label(query.get('utm_medium'), referral === 'direct' ? 'direct' : 'referral'),
      campaign: label(query.get('utm_campaign')) };
  }
  session.last = now;
  sessionStorage.setItem(SESSION, JSON.stringify(session));
  return { visitorId: visitor.id, sessionId: session.id, source: session.source, medium: session.medium, campaign: session.campaign };
}

export function trackGrowth(kind, jobId) {
  if (analyticsConsent() !== 'granted' || !allowed.has(kind) || navigator.globalPrivacyControl || navigator.doNotTrack === '1') return;
  try {
    const payload = { eventId: crypto.randomUUID(), ...identity(), kind, page: analyticsPage() };
    if (Number.isSafeInteger(Number(jobId)) && Number(jobId) > 0) payload.jobId = Number(jobId);
    const send = (retry) => {
      if (analyticsConsent() !== 'granted') return;
      fetch(`${API}/analytics/events`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
        credentials: 'omit', keepalive: true, body: JSON.stringify(payload) })
        .then((response) => { if (response.status >= 500 && retry) window.setTimeout(() => send(false), 1000); })
        .catch(() => { if (retry) window.setTimeout(() => send(false), 1000); });
    };
    send(true);
  } catch { /* Analytics is best-effort and cannot interrupt user actions. */ }
}

export function trackGrowthPage() {
  if (analyticsConsent() !== 'granted') return;
  const key = location.pathname + location.search;
  // StrictMode repeats effects; a real revisit after another route is still counted.
  if (lastPage === key) return;
  lastPage = key;
  trackGrowth('PAGE_VIEW');
  if (analyticsPage() === 'jobs') trackGrowth('SEARCH');
}
