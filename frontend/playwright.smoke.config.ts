// frontend/playwright.smoke.config.ts
//
// Full-stack smoke test config: boots the real Spring Boot backend (H2
// in-memory, `smoke-test` profile) alongside the Angular dev server, then
// exercises the app end-to-end against the real API with no HTTP mocking.
//
// Run with: npm run e2e:smoke
//
// Note: keep this separate from playwright.config.ts — the base config drives
// the 13 mocked tests under ./e2e and must stay snappy; this config adds an
// extra Spring Boot boot step (~30–60 s), one DB, one backend, strict workers=1.

import { defineConfig, devices } from '@playwright/test';

const BACKEND_CMD =
  './mvnw spring-boot:test-run ' +
  '-Dspring-boot.run.profiles=smoke-test ' +
  '-Dspring-boot.run.jvmArguments="-Dserver.port=8080" ' +
  '-Dspring-boot.run.fork=false';

export default defineConfig({
  testDir: './e2e-smoke',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  timeout: 30_000,
  reporter: [['html', { open: 'never', outputFolder: 'playwright-report-smoke' }], ['list']],
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
    viewport: { width: 375, height: 667 },
    locale: 'fr-FR',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 375, height: 667 },
        locale: 'fr-FR',
      },
    },
  ],
  webServer: [
    {
      command: BACKEND_CMD,
      cwd: '../backend',
      // Spring Boot Actuator isn't a project dependency, so /actuator/health
      // returns 404 — use a lightweight public GET that lives in the app.
      url: 'http://localhost:8080/api/csrf',
      // NEVER reuse an existing 8080: if another backend (IntelliJ/dev run) is
      // already listening on 8080, it almost certainly isn't the `smoke-test`
      // profile, and its `/api/test/seed` returns 404. Always boot our own.
      reuseExistingServer: false,
      timeout: 180_000, // Maven + Spring Boot startup can take a while on first run
      stdout: 'pipe',
      stderr: 'pipe',
    },
    {
      command: 'npm start -- --port 4200',
      url: 'http://localhost:4200',
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
  ],
});
