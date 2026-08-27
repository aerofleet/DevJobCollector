import { defineConfig } from '@playwright/test';

const viewports = [
  { name: 'mobile-360', viewport: { width: 360, height: 800 } },
  { name: 'tablet-768', viewport: { width: 768, height: 1024 } },
  { name: 'desktop-1024', viewport: { width: 1024, height: 768 } },
  { name: 'desktop-1440', viewport: { width: 1440, height: 900 } },
];

export default defineConfig({
  testDir: './production-e2e',
  fullyParallel: true,
  reporter: 'list',
  use: {
    baseURL: 'https://djc.itsdev.kr',
    browserName: 'chromium',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: viewports.map(({ name, viewport }) => ({ name, use: { viewport } })),
});
