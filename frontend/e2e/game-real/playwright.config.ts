import { defineConfig, devices } from "@playwright/test";

// Yalnız gerçek-backend Game senaryoları; `node e2e/game-real/run.mjs` (frontend/ dizininden) yığını hazırlar ve
// GAME_REAL_STATE ile NEXT_PUBLIC_API_BASE_URL değişkenlerini verir ve üretim derlemesini hazırlar. (Aynı klasörde başka bir
// `next dev` çalışabildiğinden dev sunucusu değil `next start` kullanılır; çalışan süreçlere dokunulmaz.)
const port = 5102;
const baseURL = `http://localhost:${port}`;

export default defineConfig({
  testDir: "..",
  testMatch: "game-real.spec.ts",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 120000,
  reporter: "list",
  outputDir: "../../test-results/playwright-real",
  use: { baseURL, locale: "tr-TR", trace: "retain-on-failure", screenshot: "only-on-failure" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    cwd: process.cwd(),
    command: `node node_modules/next/dist/bin/next start --hostname localhost --port ${port}`,
    url: baseURL,
    reuseExistingServer: false,
    timeout: 60000
  }
});
