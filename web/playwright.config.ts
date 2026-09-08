import { defineConfig, devices } from '@playwright/test'

/**
 * E2E-прогоны живут в shots-e2e/*.png (скриншоты) — конфиг исторически
 * минимальный: один проект, vite dev-сервер поднимается сам.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  retries: 0,
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev -- --port 5173 --strictPort',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
})
