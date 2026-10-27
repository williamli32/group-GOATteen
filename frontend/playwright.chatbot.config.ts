import { defineConfig } from 'playwright/test';
import workspaceConfig from './playwright.config';

export default defineConfig({
  ...workspaceConfig,
  testMatch: 'trading-chatbot.spec.ts',
  reporter: [['list']],
});
