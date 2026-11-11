import { expect, test, type Page } from "@playwright/test";

// Browser contract fixtures only. These do not prove live backend authorization.
const accountId = "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66";
const sessionId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const startedAt = "2026-11-01T10:00:00Z";
const endedAt = "2026-11-01T11:00:00Z";

async function installFixture(page: Page, options: { guest?: boolean; empty?: boolean; historyStatus?: number; seriesStatus?: number } = {}) {
  if (!options.guest) await page.context().addCookies([{ name: "ltz_session", value: "1", url: "http://localhost:5101" }]);
  const requests: string[] = [];
  await page.route("http://localhost:5080/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const headers = { "access-control-allow-origin": "http://localhost:5101", "access-control-allow-credentials": "true", "access-control-allow-methods": "GET, OPTIONS", "access-control-allow-headers": "content-type, accept", "cache-control": "private, no-store" };
    const reply = (json: unknown, status = 200) => route.fulfill({ json, status, headers });
    if (request.method() === "OPTIONS") return route.fulfill({ status: 204, headers });
    requests.push(path + url.search);
    if (path === "/api/v1/auth/me") return reply({ accountId, roles: ["PLAYER"], authMethods: ["pwd"], accessTokenExpiresAt: "2030-01-01T00:00:00Z" });
    if (path === "/api/v1/user/me") return reply({ userId: accountId, displayName: "Fixture player", username: "fixture", avatarUrl: null });
    if (path === "/api/v1/performance/devices") return reply([]);
    if (path === "/api/v1/hardware/profiles") return reply({ items: [], page: 0, size: 12, totalElements: 0, totalPages: 0 });
    if (path === "/api/v1/performance/sessions") return options.historyStatus ? reply({}, options.historyStatus) : reply({ items: options.empty ? [] : [{ id: sessionId, gameId: sessionId, gameTitle: "Recorded Fixture Game", hardwareProfileId: null, deviceLinkId: null, source: "MANUAL", status: "COMPLETED", startedAt, endedAt, durationSeconds: 3600, sampleCount: 2 }], page: 0, size: 20, hasNext: false, totalElements: options.empty ? 0 : 1 });
    if (path === `/api/v1/performance/sessions/${sessionId}/series`) return options.seriesStatus ? reply({}, options.seriesStatus) : reply({ sessionId, metric: url.searchParams.get("metric"), unit: "FPS", resolution: "RAW", downsampled: false, bucketMillis: null, aggregation: "NONE", totalSamples: 2, points: [{ at: startedAt, value: 60, min: 60, max: 60, count: 1 }, { at: endedAt, value: 80, min: 80, max: 80, count: 1 }] });
    if (path === `/api/v1/performance/sessions/${sessionId}/report`) return reply({ id: sessionId, sessionId, resolution: "1920x1080", graphicsPreset: "HIGH", avgFps: 70, p1LowFps: null, maxCpuTempC: null, maxGpuTempC: null, avgCpuUsagePercent: 0, avgGpuUsagePercent: null, notes: "Fixture manual report", source: "MANUAL", createdAt: endedAt, updatedAt: endedAt });
    return reply({}, 404);
  });
  return requests;
}

for (const viewport of [{ width: 375, height: 812 }, { width: 768, height: 1024 }, { width: 1440, height: 1000 }]) {
  test(`dashboard chart and report at ${viewport.width}px`, async ({ page }) => {
    await page.setViewportSize(viewport);
    const requests = await installFixture(page);
    await page.goto("/tr/performans/panel");
    const main = page.getByRole("main");
    await expect(main.getByRole("heading", { name: "Recorded Fixture Game", exact: true })).toBeVisible();
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
    expect(requests.some((path) => path.includes("/series"))).toBe(false);
    await main.getByRole("button", { name: "Recorded Fixture Game oturumunu incele" }).click();
    await expect(main.getByRole("table")).toBeVisible();
    await expect(main.getByText("1920x1080")).toBeVisible();
    await expect(main.getByText("0 %", { exact: true })).toBeVisible();
    await expect(main.getByText(/Kullanıcının manuel kaydıdır/)).toBeVisible();
    expect(requests.find((path) => path.includes("/series"))).toContain("maxPoints=100");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: `test-results/playwright/performance-${viewport.width}.png`, fullPage: true });
  });
}

test("guest landing has a disabled download and private dashboard redirects", async ({ page }) => {
  const requests = await installFixture(page, { guest: true });
  await page.goto("/en/performance");
  await expect(page.getByRole("button", { name: "Download not available yet" })).toBeDisabled();
  await expect(page.getByRole("main").getByRole("link", { name: "Sign in", exact: true })).toBeVisible();
  expect(requests.some((path) => path.startsWith("/api/v1/performance/"))).toBe(false);
  await page.goto("/en/performance/dashboard");
  await expect(page).toHaveURL(/\/en\/login\?next=/);
  expect(requests.some((path) => path.startsWith("/api/v1/performance/"))).toBe(false);
});

test("server failure stays separate from an empty hardware account", async ({ page }) => {
  await installFixture(page, { historyStatus: 503 });
  await page.goto("/tr/performans/panel");
  const main = page.getByRole("main");
  await expect(main.getByRole("heading", { name: "Kayıtlı bilgisayarın yok" })).toBeVisible();
  await expect(main.getByRole("alert")).toContainText("Bağımlı servis");
  await expect(main.getByText("Bu filtreye uyan kayıtlı oturum yok.")).toHaveCount(0);
});

test("series failure does not hide a valid manual report", async ({ page }) => {
  await installFixture(page, { seriesStatus: 503 });
  await page.goto("/tr/performans/panel");
  const main = page.getByRole("main");
  await main.getByRole("button", { name: "Recorded Fixture Game oturumunu incele" }).click();
  await expect(main.getByRole("alert")).toContainText("Bağımlı servis");
  await expect(main.getByText("1920x1080")).toBeVisible();
  await expect(main.getByRole("table")).toHaveCount(0);
});

test("English dark dashboard has readable labels and keyboard access", async ({ page }) => {
  await page.emulateMedia({ colorScheme: "dark", reducedMotion: "reduce" });
  await installFixture(page);
  await page.goto("/en/performance/dashboard");
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  const open = page.getByRole("button", { name: "Review Recorded Fixture Game session" });
  await open.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("table", { name: "FPS (FPS)" })).toBeVisible();
  await expect(page.getByText(/Manually entered by the user/)).toBeVisible();
  await page.screenshot({ path: "test-results/playwright/performance-dark-en.png", fullPage: true });
});
