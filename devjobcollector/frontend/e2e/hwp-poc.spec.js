import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import process from 'node:process';

test('renders embedded images in a public HWPX reference', async ({ page }) => {
  test.setTimeout(90000);
  const sample = path.join(os.tmpdir(), 'djc-reference-image.hwpx');
  test.skip(!fs.existsSync(sample), 'Public image reference is not available');
  const bytes = [...fs.readFileSync(sample)];
  await page.goto('/');
  const result = await page.evaluate(async bytes => {
    const { openHwpWorker } = await import('/src/utils/hwpWorkerClient.js');
    const doc = await openHwpWorker(new Uint8Array(bytes));
    try {
      const pages = [];
      for (let i = 0; i < doc.count; i++) pages.push(await doc.renderPage(i));
      return pages;
    } finally { doc.close(); }
  }, bytes);
  const imageCounts = result.map(svg => (svg.match(/<image\b/g) || []).length);
  console.log(`Reference pages: ${result.length}; images per page: ${imageCounts.join(',')}`);
  expect(imageCounts).toEqual([2, 3, 1]);
  fs.writeFileSync(path.join(os.tmpdir(), 'djc-reference-image-first.svg'), result[0]);
  await page.setViewportSize({ width: 1000, height: 1400 });
  await page.setContent(`<style>body{margin:0}svg{width:775px;height:auto}</style>${result[0]}`);
  await page.screenshot({ path: path.join(os.tmpdir(), 'djc-reference-image-render.png') });
});

test('ALIO HWP is rendered by the browser WASM engine', async ({ page }) => {
  const sample = path.join(os.tmpdir(), 'djc-alio-3085282.hwp');
  test.skip(!fs.existsSync(sample), 'Local ALIO sample is not available');
  const bytes = [...fs.readFileSync(sample)];
  await page.goto('/');
  const result = await page.evaluate(async bytes => {
    const { openHwpWorker } = await import('/src/utils/hwpWorkerClient.js');
    const doc = await openHwpWorker(new Uint8Array(bytes));
    try { return { count: doc.count, first: (await doc.renderPage(0)).slice(0, 200) }; }
    finally { doc.close(); }
  }, bytes);
  expect(result.count).toBeGreaterThan(0);
  expect(result.first).toContain('<svg');
  console.log(`ALIO sample pages: ${result.count}`);
});

test('measures the locally collected ALIO corpus', async ({ page }) => {
  test.setTimeout(600000);
  const manifest = process.env.HWP_CORPUS_MANIFEST || path.join(os.tmpdir(), 'djc-hwp-corpus', 'manifest.json');
  test.skip(!fs.existsSync(manifest), 'Local ALIO corpus is not available');
  const files = JSON.parse(fs.readFileSync(manifest, 'utf8').replace(/^\uFEFF/, ''));
  const results = [];
  await page.goto('/');
  for (const file of files) {
    const bytes = [...fs.readFileSync(file.path)];
    const result = await page.evaluate(async ({ bytes, name }) => {
      const started = performance.now();
      let doc;
      try {
        const { openHwpWorker } = await import('/src/utils/hwpWorkerClient.js');
        doc = await openHwpWorker(new Uint8Array(bytes));
        const pages = doc.count;
        if (pages < 1) throw new Error('empty document');
        const svg = await doc.renderPage(0);
        if (!svg.includes('<svg')) throw new Error('no SVG');
        const firstPageMs = Math.round(performance.now() - started);
        for (let i = 1; i < pages; i++) if (!(await doc.renderPage(i)).includes('<svg')) throw new Error(`no SVG on page ${i + 1}`);
        return { name, ok: true, pages, firstPageMs, allPagesMs: Math.round(performance.now() - started) };
      } catch (error) { return { name, ok: false, error: String(error) }; }
      finally { doc?.close(); }
    }, { bytes, name: file.name });
    results.push(result);
  }
  console.log(JSON.stringify(results));
  expect(results.length).toBeGreaterThan(0);
  expect(results.filter(result => !result.ok)).toEqual([]);
});

test('opens a synthetic HWPX with at least 50 pages', async ({ page }) => {
  test.setTimeout(90000);
  const sample = path.join(os.tmpdir(), 'djc-hwp-corpus', 'synthetic-60-pages.hwpx');
  test.skip(!fs.existsSync(sample), 'Synthetic long document is not available');
  const bytes = [...fs.readFileSync(sample)];
  await page.goto('/');
  const result = await page.evaluate(async bytes => {
    const { openHwpWorker } = await import('/src/utils/hwpWorkerClient.js');
    const doc = await openHwpWorker(new Uint8Array(bytes));
    try { return { pages: doc.count, first: (await doc.renderPage(0)).includes('<svg'), last: (await doc.renderPage(doc.count - 1)).includes('<svg') }; }
    finally { doc.close(); }
  }, bytes);
  expect(result.pages).toBeGreaterThanOrEqual(50);
  expect(result.first && result.last).toBe(true);
  console.log(`Synthetic HWPX pages: ${result.pages}`);
});
