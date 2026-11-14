import React, { useEffect, useState } from 'react';
import { previewJobFile, recordPreviewEvent } from '../../api/jobApi';
import PreviewFrame from './PreviewFrame';

export default function ImagePreviewModal({ file, originalUrl, onClose }) {
  const [state, setState] = useState({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const [zoom, setZoom] = useState(1);
  const [original, setOriginal] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false, objectUrl;
    (async () => {
      try {
        const bytes = await previewJobFile(file.id, controller.signal);
        if (cancelled) return;
        if (bytes.length > 30 * 1024 * 1024) throw new Error('file too large');
        const ext = file.fileName.split('.').pop().toLowerCase();
        const mime = ext === 'jpg' || ext === 'jpeg' ? 'image/jpeg' : `image/${ext}`;
        objectUrl = URL.createObjectURL(new Blob([bytes], { type: mime }));
        if (!cancelled) setState({ status: 'ready', url: objectUrl });
      } catch (error) {
        if (!cancelled) setState({ status: error.response?.status === 429 ? 'busy' : 'error' });
      }
    })();
    return () => { cancelled = true; controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [file.id, file.fileName, attempt]);
  const controls = state.status === 'ready' && <>
    <button type="button" onClick={() => { setOriginal(false); setZoom(value => Math.max(0.5, value - 0.25)); }}>축소</button>
    <span>{original ? '원본 크기' : `${Math.round(zoom * 100)}%`}</span>
    <button type="button" onClick={() => { setOriginal(false); setZoom(value => Math.min(3, value + 0.25)); }}>확대</button>
    <button type="button" onClick={() => { setOriginal(false); setZoom(1); }}>화면 맞춤</button>
    <button type="button" onClick={() => setOriginal(true)}>원본 크기</button>
  </>;
  return <PreviewFrame onClose={onClose} originalUrl={originalUrl} controls={controls}>
    {state.status === 'loading' && <p role="status">이미지를 불러오고 있습니다.</p>}
    {state.status === 'busy' && <div role="alert"><p>미리보기 요청이 많습니다. 잠시 후 다시 시도해 주세요.</p><button type="button" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1); }}>다시 시도</button></div>}
    {state.status === 'error' && <p role="alert">이미지를 표시할 수 없습니다. 원본 파일을 확인해 주세요.</p>}
    {state.status === 'ready' && <img className="attachment-image" src={state.url} alt={file.fileName || '첨부 이미지'} style={original ? { maxWidth: 'none' } : { width: `${zoom * 100}%`, maxWidth: 'none' }} onLoad={() => recordPreviewEvent('render_success')} onError={() => { recordPreviewEvent('parse_error'); setState({ status: 'error' }); }} />}
  </PreviewFrame>;
}
