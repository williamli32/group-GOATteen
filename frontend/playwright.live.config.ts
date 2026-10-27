import { defineConfig } from 'playwright/test';
import { viewports } from './playwright.config';

export default defineConfig({
  testDir: './e2e',
  testMatch: 'goal5-live.spec.ts',
  timeout: 120_000,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [
    ['list'],
    ['junit', { outputFile: 'test-results/live-junit.xml' }],
    ['html', { open: 'never', outputFolder: 'playwright-report-live' }],
  ],
  use: {
    baseURL: process.env['GOAL5_UI_URL'] ?? 'http://localhost:4200',
    browserName: 'chromium',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    contextOptions: { reducedMotion: 'reduce' },
  },
  projects: viewports,
});
