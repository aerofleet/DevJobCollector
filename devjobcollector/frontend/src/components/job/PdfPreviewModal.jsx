import React, { useEffect, useRef, useState } from 'react';
import { previewJobFile, recordPreviewEvent } from '../../api/jobApi';
import PreviewFrame from './PreviewFrame';

function PdfPage({ document: pdf, number, zoom, onRendered, onError }) {
  const element = useRef(null);
  const canvas = useRef(null);
  useEffect(() => {
    let active = true, renderTask, generation = 0;
    const target = canvas.current;
    const observer = new IntersectionObserver(entries => {
      const current = ++generation;
      if (!entries[0].isIntersecting) {
        renderTask?.cancel();
        if (target) { target.width = 0; target.height = 0; }
        return;
      }
      pdf.getPage(number).then(value => {
        if (!active || current !== generation) return;
        const page = value;
        const base = page.getViewport({ scale: 1 });
        const scale = Math.min((800 * zoom) / base.width, Math.sqrt(4000000 / (base.width * base.height)));
        const viewport = page.getViewport({ scale });
        if (!target) return;
        target.width = Math.ceil(viewport.width); target.height = Math.ceil(viewport.height);
        renderTask = page.render({ canvas: target, viewport });
        return renderTask.promise.then(() => { if (active && current === generation && number === 1) onRendered(); });
      }).catch(error => { if (active && current === generation && error.name !== 'RenderingCancelledException') onError(); });
    }, { rootMargin: '100% 0px' });
    observer.observe(element.current);
    return () => { active = false; generation++; observer.disconnect(); renderTask?.cancel(); if (target) { target.width = 0; target.height = 0; } };
  }, [pdf, number, zoom, onRendered, onError]);
  return <div ref={element} className="attachment-pdf-page" style={{ width: `${zoom * 100}%`, maxWidth: `${800 * zoom}px` }} aria-label={`${number}쪽`}><canvas ref={canvas} /></div>;
}

export default function PdfPreviewModal({ file, originalUrl, onClose }) {
  const [state, setState] = useState({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const [zoom, setZoom] = useState(1);
  const metrics = useRef({ success: false, error: false });
  const rendered = React.useCallback(() => { if (!metrics.current.success) { metrics.current.success = true; recordPreviewEvent('render_success'); } }, []);
  const failed = React.useCallback(() => { if (!metrics.current.error) { metrics.current.error = true; recordPreviewEvent('parse_error'); } setState({ status: 'error' }); }, []);
  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false, loadingTask;
    (async () => {
      try {
        const [bytes, pdfjs, worker] = await Promise.all([
          previewJobFile(file.id, controller.signal), import('pdfjs-dist'), import('pdfjs-dist/build/pdf.worker.min.mjs?url'),
        ]);
        if (bytes.length > 30 * 1024 * 1024) throw new Error('file too large');
        if (cancelled) return;
        pdfjs.GlobalWorkerOptions.workerSrc = worker.default;
        loadingTask = pdfjs.getDocument({ data: bytes, isEvalSupported: false, enableXfa: false });
        const pdf = await loadingTask.promise;
        if (!pdf.numPages || pdf.numPages > 1000) throw new Error('invalid page count');
        if (!cancelled) setState({ status: 'ready', pdf });
      } catch (error) {
        if (!cancelled) { if (error.response?.status === 429) setState({ status: 'busy' }); else failed(); }
      }
    })();
    return () => { cancelled = true; controller.abort(); loadingTask?.destroy(); };
  }, [file.id, attempt, failed]);
  const controls = state.status === 'ready' && <>
    <button type="button" onClick={() => setZoom(value => Math.max(0.5, value - 0.25))}>축소</button>
    <span>{Math.round(zoom * 100)}%</span>
    <button type="button" onClick={() => setZoom(value => Math.min(3, value + 0.25))}>확대</button>
    <button type="button" onClick={() => setZoom(1)}>화면 맞춤</button>
  </>;
  return <PreviewFrame onClose={onClose} originalUrl={originalUrl} controls={controls}>
    {state.status === 'loading' && <p role="status">PDF를 불러오고 있습니다.</p>}
    {state.status === 'busy' && <div role="alert"><p>미리보기 요청이 많습니다. 잠시 후 다시 시도해 주세요.</p><button type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1); }}>다시 시도</button></div>}
    {state.status === 'error' && <p role="alert">PDF를 표시할 수 없습니다. 원본 파일을 확인해 주세요.</p>}
    {state.status === 'ready' && Array.from({ length: state.pdf.numPages }, (_, index) => <PdfPage key={index} document={state.pdf} number={index + 1} zoom={zoom} onRendered={rendered} onError={failed} />)}
  </PreviewFrame>;
}
