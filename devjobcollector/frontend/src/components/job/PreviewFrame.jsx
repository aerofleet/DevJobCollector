import React, { useEffect, useRef } from 'react';
import { recordPreviewEvent } from '../../api/jobApi';
import '../../styles/HwpPreviewModal.css';
import '../../styles/AttachmentPreview.css';

export default function PreviewFrame({ onClose, originalUrl, children, controls }) {
  const close = useRef(null);
  const dialog = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    close.current?.focus();
    return () => previous?.focus();
  }, []);
  useEffect(() => {
    const key = event => {
      if (event.key === 'Escape') onClose();
      if (event.key !== 'Tab') return;
      const items = [...dialog.current.querySelectorAll('button:not(:disabled), a[href]')];
      if (!items.length) return;
      if (event.shiftKey && document.activeElement === items[0]) { event.preventDefault(); items.at(-1).focus(); }
      else if (!event.shiftKey && document.activeElement === items.at(-1)) { event.preventDefault(); items[0].focus(); }
    };
    window.addEventListener('keydown', key);
    return () => window.removeEventListener('keydown', key);
  }, [onClose]);
  return <div className="hwp-overlay" onClick={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section ref={dialog} className="hwp-dialog" role="dialog" aria-modal="true" aria-labelledby="attachment-preview-title">
      <header><h2 id="attachment-preview-title">첨부문서 미리보기</h2><button ref={close} type="button" onClick={onClose} aria-label="미리보기 닫기">✕</button></header>
      {controls && <div className="attachment-controls">{controls}</div>}
      <div className="hwp-scroll">{children}</div>
      <footer><span>내용이 표시되지 않으면 원본을 확인해 주세요.</span>{originalUrl && <a href={originalUrl} target="_blank" rel="noopener noreferrer" onClick={() => recordPreviewEvent('fallback')}>원본 파일 열기 / 다운로드</a>}</footer>
    </section>
  </div>;
}
