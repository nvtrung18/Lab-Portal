import { defineConfig } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const output = process.env.AI_TEST_OUTPUT_DIR;
if (!output || !process.env.AI_TEST_BACKEND_URL) throw new Error('Run through the isolated remediation runner');

export default defineConfig({
  testDir: '.',
  testMatch: 'assistant.spec.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 30_000,
  expect: { timeout: 8_000 },
  outputDir: path.join(output, 'browser-artifacts'),
  reporter: [['list'], ['json', { outputFile: path.join(output, 'browser-results.json') }],
    ['junit', { outputFile: path.join(output, 'browser-results.xml') }]],
  use: { baseURL: 'http://127.0.0.1:4173', browserName: 'chromium', headless: true,
    screenshot: 'only-on-failure', trace: 'retain-on-failure' },
  webServer: { command: 'node tests/ai/serve.mjs', url: 'http://127.0.0.1:4173',
    cwd: path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..'),
    reuseExistingServer: false, timeout: 45_000 },
});
