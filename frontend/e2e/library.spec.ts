import { expect, test, type Page } from "@playwright/test";

// Gateway fixture'ları yalnız UI sözleşmesini doğrular; canlı servis entegrasyonu değildir.
const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const owner = "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66";
const game = { gameId, title: "Library Fixture Game", coverUrl: null, origin: "INTERNAL", metadataCompleteness: "PARTIAL", gameType: "GAME", status: "AVAILABLE", metadataSource: "LIVE", metadataRefreshedAt: null };
const entry = () => ({ id: gameId, gameId, inCollection: true, favorite: false, completed: false, addedAt: "2026-11-01T12:00:00Z", updatedAt: "2026-11-01T12:00:00Z", version: 0 });

async function fixture(page: Page, signedIn = true) {
  if (signedIn) await page.context().addCookies([{ name: "ltz_session", value: "1", url: "http://localhost:5101" }, { name: "XSRF-TOKEN", value: "fixture-csrf", url: "http://localhost:5101" }]);
  const state = { entries: [entry()], profileStatus: 200, privacyConflict: false, metadataPartial: false, requests: [] as string[], privacyBodies: [] as unknown[], privacy: { version: 2, library: "PUBLIC", friendsList: "FRIENDS", hardware: "PRIVATE", performance: "FRIENDS", guestProfileAccess: "DETAILED" } };
  await page.route("http://localhost:5080/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    const headers = { "access-control-allow-origin": "http://localhost:5101", "access-control-allow-credentials": "true", "access-control-allow-headers": "content-type, x-xsrf-token, accept", "access-control-allow-methods": "GET, POST, PUT, DELETE, OPTIONS", "cache-control": "no-store" };
    const reply = (json: unknown, status = 200) => route.fulfill({ json, status, headers });
    if (method === "OPTIONS") return route.fulfill({ status: 204, headers });
    state.requests.push(`${method} ${path}${url.search}`);
    if (path === "/api/v1/auth/me") return reply(signedIn ? { accountId: owner, roles: ["PLAYER"], authMethods: ["pwd"], accessTokenExpiresAt: "2030-01-01T00:00:00Z" } : {}, signedIn ? 200 : 401);
    if (path === "/api/v1/user/me") return reply({ userId: owner, displayName: "Fixture player", username: "fixture", avatarUrl: null });
    if (path === "/api/v1/game/games") return reply({ items: [{ game }], page: 0, size: 8, totalElements: 1 });
    if (path === "/api/v1/user/me/privacy") {
      if (method === "PUT") {
        state.privacyBodies.push(request.postDataJSON());
        if (state.privacyConflict) { state.privacyConflict = false; state.privacy = { ...state.privacy, hardware: "PUBLIC", version: 3 }; return reply({ code: "CONCURRENT_MODIFICATION" }, 409); }
        state.privacy = { ...request.postDataJSON(), version: state.privacy.version + 1 };
      }
      return reply(state.privacy);
    }
    if (!path.startsWith("/api/v1/library/")) return reply({}, 404);
    if (path.includes("/users/") && state.profileStatus !== 200) return reply({}, state.profileStatus);
    if (method !== "GET") {
      expect(request.headers()["x-xsrf-token"]).toBe("fixture-csrf");
      let current = state.entries[0];
      if (method === "DELETE") {
        if (path.endsWith("/favorite")) current.favorite = false;
        else if (path.endsWith("/completed")) current.completed = false;
        else state.entries = [];
        return route.fulfill({ status: 204, headers });
      }
      if (!current) { current = entry(); state.entries = [current]; }
      if (path.endsWith("/favorite")) current.favorite = true;
      if (path.endsWith("/completed")) current.completed = true;
      return reply(current);
    }
    if (path.endsWith("/statistics")) return reply({ totalGames: state.entries.length, favoriteGames: state.entries.filter((item) => item.favorite).length, completedGames: state.entries.filter((item) => item.completed).length });
    const filtered = state.entries.filter((item) => ["favorite", "completed"].every((key) => !url.searchParams.has(key) || item[key as "favorite" | "completed"] === (url.searchParams.get(key) === "true")));
    const pageIndex = Number(url.searchParams.get("page") ?? 0);
    return reply({ items: pageIndex === 0 ? filtered.map((item) => ({ entry: item, game: state.metadataPartial ? { ...game, metadataSource: "LAST_KNOWN", status: "TEMPORARILY_UNAVAILABLE" } : game })) : [], page: pageIndex, size: Number(url.searchParams.get("size") ?? 20), totalElements: filtered.length, metadataPartial: state.metadataPartial });
  });
  return state;
}

test("koleksiyon, favori, bitirildi, silme onayı ve yeniden ekleme", async ({ page }) => {
  const state = await fixture(page);
  await page.goto("/tr/kutuphane");
  const main = page.getByRole("main");
  await expect(main.getByRole("heading", { name: "Library Fixture Game" })).toBeVisible();
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
  await main.getByRole("button", { name: "Favoriye ekle", exact: true }).click();
  await expect(main.getByRole("button", { name: "Favoriden çıkar" })).toBeVisible();
  await main.getByRole("link", { name: "Favoriler", exact: true }).click();
  await expect(page).toHaveURL("/tr/kutuphane/favoriler");
  await main.getByRole("button", { name: "Bitirildi işaretle" }).click();
  await expect(main.getByRole("button", { name: "Bitirildi işaretini kaldır" })).toBeVisible();
  await main.getByRole("button", { name: "Favoriden çıkar" }).click();
  await expect(main.getByText("Bu filtrelerde oyun yok")).toBeVisible();
  expect(state.entries[0].completed).toBe(true);
  await main.getByRole("link", { name: "Bitirilenler", exact: true }).click();
  await expect(main.getByRole("heading", { name: "Library Fixture Game" })).toBeVisible();
  await main.getByRole("button", { name: "Kütüphaneden kaldır" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole("button", { name: "Vazgeç" })).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(dialog).not.toBeVisible();
  expect(state.entries).toHaveLength(1);
  await main.getByRole("button", { name: "Kütüphaneden kaldır" }).click();
  await dialog.getByRole("button", { name: "Kütüphaneden kaldır" }).click();
  await expect(main.getByText("Bu filtrelerde oyun yok")).toBeVisible();
  await main.getByRole("link", { name: "Oyun ekle", exact: true }).click();
  await main.getByRole("searchbox", { name: "Katalogda oyun ara" }).fill("Fixture");
  await main.getByRole("button", { name: /Library Fixture Game/ }).click();
  await main.getByRole("checkbox", { name: "Favoriye ekle" }).check();
  await main.getByRole("button", { name: "Oyun Ekle", exact: true }).click();
  await expect(page).toHaveURL("/tr/kutuphane");
  await expect(main.getByRole("button", { name: "Favoriden çıkar" })).toBeVisible();
  expect(state.entries).toHaveLength(1);
  expect(state.requests.some((request) => request.includes("/game/games/batch"))).toBe(false);
});

test("gizlilik çakışmasında diğer alanları korur ve yeni sürümle kaydeder", async ({ page }) => {
  const state = await fixture(page); state.privacyConflict = true;
  await page.goto("/tr/kutuphane/gizlilik");
  await page.getByRole("radio", { name: /Yalnız ben/ }).check();
  await page.getByRole("button", { name: "Kaydet", exact: true }).click();
  await expect(page.getByRole("main").getByRole("alert")).toContainText("Ayarlar başka bir yerde değişti");
  await expect(page.getByRole("radio", { name: /Herkese açık/ })).toBeChecked();
  expect(state.privacyBodies[0]).toEqual({ version: 2, friendsList: "FRIENDS", library: "PRIVATE", hardware: "PRIVATE", performance: "FRIENDS", guestProfileAccess: "DETAILED" });
  await page.getByRole("radio", { name: /Yalnız ben/ }).check();
  await page.getByRole("button", { name: "Kaydet", exact: true }).click();
  await expect(page.getByText("Kütüphane gizliliğin kaydedildi.")).toBeVisible();
  expect(state.privacyBodies[1]).toEqual({ version: 3, friendsList: "FRIENDS", library: "PRIVATE", hardware: "PUBLIC", performance: "FRIENDS", guestProfileAccess: "DETAILED" });
});

test("profil 403/503 kararları bilgi sızdırmaz; izinli profil salt okunurdur", async ({ page }) => {
  const state = await fixture(page); state.profileStatus = 403;
  await page.goto(`/tr/kutuphane/oyuncu/${owner}`);
  await expect(page.getByRole("main").getByRole("alert")).toContainText("görüntüleme iznin yok");
  expect(state.requests.some((request) => request.includes(`/users/${owner}/entries/statistics`))).toBe(false);
  state.profileStatus = 503;
  await page.getByRole("button", { name: "Tekrar dene" }).click();
  await expect(page.getByRole("main").getByRole("alert")).toContainText("görünürlüğü şu anda doğrulanamıyor");
  state.profileStatus = 200;
  await page.getByRole("button", { name: "Tekrar dene" }).click();
  await expect(page.getByRole("heading", { name: "Library Fixture Game" })).toBeVisible();
  await expect(page.getByRole("button", { name: /Favoriye|Kütüphaneden|Bitirildi/ })).toHaveCount(0);
});

test("misafir güvenli giriş dönüş adresine yönlendirilir", async ({ page }) => {
  const state = await fixture(page, false);
  await page.goto("/tr/kutuphane");
  await expect(page).toHaveURL(/\/tr\/giris\?next=%2Ftr%2Fkutuphane/);
  expect(state.requests.some((request) => request.includes("/library/"))).toBe(false);
});

for (const locale of ["tr", "en"] as const) for (const theme of ["light", "dark"]) for (const width of [375, 768, 1440]) {
  test(`Library görünüm ${locale} ${theme} ${width}`, async ({ page }) => {
    const state = await fixture(page); state.metadataPartial = true;
    await page.setViewportSize({ width, height: 1000 });
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.addInitScript((theme) => { localStorage.setItem("ltz-theme", theme); }, theme);
    await page.goto(locale === "tr" ? "/tr/kutuphane" : "/en/library");
    await expect(page.getByRole("heading", { name: "Library Fixture Game" })).toBeVisible();
    await expect(page.getByRole("main").getByText(locale === "tr" ? "Son bilinen bilgi" : "Last known information", { exact: true })).toBeVisible();
    await expect(page.locator("html")).toHaveAttribute("data-theme", theme);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: `test-results/library-${locale}-${theme}-${width}.png`, fullPage: true });
  });
}

test("URL filtreleri geri/ileri ve dil değişiminde korunur", async ({ page }) => {
  await fixture(page);
  await page.goto("/tr/kutuphane?favorite=false&page=2");
  await expect(page.getByText(/Bu sayfada kayıt yok/)).toBeVisible();
  await page.getByRole("combobox", { name: "Sıralama" }).click();
  await page.getByRole("option", { name: "Oyun adı: A → Z" }).click();
  await expect(page).toHaveURL(/favorite=false&sort=TITLE&direction=ASC/);
  await page.goBack();
  await expect(page).toHaveURL(/page=2/);
  await page.goForward();
  await page.getByRole("button", { name: "Dil: Türkçe" }).click();
  await page.getByRole("menuitemradio", { name: "English" }).click();
  await expect(page).toHaveURL(/\/en\/library\?favorite=false&sort=TITLE&direction=ASC/);
  await expect(page.getByRole("heading", { name: "My Game Library" })).toBeVisible();
});

test("E2E-17 profil dili iki yönde kimlik, query, hash ve temayı korur", async ({ page }) => {
  const state = await fixture(page);
  await page.addInitScript(() => { localStorage.setItem("ltz-theme", "dark"); });
  const suffix = "?favorite=false&sort=TITLE&direction=ASC#main-content";
  await page.goto(`/tr/kutuphane/oyuncu/${owner}${suffix}`);
  await expect(page.getByRole("heading", { name: "Library Fixture Game" })).toBeVisible();
  for (const [label, choice, target, locale] of [
    ["Dil: Türkçe", "English", `/en/library/users/${owner}`, "en"],
    ["Language: English", "Türkçe", `/tr/kutuphane/oyuncu/${owner}`, "tr"]
  ]) {
    await page.getByRole("button", { name: label }).click();
    await page.getByRole("menuitemradio", { name: choice }).click();
    await expect(page).toHaveURL(`${target}${suffix}`);
    await expect(page.locator("html")).toHaveAttribute("lang", locale);
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await expect(page.getByRole("heading", { name: "Library Fixture Game" })).toBeVisible();
  }
  expect(state.requests.filter((request) => !request.startsWith("GET "))).toEqual([]);
});
