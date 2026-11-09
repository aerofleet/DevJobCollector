import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  testMatch: 'hwp-*.spec.js',
  use: { baseURL: globalThis.process?.env.HWP_E2E_BASE_URL || 'http://127.0.0.1:4197', browserName: 'chromium' },
  webServer: globalThis.process?.env.HWP_E2E_EXTERNAL_SERVER === 'true' ? undefined : { command: 'node ./node_modules/vite/bin/vite.js --mode e2e --host 127.0.0.1 --port 4197 --strictPort', url: 'http://127.0.0.1:4197', reuseExistingServer: false, timeout: 120000 },
});
