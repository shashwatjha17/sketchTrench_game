import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests for SketchTrench.
 *   local:       npm run e2e                     (needs `npm run dev` + the backend on :8080)
 *   live site:   BASE_URL=https://sketch-trench-game.vercel.app npm run e2e
 * Multiplayer cases open separate browser contexts, so each player has its own identity.
 */
const BASE_URL = process.env.BASE_URL ?? 'http://localhost:5173';
const LIVE = !BASE_URL.includes('localhost');

export default defineConfig({
  testDir: './e2e',
  timeout: 150_000,
  expect: { timeout: 15_000 },
  fullyParallel: true,
  // the live backend is a small free instance: go easier on it
  workers: LIVE ? 2 : 4,
  retries: 0,
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: 'e2e-report' }],
    ['json', { outputFile: LIVE ? 'e2e-results-live.json' : 'e2e-results-local.json' }],
  ],
  use: {
    baseURL: BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    permissions: ['clipboard-read', 'clipboard-write'],
    actionTimeout: 15_000,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } }],
});
