import { useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';

const focusableSelector = 'a[href], button:not([disabled]), input:not([disabled]), '
  + 'select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export default function DetailModal({ title, onClose, closeDisabled = false, children }) {
  const titleId = useId();
  const dialogRef = useRef(null);
  const closeRef = useRef(onClose);
  const disabledRef = useRef(closeDisabled);

  useEffect(() => {
    closeRef.current = onClose;
    disabledRef.current = closeDisabled;
  }, [onClose, closeDisabled]);

  useEffect(() => {
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialogRef.current?.querySelector('button')?.focus();

    const handleKeyDown = (event) => {
      if (event.key === 'Escape' && !disabledRef.current) {
        event.preventDefault();
        closeRef.current();
      }
      if (event.key !== 'Tab') return;
      const focusable = [...(dialogRef.current?.querySelectorAll(focusableSelector) || [])];
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (!focusable.includes(document.activeElement)) {
        event.preventDefault();
        first.focus();
      } else if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus();
    };
  }, []);

  return createPortal(
    <div className="detail-modal-overlay" onMouseDown={(event) => {
      if (event.target === event.currentTarget && !closeDisabled) onClose();
    }}>
      <section className="dashboard-panel user-detail detail-modal" role="dialog"
        aria-modal="true" aria-labelledby={titleId} ref={dialogRef}>
        <div className="panel-header"><h3 id={titleId}>{title}</h3>
          <button type="button" disabled={closeDisabled} onClick={onClose}
            aria-label={`${title} 닫기`}>닫기</button></div>
        {children}
      </section>
    </div>, document.body,
  );
}
