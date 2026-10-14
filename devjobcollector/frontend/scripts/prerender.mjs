import { createServer } from 'vite';
import { readFile, writeFile, access } from 'node:fs/promises';

const server = await createServer({ mode: 'e2e', server: { middlewareMode: true }, appType: 'custom' });
try {
  const { renderHome } = await server.ssrLoadModule('/src/prerender.jsx');
  const home = renderHome();
  let target;
  for (const file of ['dist/client/index.html', 'dist/index.html']) {
    try { await access(file); target = file; break; } catch { /* Next build layout. */ }
  }
  if (!target) throw new Error('Built HTML not found');
  const html = await readFile(target, 'utf8');
  if (!html.includes('<div id="root"></div>')) throw new Error('Unexpected root markup');
  await writeFile(target, html.replace('<div id="root"></div>', `<div id="root">${home}<!--/prerender--></div>`));
  console.log(`Prerendered homepage: ${target}`);
} finally { await server.close(); }
