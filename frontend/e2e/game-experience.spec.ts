import { expect, test, type Page } from "@playwright/test";
import messages from "../src/shared/lib/i18n/messages/tr.json";

// Yanıtlar tarayıcı fixture'ıdır; gerçek Game/Gateway entegrasyon kabulü yerine geçmez.
test.use({ baseURL: "http://localhost:5101" });
const t = messages.Game;
const accountId = "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66";
const ids = [1, 2, 3].map((n) => `0198a3c2-000${n}-7d44-9a55-3f1c2b4d5e66`);
const pixel = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==", "base64");
const card = (n: number, extra: object = {}) => ({ gameId: ids[n], title: `E2E Oyun ${n + 1}`, origin: "INTERNAL", metadataCompleteness: "PARTIAL", gameType: "MAIN_GAME", coverUrl: null, ...extra });
const cors = { "Access-Control-Allow-Origin": "http://localhost:5101", "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Headers": "Content-Type,X-XSRF-TOKEN", "Access-Control-Allow-Methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS", "Cache-Control": "no-store" };

async function overflowFree(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
}

for (const width of [1440, 768, 390]) {
  test(`Katalog kartları, görünüm geçişi ve URL'de kalıcı filtreler (${width})`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const queries: URLSearchParams[] = [];
    await page.route("https://images.example/**", (route) => route.fulfill({ body: pixel, contentType: "image/png" }));
    await page.route("http://localhost:5080/api/v1/**", async (route) => {
      const url = new URL(route.request().url());
      const reply = (json: unknown, status = 200) => route.fulfill({ json, status, headers: cors });
      if (url.pathname === "/api/v1/auth/me") return reply({ status: 401 }, 401);
      if (url.pathname === "/api/v1/game/games") {
        queries.push(url.searchParams);
        return reply({ items: [
          { game: card(0, { coverUrl: "https://images.example/c.png" }), releaseDate: "2026-01-01", free: true, rating: { status: "RATED", averageRating: 8.5, ratingCount: 3 } },
          { game: card(1), releaseDate: null, comingSoon: true, rating: { status: "UNRATED", averageRating: null, ratingCount: 0 } },
          { game: card(2), releaseDate: "2026-02-02", earlyAccess: true, rating: { status: "UNAVAILABLE", averageRating: null, ratingCount: null } }
        ], page: 0, size: 20, totalElements: 3 });
      }
      return reply({ status: 404 }, 404);
    });
    await page.goto("/tr/oyunlar");
    await expect(page.getByRole("link", { name: "E2E Oyun 1", exact: true })).toBeVisible();
    await expect(page.getByTestId("cover-placeholder")).toHaveCount(2);
    await expect(page.getByText(t.unrated)).toBeVisible();
    await expect(page.getByText(t.ratingUnavailable)).toBeVisible();
    // Kapak üstü durum rozetleri: ücretsiz, yakında ve erken erişim ayrı kartlarda, taşma olmadan.
    await expect(page.getByTestId("free-mark")).toHaveCount(1);
    await expect(page.getByTestId("free-mark")).toHaveText(t.freeBadge);
    await expect(page.getByTestId("coming-soon-mark")).toHaveCount(1);
    await expect(page.getByTestId("coming-soon-mark")).toHaveText(t.comingSoonBadge);
    await expect(page.getByTestId("early-access-mark")).toHaveCount(1);
    await overflowFree(page);

    await page.getByRole("button", { name: t.viewList, exact: true }).click();
    await expect(page).toHaveURL(/view=list/);
    await expect(page.getByRole("button", { name: t.viewList, exact: true })).toHaveAttribute("aria-pressed", "true");

    await page.getByRole("button", { name: t.filters, exact: true }).click();
    await page.getByLabel(t.fields.genre, { exact: true }).fill("rpg");
    await page.getByLabel(t.fields.minRating, { exact: true }).fill("7.5");
    await page.getByRole("button", { name: t.applyFilters, exact: true }).click();
    const chips = page.getByRole("group", { name: t.activeFilters });
    await expect(chips.getByRole("button", { name: /rpg/ })).toBeVisible();
    await expect(chips.getByRole("button", { name: /7.5/ })).toBeVisible();
    await expect.poll(() => queries.some((q) => q.get("genre") === "rpg" && q.get("minRating") === "7.5")).toBe(true);

    await page.reload();
    await expect(page.getByRole("group", { name: t.activeFilters }).getByRole("button", { name: /rpg/ })).toBeVisible();
    await expect(page).toHaveURL(/view=list/);
    await page.getByRole("group", { name: t.activeFilters }).getByRole("button", { name: /rpg/ }).click();
    await expect(page).not.toHaveURL(/genre=rpg/);
    await expect(page).toHaveURL(/minRating=7.5/);
    await page.goBack();
    await expect(page).toHaveURL(/genre=rpg/);

    // Tarih aralığı ters ise uygulama düğmesi kapalıdır ve hata duyurulur.
    const toggle = page.getByRole("button", { name: new RegExp(String.raw`^${t.filters}\s*\d*$`) });
    if ((await toggle.getAttribute("aria-expanded")) !== "true") await toggle.click();
    await page.getByLabel(t.fields.releasedAfter, { exact: true }).fill("2026-05-01");
    await page.getByLabel(t.fields.releasedBefore, { exact: true }).fill("2026-01-01");
    await expect(page.getByRole("main").getByRole("alert")).toContainText(t.filterErrors.dateRange);
    await expect(page.getByRole("button", { name: t.applyFilters, exact: true })).toBeDisabled();
    await overflowFree(page);

    for (const theme of ["dark", "light"]) {
      await page.evaluate((value) => { document.documentElement.dataset.theme = value; }, theme);
      await page.screenshot({ path: `test-results/game-catalogue-${theme}-${width}.png`, fullPage: true });
    }
  });
}

for (const width of [1440, 390]) {
  test(`Oyun detayı: galeri klavyesi ve bölüm gezinmesi (${width})`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.route("https://images.example/**", (route) => route.fulfill({ body: pixel, contentType: "image/png" }));
    await page.route("http://localhost:5080/api/v1/**", async (route) => {
      const url = new URL(route.request().url());
      const reply = (json: unknown, status = 200) => route.fulfill({ json, status, headers: cors });
      if (url.pathname === "/api/v1/auth/me") return reply({ status: 401 }, 401);
      if (url.pathname === "/api/v1/game/games/prices") return reply({ market: { country: "US", currency: "USD" }, items: [{ gameId: ids[0], status: "STALE", offers: [] }] });
      if (url.pathname.endsWith("/developer-content")) return reply({ gameId: ids[0], description: null, media: [], updateNotes: [] });
      if (url.pathname.includes("/modules/")) return reply({ gameId: ids[0], type: "ACHIEVEMENTS", state: "EMPTY", items: [], page: 0, size: 20, totalElements: 0 });
      if (url.pathname === `/api/v1/game/games/${ids[0]}`) return reply({
        game: card(0, { coverUrl: "https://images.example/cover.png" }), description: "Açıklama", releaseDate: "2026-01-01", freshness: { status: "FRESH", fetchedAt: null },
        metadata: { developers: ["Stüdyo"], publishers: [], languages: [], genres: [{ id: "g", code: "rpg", nameTr: "Rol yapma", nameEn: "RPG" }], platforms: [],
          media: [{ type: "SCREENSHOT", url: "https://images.example/s1.png", provider: "IGDB", sortOrder: 1 }, { type: "SCREENSHOT", url: "https://images.example/s2.png", provider: "IGDB", sortOrder: 2 }] }
      });
      return reply({ status: 404 }, 404);
    });
    await page.goto(`/tr/oyunlar/${ids[0]}`);
    await expect(page.getByRole("heading", { level: 1, name: "E2E Oyun 1" })).toBeVisible();
    const gallery = page.getByRole("group", { name: t.gallery });
    await gallery.focus();
    await expect(gallery.getByText("1 / 3")).toBeVisible();
    await page.keyboard.press("ArrowRight");
    await expect(gallery.getByText("2 / 3")).toBeVisible();
    await page.getByRole("navigation", { name: t.pageSections }).getByRole("link", { name: t.sections.prices }).click();
    await expect(page).toHaveURL(/#game-prices$/);
    await expect(page.getByText(t.priceStates.STALE)).toBeVisible();
    await overflowFree(page);
    await page.screenshot({ path: `test-results/game-detail-${width}.png`, fullPage: true });
  });
}

test("Admin editör listesi: sırala, çıkar, gerekçe + onayla kaydet (CSRF'li PUT)", async ({ page }) => {
  await page.context().addCookies([{ name: "XSRF-TOKEN", value: "fixture-csrf", domain: "localhost", path: "/" }, { name: "ltz_session", value: "1", domain: "localhost", path: "/" }]);
  const writes: { method: string; csrf: string | undefined; body: Record<string, unknown> }[] = [];
  await page.route("http://localhost:5080/api/v1/**", async (route) => {
    const request = route.request(), url = new URL(request.url());
    const reply = (json: unknown, status = 200) => route.fulfill({ json, status, headers: cors });
    if (request.method() === "OPTIONS") return route.fulfill({ status: 204, headers: cors });
    if (url.pathname === "/api/v1/auth/me") return reply({ accountId, roles: ["ADMIN"] });
    if (url.pathname === "/api/v1/auth/csrf") return route.fulfill({ status: 204, headers: cors });
    if (request.method() === "PUT" && url.pathname === "/api/v1/game/admin/popular") { writes.push({ method: "PUT", csrf: request.headers()["x-xsrf-token"], body: request.postDataJSON() }); return route.fulfill({ status: 204, headers: cors }); }
    if (url.pathname === "/api/v1/game/games/popular") return reply({ items: [0, 1, 2].map((n) => ({ game: card(n), releaseDate: null, editorialRank: n + 1 })), page: 0, size: 100, totalElements: 3 });
    return reply({ status: 404 }, 404);
  });
  await page.goto("/tr/oyunlar/yonetim");
  await page.getByRole("tab", { name: t.popularSelection, exact: true }).click();
  await expect(page.getByText("E2E Oyun 1", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: t.moveDown.replace("{title}", "E2E Oyun 1") }).click();
  await page.getByRole("button", { name: t.removeFromList.replace("{title}", "E2E Oyun 3") }).click();
  const form = page.getByRole("form", { name: t.popularSave });
  await form.getByLabel(t.fields.reason).fill("E2E editör sıralaması");
  await expect(form.getByRole("button", { name: t.save, exact: true })).toBeDisabled();
  await form.getByLabel(t.confirmAction).check();
  await form.getByRole("button", { name: t.save, exact: true }).click();
  await expect.poll(() => writes.length).toBe(1);
  expect(writes[0]).toEqual({ method: "PUT", csrf: "fixture-csrf", body: { gameIds: [ids[1], ids[0]], reason: "E2E editör sıralaması" } });
  await overflowFree(page);
});

test("Yönetim bölümleri ortak Game gezintisinden bulunur ve ?section ile bağlantılanır; yetkisiz rol alan görmez", async ({ page }) => {
  // Oturum ipucu çerezi (Auth `ltz_session`) varken arayüz /auth/me sorar; yoksa kullanıcı misafir sayılır.
  await page.context().addCookies([{ name: "ltz_session", value: "1", domain: "localhost", path: "/" }]);
  await page.route("http://localhost:5080/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    const reply = (json: unknown, status = 200) => route.fulfill({ json, status, headers: cors });
    if (url.pathname === "/api/v1/auth/me") return reply({ accountId, roles: ["ADMIN", "MODERATOR"] });
    return reply({ status: 404 }, 404);
  });
  await page.goto("/tr/oyunlar");
  const nav = page.getByRole("navigation", { name: t.catalogue, exact: true });
  await expect(nav.getByRole("link", { name: t.workspace.admin, exact: true })).toBeVisible();
  await expect(nav.getByRole("link", { name: t.workspace.developer, exact: true })).toHaveCount(0);
  await nav.getByRole("link", { name: t.workspace.moderation, exact: true }).click();
  await expect(page).toHaveURL(/yonetim\?section=moderation/);
  await expect(page.getByRole("heading", { level: 1, name: t.workspace.moderation })).toBeVisible();
  await expect(page.getByRole("form", { name: t.createGame })).toHaveCount(0);
  await page.goto("/tr/oyunlar/yonetim?section=admin");
  await expect(page.getByRole("heading", { level: 1, name: t.workspace.admin })).toBeVisible();
  await expect(page.getByRole("tab", { name: t.adminGames, exact: true })).toBeVisible();

  const player = await page.context().newPage();
  await player.route("http://localhost:5080/api/v1/**", (route) => route.fulfill({ json: { accountId, roles: ["PLAYER"] }, headers: cors }));
  await player.goto("/tr/oyunlar/yonetim?section=admin");
  await expect(player.getByText(t.noWorkspaceAccess)).toBeVisible();
  await expect(player.getByRole("form")).toHaveCount(0);
  await expect(player.getByRole("navigation", { name: t.catalogue, exact: true }).getByRole("link", { name: t.workspace.admin })).toHaveCount(0);
});
