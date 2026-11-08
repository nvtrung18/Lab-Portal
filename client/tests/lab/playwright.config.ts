import { defineConfig } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const clientRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

export default defineConfig({
  testDir: '.',
  testMatch: 'application-submit.spec.ts',
  workers: 1,
  retries: 0,
  timeout: 20_000,
  expect: { timeout: 5_000 },
  outputDir: path.resolve(clientRoot, '../.artifacts/lab-application-tests'),
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:4174',
    browserName: 'chromium',
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'node tests/lab/serve.mjs',
    cwd: clientRoot,
    url: 'http://127.0.0.1:4174',
    reuseExistingServer: false,
    timeout: 45_000,
  },
});
