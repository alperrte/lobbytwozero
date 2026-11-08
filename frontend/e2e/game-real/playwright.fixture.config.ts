import { defineConfig, devices } from "@playwright/test";

// Fixture (sahte API) Game senaryolarını üretim derlemesiyle çalıştırır. Aynı klasörde başka bir `next dev` çalışırken
// (Next ikinci dev sunucusuna izin vermez) varsayılan yapılandırma kullanılamaz. Kullanım (frontend/ dizininden):
//   NEXT_PUBLIC_API_BASE_URL=http://localhost:5080 node node_modules/next/dist/bin/next build
//   node node_modules/@playwright/test/cli.js test -c e2e/game-real/playwright.fixture.config.ts
// Bu senaryolar gerçek entegrasyon testi DEĞİLDİR: API yanıtları tarayıcıda taklit edilir.
const port = 5101;
const baseURL = `http://localhost:${port}`;

export default defineConfig({
  testDir: "..",
  testMatch: "game*.spec.ts",
  testIgnore: "game-real.spec.ts",
  fullyParallel: true,
  retries: 0,
  reporter: "list",
  outputDir: "../../test-results/playwright-fixture",
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
