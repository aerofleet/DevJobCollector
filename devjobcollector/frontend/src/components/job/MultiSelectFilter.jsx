import { useEffect, useRef, useState } from 'react';

export default function MultiSelectFilter({ label, groups, values, onChange }) {
  const root = useRef(null);
  const [query, setQuery] = useState('');
  useEffect(() => {
    // Close after the click, so collapsing an inline mobile menu does not move
    // the next control between pointer-down and pointer-up.
    const closeOutside = event => {
      if (root.current && !root.current.contains(event.target)) root.current.open = false;
    };
    document.addEventListener('click', closeOutside);
    return () => document.removeEventListener('click', closeOutside);
  }, []);
  const options = groups.flatMap(group => group.options);
  const selected = options.filter(option => values.includes(option.value));
  const toggle = value => onChange(values.includes(value)
    ? values.filter(item => item !== value) : [...values, value]);
  return <details className="multi-filter" ref={root}
    onKeyDown={event => {
      if (event.key === 'Escape') {
        root.current.open = false;
        root.current.querySelector('summary').focus();
      }
    }}>
    <summary aria-label={label}>
      <span className="multi-filter-label">{label}</span>
      <span className="multi-filter-value">{values.length ? `${selected[0]?.label ?? values[0]}${values.length > 1 ? ` 외 ${values.length - 1}개` : ''}` : '전체'}</span>
    </summary>
    <div className="multi-filter-menu" role="group" aria-label={`${label} 선택`}>
      <input type="search" aria-label={`${label} 검색`} placeholder="항목 검색" value={query} onChange={event => setQuery(event.target.value)} />
      <button type="button" onClick={() => onChange([])}>선택 해제</button>
      <div className="multi-filter-options">
        {groups.map(group => {
          const visible = group.options.filter(option => option.label.toLowerCase().includes(query.trim().toLowerCase()));
          return visible.length > 0 && <fieldset key={group.label}>
            <legend>{group.label}</legend>
            {visible.map(option => <label key={option.value}>
              <input type="checkbox" checked={values.includes(option.value)} onChange={() => toggle(option.value)} />
              {option.label}
            </label>)}
          </fieldset>;
        })}
        {!options.some(option => option.label.toLowerCase().includes(query.trim().toLowerCase())) && <p>일치하는 항목이 없습니다.</p>}
      </div>
    </div>
  </details>;
}
