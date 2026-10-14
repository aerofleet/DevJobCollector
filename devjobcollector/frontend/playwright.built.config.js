import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './build-e2e',
  reporter: 'list',
  use: { baseURL: 'http://127.0.0.1:4175', browserName: 'chromium', screenshot: 'only-on-failure' },
  projects: [360, 1440].map((width) => ({ name: `built-${width}`, use: { viewport: { width, height: 900 } } })),
  webServer: { command: 'node node_modules/wrangler/bin/wrangler.js dev --config dist/djc/wrangler.json --local --port 4175', url: 'http://127.0.0.1:4175', reuseExistingServer: false, timeout: 120000 },
});
