import { defineConfig, devices } from "@playwright/test";

// ÇALIŞAN bir arayüze (varsayılan http://localhost:3000, `next dev`) karşı gerçek-yığın senaryoları. Sunucu başlatmaz ve durdurmaz;
// arka uç için `node e2e/game-real/serve.mjs` kullanılır. GAME_REAL_STATE, o betiğin yazdığı state.json yoludur.
const baseURL = process.env.GAME_LIVE_BASE_URL ?? "http://localhost:3000";

export default defineConfig({
  testDir: "..",
  testMatch: "game-real.spec.ts",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 150000,
  reporter: "list",
  outputDir: "../../test-results/playwright-live",
  use: { baseURL, locale: "tr-TR", trace: "retain-on-failure", screenshot: "only-on-failure" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }]
});
