import { useLayoutEffect, useRef } from 'react';

// Filters remain native toggle buttons, not ARIA tabs: they change a query.
export default function MotionFilter({ options, value, onChange }) {
  const root = useRef(null);
  const selectionState = option => {
    if (!Array.isArray(value)) return value === option.value;
    if (!option.value) return value.length === 0;
    const roles = option.values ?? [option.value];
    const selected = roles.filter(role => value.includes(role)).length;
    return selected === roles.length ? true : selected > 0 ? 'mixed' : false;
  };
  useLayoutEffect(() => {
    const element = root.current;
    const update = () => {
      if (Array.isArray(value)) { element.dataset.ready = 'false'; return; }
      const active = element.querySelector('[aria-pressed="true"]');
      if (!active || !active.offsetWidth) { element.dataset.ready = 'false'; return; }
      element.style.setProperty('--im-tab-x', `${active.offsetLeft}px`);
      element.style.setProperty('--im-tab-y', `${active.offsetTop}px`);
      element.style.setProperty('--im-tab-width', `${active.offsetWidth}px`);
      element.style.setProperty('--im-tab-height', `${active.offsetHeight}px`);
      element.dataset.ready = 'true';
    };
    update();
    const observer = new ResizeObserver(update);
    observer.observe(element);
    element.querySelectorAll('button').forEach(button => observer.observe(button));
    return () => observer.disconnect();
  }, [value]);
  return <div ref={root} className="role-filter im-filter" role="group" aria-label="개발 직무">
    <span className="im-filter-indicator" aria-hidden="true" />
    {options.map(option => <button type="button" key={option.value} aria-pressed={selectionState(option)}
      className={selectionState(option) === true ? 'active' : selectionState(option) === 'mixed' ? 'partial' : ''}
      onClick={() => onChange(option.value)}>{option.label}</button>)}
  </div>;
}
