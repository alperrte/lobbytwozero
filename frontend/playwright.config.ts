import { defineConfig, devices } from "@playwright/test";

const port = 5101;
const baseURL = `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  retries: 0,
  reporter: "list",
  outputDir: "test-results/playwright",
  use: {
    baseURL,
    // Dil URL önekindedir; Accept-Language Türkçe olmazsa "/" İngilizce sayfaya yönlenir.
    locale: "tr-TR",
    trace: "retain-on-failure",
    screenshot: "only-on-failure"
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] }
    }
  ],
  webServer: {
    command: `npm run dev -- --hostname 127.0.0.1 --port ${port}`,
    url: baseURL,
    reuseExistingServer: false,
    timeout: 120000
  }
});
