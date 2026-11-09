export async function openHwpWorker(bytes, signal) {
  const worker = new Worker(new URL('../workers/hwpPreviewWorker.js', import.meta.url), { type: 'module' });
  const pending = new Map();
  let nextId = 0;
  let closed = false;
  const close = () => {
    if (closed) return;
    closed = true;
    signal?.removeEventListener('abort', close);
    worker.terminate();
    for (const item of pending.values()) { clearTimeout(item.timer); item.reject(new Error('preview closed')); }
    pending.clear();
  };
  if (signal?.aborted) { close(); throw new Error('preview closed'); }
  signal?.addEventListener('abort', close, { once: true });
  worker.onmessage = ({ data }) => {
    const item = pending.get(data.id);
    if (!item) return;
    clearTimeout(item.timer);
    pending.delete(data.id);
    if (data.error) item.reject(new Error('preview failed'));
    else item.resolve(data.result);
  };
  worker.onerror = close;
  worker.onmessageerror = close;
  const request = (type, payload = {}, transfers = []) => new Promise((resolve, reject) => {
    if (closed) { reject(new Error('preview closed')); return; }
    const id = ++nextId;
    const timer = setTimeout(() => { close(); }, 30000);
    pending.set(id, { resolve, reject, timer });
    worker.postMessage({ id, type, ...payload }, transfers);
  });
  try {
    const buffer = bytes.byteOffset === 0 && bytes.byteLength === bytes.buffer.byteLength ? bytes.buffer : bytes.slice().buffer;
    const count = await request('open', { bytes: buffer }, [buffer]);
    return { count, renderPage: page => request('page', { page }), close };
  } catch (error) { close(); throw error; }
}
