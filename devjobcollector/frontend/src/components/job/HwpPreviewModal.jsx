import React, { useEffect, useRef, useState } from 'react';
import { previewJobFile, recordPreviewEvent } from '../../api/jobApi';
import { openHwpWorker } from '../../utils/hwpWorkerClient';
import '../../styles/HwpPreviewModal.css';

const Page = ({ doc, index, onRendered, onRenderError }) => {
  const element = useRef(null);
  const [svg, setSvg] = useState(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let active = true;
    let generation = 0;
    const observer = new IntersectionObserver(entries => {
      if (entries[0].isIntersecting) {
        const current = ++generation;
        doc.renderPage(index).then(result => {
          if (active && current === generation) { setSvg(result); if (index === 0) onRendered(); }
        }).catch(() => { if (active && current === generation) { setFailed(true); onRenderError(); } });
      } else { generation++; setSvg(null); }
    }, { rootMargin: '100% 0px' });
    observer.observe(element.current);
    return () => { active = false; observer.disconnect(); };
  }, [doc, index, onRendered, onRenderError]);
  const viewBox = svg?.match(/\bviewBox\s*=\s*["']\s*[-.\d]+\s+[-.\d]+\s+([.\d]+)\s+([.\d]+)\s*["']/i);
  const ratio = viewBox && Number(viewBox[1]) > 0 && Number(viewBox[2]) > 0 ? `${Number(viewBox[1])} / ${Number(viewBox[2])}` : '210 / 297';
  return <div ref={element} className="hwp-page" aria-label={`${index + 1}쪽`} style={{ aspectRatio: ratio }}>
    {failed ? <p>이 페이지를 표시하지 못했습니다. 원본 파일을 확인해 주세요.</p> : svg &&
      <iframe title={`${index + 1}쪽 미리보기`} tabIndex={-1} sandbox="" style={{ aspectRatio: ratio }} srcDoc={`<!doctype html><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src data: blob:"><style>body{margin:0}svg{width:100%;height:auto}</style>${svg}`} />}
  </div>;
};

export default function HwpPreviewModal({ file, originalUrl, onClose }) {
  const [state, setState] = useState({ status: 'loading' });
  const [doc, setDoc] = useState(null);
  const [attempt, setAttempt] = useState(0);
  const close = useRef(null);
  const dialog = useRef(null);
  const metrics = useRef({ success: false, error: false });
  const onRendered = React.useCallback(() => { if (!metrics.current.success) { metrics.current.success = true; recordPreviewEvent('render_success'); } }, []);
  const onRenderError = React.useCallback(() => { if (!metrics.current.error) { metrics.current.error = true; recordPreviewEvent('parse_error'); } }, []);
  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false, opened;
    const previous = document.activeElement;
    close.current?.focus();
    (async () => {
      let fetched = false;
      try {
        const bytes = await previewJobFile(file.id, controller.signal);
        fetched = true;
        if (bytes.length > 30 * 1024 * 1024) throw new Error('file too large');
        opened = await openHwpWorker(bytes, controller.signal);
        if (cancelled) { opened.close(); return; }
        const count = opened.count;
        if (count < 1 || count > 1000) throw new Error('invalid page count');
        setDoc(opened);
        setState({ status: 'ready', count });
      } catch (error) {
        opened?.close(); opened = undefined;
        if (!cancelled) { if (fetched) onRenderError(); setState({ status: error.response?.status === 429 ? 'busy' : 'error' }); }
      }
    })();
    return () => { cancelled = true; controller.abort(); opened?.close(); previous?.focus(); };
  }, [file.id, onRenderError, attempt]);
  useEffect(() => {
    const handleKey = event => {
      if (event.key === 'Escape') onClose();
      if (event.key !== 'Tab') return;
      const items = [...dialog.current.querySelectorAll('button, a[href]')];
      if (!items.length) return;
      if (event.shiftKey && document.activeElement === items[0]) { event.preventDefault(); items.at(-1).focus(); }
      else if (!event.shiftKey && document.activeElement === items.at(-1)) { event.preventDefault(); items[0].focus(); }
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [onClose]);
  return <div className="hwp-overlay" onClick={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section ref={dialog} className="hwp-dialog" role="dialog" aria-modal="true" aria-labelledby="hwp-title">
      <header><h2 id="hwp-title">첨부문서 미리보기</h2><button ref={close} type="button" onClick={onClose} aria-label="미리보기 닫기">✕</button></header>
      <div className="hwp-scroll">
        {state.status === 'loading' && <p role="status">문서를 불러오고 있습니다.</p>}
        {state.status === 'busy' && <div role="alert"><p>미리보기 요청이 많습니다. 잠시 후 다시 시도하거나 원본 파일을 확인해 주세요.</p><button type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1); }}>다시 시도</button></div>}
        {state.status === 'error' && <p role="alert">이 문서는 브라우저 미리보기를 지원하지 않습니다. 원본 파일을 확인해 주세요.</p>}
        {state.status === 'ready' && Array.from({ length: state.count }, (_, index) => <Page key={index} doc={doc} index={index} onRendered={onRendered} onRenderError={onRenderError} />)}
      </div>
      <footer><span>일부 서식은 원본과 다를 수 있습니다.</span>{originalUrl && <a href={originalUrl} target="_blank" rel="noopener noreferrer" onClick={() => recordPreviewEvent('fallback')}>원본 파일 열기 / 다운로드</a>}</footer>
    </section>
  </div>;
}
