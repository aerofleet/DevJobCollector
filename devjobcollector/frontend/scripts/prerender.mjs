import { createServer } from 'vite';
import { readFile, writeFile, access } from 'node:fs/promises';
import { dirname, join } from 'node:path';

const server = await createServer({ mode: 'e2e', server: { middlewareMode: true }, appType: 'custom' });
try {
  const { renderHome } = await server.ssrLoadModule('/src/prerender.jsx');
  const home = renderHome();
  let target;
  for (const file of ['dist/client/index.html', 'dist/index.html']) {
    try { await access(file); target = file; break; } catch { /* Next build layout. */ }
  }
  if (!target) throw new Error('Built HTML not found');
  let html = await readFile(target, 'utf8');
  // The home shell must not wait for another CSS network round trip to become visible.
  for (const match of html.matchAll(/<link rel="stylesheet"[^>]*href="([^"]+)"[^>]*>/g)) {
    const href = match[1];
    const css = await readFile(join(dirname(target), href.replace(/^\//, '')), 'utf8');
    const absoluteCss = css.replace(/url\(([^)]+)\)/g, (whole, value) => {
      const raw = value.trim().replace(/^["']|["']$/g, '');
      if (/^(data:|https?:|\/|#)/.test(raw)) return whole;
      const resolved = new URL(raw, `https://build.local${href}`);
      return `url("${resolved.pathname}${resolved.search}${resolved.hash}")`;
    });
    html = html.replace(match[0], `<style data-prerender-css>${absoluteCss}</style>`);
  }
  if (!html.includes('<div id="root"></div>')) throw new Error('Unexpected root markup');
  await writeFile(target, html.replace('<div id="root"></div>', `<div id="root">${home}<!--/prerender--></div>`));
  console.log(`Prerendered homepage: ${target}`);
} finally { await server.close(); }
