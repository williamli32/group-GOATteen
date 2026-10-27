import { defineConfig } from 'playwright/test';
import workspaceConfig from './playwright.config';

export default defineConfig({
  ...workspaceConfig,
  testMatch: ['portfolio-live.spec.ts', 'portfolio-interactions.spec.ts'],
  reporter: [['list']],
});
