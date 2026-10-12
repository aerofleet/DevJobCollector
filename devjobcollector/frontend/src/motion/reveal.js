// Shared with ITSDEV Portal. Content stays visible if enhancement cannot run.
export function observeReveals() {
  if (!('IntersectionObserver' in window) || !Element.prototype.animate) return () => {};
  const selector = '[data-motion-reveal]';
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const seen = new WeakSet();
  const checked = new WeakSet();
  const waiting = new Set();
  const running = new Map();
  let ready = document.readyState === 'complete';
  const enabled = () => !reduced.matches && document.documentElement.dataset.motion !== 'off';
  const finish = (element) => {
    seen.add(element);
    observer.unobserve(element);
    waiting.delete(element);
    running.get(element)?.cancel();
    running.delete(element);
    element.dataset.revealState = 'done';
  };
  const observer = new IntersectionObserver(entries => {
    let order = 0;
    for (const { target, isIntersecting } of entries) {
      const initiallyVisible = !checked.has(target);
      checked.add(target);
      if (!isIntersecting) continue;
      observer.unobserve(target);
      waiting.delete(target);
      if (seen.has(target)) continue;
      seen.add(target);
      if (!enabled() || target.contains(document.activeElement)) { finish(target); continue; }
      const distance = document.documentElement.dataset.motion === 'subtle' ? 20 : 28;
      // First-screen and newly fetched visible content must never wait for a fade.
      const step = target.dataset.motionStep;
      const staged = initiallyVisible && step !== undefined;
      const opaque = target.dataset.motionReveal === 'lead' || (initiallyVisible && !staged);
      const animation = target.animate([
        { opacity: opaque ? 1 : 0.35, translate: `0 ${distance}px` },
        { opacity: 1, translate: '0 0' },
      ], { duration: 1400, delay: staged ? Number(step) * 500 : Math.min(order++, 3) * 180, easing: 'cubic-bezier(.4,0,.2,1)', fill: 'backwards' });
      target.dataset.revealState = 'running';
      running.set(target, animation);
      animation.onfinish = () => finish(target);
    }
  }, { threshold: 0.08 });
  const register = element => {
    if (seen.has(element)) return;
    if (!enabled()) { finish(element); return; }
    if (!waiting.has(element)) { waiting.add(element); if (ready) observer.observe(element); }
  };
  const scan = node => {
    if (!(node instanceof Element)) return;
    if (node.matches(selector)) register(node);
    node.querySelectorAll(selector).forEach(register);
  };
  const revealAll = () => { for (const element of [...waiting, ...running.keys()]) finish(element); };
  const syncMode = () => { if (!enabled()) revealAll(); };
  const loaded = () => {
    ready = true;
    for (const element of waiting) observer.observe(element);
  };
  if (!ready) window.addEventListener('load', loaded, { once: true });
  const mutations = new MutationObserver(records => {
    let removed = false;
    for (const record of records) {
      if (record.type === 'attributes') syncMode();
      else {
        record.addedNodes.forEach(scan);
        removed ||= record.removedNodes.length > 0;
      }
    }
    if (removed) for (const element of [...waiting, ...running.keys()]) if (!element.isConnected) finish(element);
  });
  mutations.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['data-motion'] });
  const focus = event => {
    // A pointer click must not move its target between pointerdown and pointerup.
    // Keyboard focus still reveals immediately, without waiting for a sequence.
    if (!event.target.matches(':focus-visible')) return;
    for (let element = event.target; element instanceof Element; element = element.parentElement) {
      if (element.matches(selector)) finish(element);
    }
  };
  document.addEventListener('focusin', focus);
  window.addEventListener('beforeprint', revealAll);
  reduced.addEventListener('change', syncMode);
  scan(document.documentElement);
  return () => {
    mutations.disconnect();
    observer.disconnect();
    for (const animation of running.values()) animation.cancel();
    document.removeEventListener('focusin', focus);
    window.removeEventListener('beforeprint', revealAll);
    reduced.removeEventListener('change', syncMode);
    window.removeEventListener('load', loaded);
  };
}
