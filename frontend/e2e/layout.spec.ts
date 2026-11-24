import { expect, test, type Page } from "@playwright/test";

// Ortak sayfa layout sözleşmesi (frontend/frontend-layout.md §1–§2): her ekran tek PageContainer içinde,
// donanımlar kataloğuyla aynı genişlik/hiza ve navbar altından 12px boşlukla açılır; hiçbir genişlikte yatay taşma yok.
// Gateway fixture'ı yalnız oturumu sağlar; diğer çağrılar 404 döner (layout, veri durumundan bağımsız olmalı).
const owner = "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66";
const MAX_WIDTH = 1600; // max-w-[100rem]
const TOP_GAP = 12; // main pt-3
const WIDTHS = [375, 768, 1440, 1920, 2560];

const SCREENS: Array<[string, string]> = [
  ["Donanımlar (referans)", "/tr/donanimlar"],
  ["Ana Sayfa", "/tr"],
  ["Oyunlar", "/tr/oyunlar"],
  ["Kütüphanem", "/tr/kutuphane"],
  ["İncelemeler", "/tr/incelemeler"],
  ["Topluluk", "/tr/topluluk"],
  ["Arkadaşlar", "/tr/arkadaslar"],
  ["Kampanyalar", "/tr/donanimlar/kampanyalar"],
  ["Performans", "/tr/performans"],
  ["Performans paneli", "/tr/performans/panel"],
  ["Sana Özel", "/tr/sana-ozel"],
  ["Ayarlar", "/tr/ayarlar"],
  ["Bilgisayarlarım", "/tr/profil/bilgisayarlarim"],
  ["Profilim", "/tr/profil"],
  ["Hesap Ayarları", "/tr/profil/ayarlar"]
];

async function signedIn(page: Page) {
  await page.context().addCookies([
    { name: "ltz_session", value: "1", url: "http://localhost:5101" },
    { name: "XSRF-TOKEN", value: "fixture-csrf", url: "http://localhost:5101" }
  ]);
  await page.route("http://localhost:5080/api/v1/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const headers = {
      "access-control-allow-origin": "http://localhost:5101",
      "access-control-allow-credentials": "true",
      "access-control-allow-headers": "content-type, x-xsrf-token, accept",
      "access-control-allow-methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
      "cache-control": "no-store"
    };
    if (request.method() === "OPTIONS") return route.fulfill({ status: 204, headers });
    if (path === "/api/v1/auth/me") {
      return route.fulfill({ headers, json: { accountId: owner, roles: ["PLAYER"], authMethods: ["pwd"], accessTokenExpiresAt: "2030-01-01T00:00:00Z" } });
    }
    if (path === "/api/v1/user/me") {
      return route.fulfill({ headers, json: { userId: owner, displayName: "Fixture player", username: "fixture", avatarUrl: null } });
    }
    return route.fulfill({ status: 404, headers, json: {} });
  });
}

type Geometry = {
  containers: number;
  left: number;
  width: number;
  expectedLeft: number;
  expectedWidth: number;
  gap: number;
  overflow: number;
};

function measure(page: Page) {
  return page.evaluate((max): Geometry => {
    const containers = document.querySelectorAll("[data-page-container]");
    const main = document.getElementById("main-content")!;
    const box = containers[0]!.getBoundingClientRect();
    const mainBox = main.getBoundingClientRect();
    const style = getComputedStyle(main);
    const padStart = parseFloat(style.paddingLeft);
    const inner = mainBox.width - padStart - parseFloat(style.paddingRight);
    const expectedWidth = Math.min(inner, max);
    const navbar = document.querySelector("header")!.parentElement!.getBoundingClientRect();
    return {
      containers: containers.length,
      left: box.left,
      width: box.width,
      expectedLeft: mainBox.left + padStart + (inner - expectedWidth) / 2,
      expectedWidth,
      gap: box.top - navbar.bottom,
      overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth
    };
  }, MAX_WIDTH);
}

test.describe("ortak sayfa layout'u", () => {
  for (const [name, path] of SCREENS) {
    test(`${name}: tek kap, hardware genişliği/hizası, 12px üst boşluk, taşma yok`, async ({ page }) => {
      test.setTimeout(90_000);
      await signedIn(page);
      await page.setViewportSize({ width: WIDTHS[0]!, height: 900 });
      await page.goto(path);
      await expect(page.locator("[data-page-container]").first()).toBeVisible();
      await page.waitForLoadState("networkidle");

      for (const width of WIDTHS) {
        await page.setViewportSize({ width, height: 900 });
        await page.evaluate(() => window.scrollTo(0, 0));
        await expect.poll(async () => (await measure(page)).width, { message: `${name} @${width}` }).toBeGreaterThan(0);
        const g = await measure(page);
        const at = `${name} @${width}px`;
        expect.soft(g.containers, `${at}: PageContainer sayısı`).toBe(1);
        expect.soft(Math.abs(g.width - g.expectedWidth), `${at}: genişlik ${g.width} ≠ ${g.expectedWidth}`).toBeLessThanOrEqual(1);
        expect.soft(Math.abs(g.left - g.expectedLeft), `${at}: sol kenar ${g.left} ≠ ${g.expectedLeft}`).toBeLessThanOrEqual(1);
        expect.soft(Math.abs(g.gap - TOP_GAP), `${at}: üst boşluk ${g.gap}`).toBeLessThanOrEqual(1);
        expect.soft(g.overflow, `${at}: yatay taşma`).toBeLessThanOrEqual(0);
      }
    });
  }
});
