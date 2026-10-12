// Shared with ITSDEV Portal. Content stays visible if enhancement cannot run.
export function observeReveals() {
  if (!('IntersectionObserver' in window) || !Element.prototype.animate) return () => {};
  const selector = '[data-motion-reveal]';
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const seen = new WeakSet();
  const waiting = new Set();
  const running = new Map();
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
      if (!isIntersecting) continue;
      observer.unobserve(target);
      waiting.delete(target);
      if (seen.has(target)) continue;
      seen.add(target);
      if (!enabled() || target.contains(document.activeElement)) { finish(target); continue; }
      const distance = document.documentElement.dataset.motion === 'subtle' ? 12 : 20;
      // Keep the headline painted from the first frame; never hide the LCP text.
      const lead = target.dataset.motionReveal === 'lead';
      const animation = target.animate([
        { opacity: lead ? 1 : 0, translate: `0 ${distance}px` },
        { opacity: 1, translate: '0 0' },
      ], { duration: 450, delay: Math.min(order++, 3) * 70, easing: 'cubic-bezier(.2,.8,.2,1)', fill: 'backwards' });
      target.dataset.revealState = 'running';
      running.set(target, animation);
      animation.onfinish = () => finish(target);
    }
  }, { threshold: 0.08 });
  const scan = () => {
    for (const element of waiting) if (!element.isConnected) { observer.unobserve(element); waiting.delete(element); }
    for (const element of running.keys()) if (!element.isConnected || !enabled()) finish(element);
    document.querySelectorAll(selector).forEach(element => {
      if (seen.has(element)) return;
      if (!enabled()) { finish(element); return; }
      if (!waiting.has(element)) { waiting.add(element); observer.observe(element); }
    });
  };
  const mutations = new MutationObserver(scan);
  mutations.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['data-motion'] });
  const focus = event => {
    for (let element = event.target; element instanceof Element; element = element.parentElement) {
      if (element.matches(selector)) finish(element);
    }
  };
  const revealAll = () => { for (const element of [...waiting, ...running.keys()]) finish(element); };
  document.addEventListener('focusin', focus);
  window.addEventListener('beforeprint', revealAll);
  reduced.addEventListener('change', scan);
  scan();
  return () => {
    mutations.disconnect();
    observer.disconnect();
    for (const animation of running.values()) animation.cancel();
    document.removeEventListener('focusin', focus);
    window.removeEventListener('beforeprint', revealAll);
    reduced.removeEventListener('change', scan);
  };
}
