import { defineConfig, devices } from "@playwright/test";

/**
 * scripts/admin-announcements-browser.config.ts
 *
 * Standalone browser smoke config for Server Admin Announcements.
 * Reuses existing glean-hub-app Playwright toolchain without adding new package dependencies.
 *
 * Usage:
 *   pnpm --dir /home/loidinh/WS/glean-hub/glean-hub-app exec playwright test \
 *     --config /home/loidinh/WS/tabby-rush/scripts/admin-announcements-browser.config.ts
 */

const port = Number(process.env.ADMIN_BROWSER_PORT || 4173);
const baseURL = process.env.ADMIN_BROWSER_BASE_URL || `http://127.0.0.1:${port}`;
const authState = process.env.ADMIN_AUTH_STATE;

export default defineConfig({
  testDir: ".",
  testMatch: "admin-announcements-browser.spec.ts",
  timeout: 30000,
  expect: {
    timeout: 5000,
  },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [
    ["list"],
    ["html", { outputFolder: "browser-reports/playwright-html", open: "never" }],
  ],
  use: {
    baseURL,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    ...(authState ? { storageState: authState } : {}),
  },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        headless: true,
      },
    },
  ],
});
