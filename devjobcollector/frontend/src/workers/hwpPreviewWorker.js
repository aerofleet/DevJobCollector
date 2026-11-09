import init, { HwpDocument } from '@rhwp/core';
import wasmUrl from '@rhwp/core/rhwp_bg.wasm?url';

let doc;
self.onmessage = async ({ data }) => {
  try {
    if (data.type === 'open') {
      if (typeof OffscreenCanvas !== 'function') throw new Error('unsupported browser');
      const context = new OffscreenCanvas(1, 1).getContext('2d');
      if (!context) throw new Error('canvas unavailable');
      self.measureTextWidth = (font, text) => { context.font = font; return context.measureText(text).width; };
      await init({ module_or_path: wasmUrl });
      doc = new HwpDocument(new Uint8Array(data.bytes));
      self.postMessage({ id: data.id, result: doc.pageCount() });
    } else if (data.type === 'page' && doc) {
      self.postMessage({ id: data.id, result: doc.renderPageSvg(data.page) });
    }
  } catch { self.postMessage({ id: data.id, error: true }); }
};
