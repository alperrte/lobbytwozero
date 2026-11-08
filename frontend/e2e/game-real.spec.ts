import { createHmac } from "node:crypto";
import fs from "node:fs";
import { expect, test, type Browser, type BrowserContext, type Locator, type Page } from "@playwright/test";
import tr from "../src/shared/lib/i18n/messages/tr.json";

// GERÇEK Auth + Gateway + Game + Community yığını gerekir; `node e2e/game-real/run.mjs` ile çalıştırılır.
// Bu dosya fixture KULLANMAZ. Yığın yoksa (varsayılan `npm run test:e2e`) tüm senaryolar atlanır.
type User = { email: string; username: string; password: string; totpSecret?: string; totpStep?: number };
type State = { gateway: string; frontend: string; users: Record<"admin" | "moderator" | "developer" | "player" | "other", User>; seeded: number; externalGameId: string };
const state: State | null = process.env.GAME_REAL_STATE ? JSON.parse(fs.readFileSync(process.env.GAME_REAL_STATE, "utf8")) : null;
test.skip(!state, "GAME_REAL_STATE yok: gerçek yığın `node e2e/game-real/run.mjs` ile hazırlanır");
test.describe.configure({ mode: "serial" });

const t = tr.Game;
const gw = state?.gateway ?? "";
type Role = keyof State["users"];
const sessions = new Map<Role, Awaited<ReturnType<BrowserContext["storageState"]>>>();

/** RFC 6238 TOTP (SHA-1, 6 hane, 30 sn). */
function totp(secret: string, nowMs: number): string {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  const bits = [...secret.replace(/=/g, "").toUpperCase()].map((char) => alphabet.indexOf(char).toString(2).padStart(5, "0")).join("");
  const key = Buffer.from((bits.match(/.{8}/g) ?? []).map((byte) => parseInt(byte, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(nowMs / 30000)));
  const digest = createHmac("sha1", key).update(counter).digest();
  const offset = digest[19] & 0xf;
  return String((digest.readUInt32BE(offset) & 0x7fffffff) % 1000000).padStart(6, "0");
}
const lastTotpStep = new Map<string, number>();
/** Aynı 30 sn penceresindeki kod yeniden kullanılamaz (replay koruması); gerekirse sonraki pencere beklenir. */
async function freshTotp(secret: string, enrolledStep?: number): Promise<string> {
  if (enrolledStep !== undefined && !lastTotpStep.has(secret)) lastTotpStep.set(secret, enrolledStep);
  while (Math.floor(Date.now() / 30000) === lastTotpStep.get(secret)) await new Promise((resolve) => setTimeout(resolve, 1000));
  lastTotpStep.set(secret, Math.floor(Date.now() / 30000));
  return totp(secret, Date.now());
}

/**
 * Gerçek giriş sayfasından oturum açar (Auth çerezleri, oturum ipucu ve CSRF çifti gerçek Gateway'den gelir).
 * Ayrıcalıklı roller için Auth politikası gereği ikinci adımda gerçek TOTP kodu girilir.
 */
async function loginViaUi(page: Page, user: User) {
  await page.goto("/tr/giris");
  await page.getByLabel(tr.Auth.login.identifier, { exact: true }).fill(user.email);
  await page.getByLabel(tr.Auth.login.password, { exact: true }).fill(user.password);
  await page.getByRole("button", { name: tr.Auth.login.submit, exact: true }).click();
  if (user.totpSecret) {
    await page.getByLabel(tr.Auth.mfa.verify.code, { exact: true }).fill(await freshTotp(user.totpSecret, user.totpStep));
    await page.getByRole("button", { name: tr.Auth.mfa.verify.submit, exact: true }).click();
  }
  await page.waitForURL((url) => !url.pathname.endsWith("/giris"), { timeout: 20000 });
}
const pixel = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==", "base64");
async function stubImages(context: BrowserContext) {
  context.on("response", (response) => { if (response.status() === 429) console.log("RATE-LIMITED", response.request().method(), new URL(response.url()).pathname, JSON.stringify(response.headers()["retry-after"] ?? "")); });
  await context.route(/steamstatic.com|images.example/, (route) => route.fulfill({ body: pixel, contentType: "image/png" }));
}
test.beforeEach(async ({ context }) => { await stubImages(context); });
/**
 * Rol başına TEK kalıcı bağlam: access token 60 sn'de dolar ve arayüz refresh token'ı döndürür (rotasyon). Aynı storageState ile
 * yeni bağlam açmak tükenmiş refresh token'ı yeniden kullanmak olur (Auth bunu reddeder), bu yüzden bağlamlar paylaşılır.
 */
const live = new Map<Role, BrowserContext>();
async function openAs(browser: Browser, role: Role, viewport?: { width: number; height: number }) {
  let context = live.get(role);
  if (!context) {
    context = await browser.newContext({ storageState: sessions.get(role), baseURL: state!.frontend, locale: "tr-TR" });
    await stubImages(context);
    live.set(role, context);
  }
  const page = await context.newPage();
  if (viewport) await page.setViewportSize(viewport);
  return { context, page };
}
/** Paylaşılan bağlamlar test bitince kapatılmaz; yalnız sayfaları kapatılır. */
const keep = async (context: BrowserContext) => { await Promise.all(context.pages().map((page) => page.close().catch(() => {}))); };
/** Durum değiştiren Gateway çağrısı: önce CSRF çifti alınır, sonra çerezli istek gönderilir (tarayıcı oturumuyla). */
async function api(context: BrowserContext, method: string, route: string, data?: unknown) {
  await context.request.get(`${gw}/api/v1/auth/csrf`);
  const csrf = (await context.cookies(gw)).find((cookie) => /XSRF-TOKEN$/.test(cookie.name))?.value;
  const response = await context.request.fetch(`${gw}${route}`, { method, headers: { "Content-Type": "application/json", Origin: state!.frontend, ...(csrf && method !== "GET" ? { "X-XSRF-TOKEN": csrf } : {}) }, data });
  const text = await response.text();
  return { status: response.status(), body: text ? safeJson(text) : undefined };
}
function safeJson(text: string) { try { return JSON.parse(text); } catch { return text; } }
async function noOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
}

test.beforeAll(async ({ browser }) => {
  test.setTimeout(240000);
  for (const role of Object.keys(state!.users) as Role[]) {
    const context = await browser.newContext({ baseURL: state!.frontend, locale: "tr-TR" });
    const page = await context.newPage();
    await loginViaUi(page, state!.users[role]);
    sessions.set(role, await context.storageState());
    await context.close();
  }
});

const game = { id: "", title: "Gerçek Test Oyunu" };
const game2 = { id: "", title: "Erken Erişimsiz Oyun" };
let developerAccountId = "";
let feedbackId = "";

test.describe("1 · Oturum ve Auth entegrasyonu (gerçek Auth + Gateway)", () => {
  test("TOTP'li ADMIN girişi sonrası /auth/me rolü ve çerez/CSRF çifti Game ekranına yansır", async ({ browser }) => {
    const { context, page } = await openAs(browser, "admin");
    await page.goto("/tr/oyunlar/yonetim");
    await expect(page.getByRole("tab", { name: t.adminGames, exact: true })).toBeVisible();
    const me = await api(context, "GET", "/api/v1/auth/me");
    expect(me.status).toBe(200);
    expect((me.body as { roles: string[] }).roles).toContain("ADMIN");
    const names = (await context.cookies(gw)).map((cookie) => cookie.name);
    expect(names.some((name) => /ltz_access$/.test(name))).toBe(true);
    expect(names.some((name) => /XSRF-TOKEN$/.test(name))).toBe(true);
    await keep(context);
  });
  test("misafir: /auth/me 401 (oturum ipucu yok) ve Game yönetimi giriş ister", async ({ page }) => {
    await page.goto("/tr/oyunlar/yonetim");
    await expect(page.getByText(t.signInRequired)).toBeVisible();
    expect(await page.evaluate(async (base) => (await fetch(base + "/api/v1/auth/me", { credentials: "include" })).status, gw)).toBe(401);
  });
  test("PLAYER yönetim alanına giremez (arayüz) ve admin/geliştirici uçları 403 döner (sunucu)", async ({ browser }) => {
    const { context, page } = await openAs(browser, "player");
    await page.goto("/tr/oyunlar/yonetim");
    await expect(page.getByText(t.noWorkspaceAccess)).toBeVisible();
    await expect(page.getByRole("form")).toHaveCount(0);
    expect((await api(context, "POST", "/api/v1/game/admin/games", { title: "x", gameType: "MAIN_GAME" })).status).toBe(403);
    expect((await api(context, "GET", "/api/v1/game/developer/games")).status).toBe(403);
    expect((await api(context, "PUT", "/api/v1/game/admin/popular", { gameIds: [], reason: "yetkisiz deneme" })).status).toBe(403);
    await keep(context);
  });
});

test.describe("2 · Herkese açık katalog (gerçek Game API)", () => {
  test("gerçek liste, sayfalama, arama ve sıralama", async ({ page }) => {
    const requests: URL[] = [];
    page.on("request", (request) => { const url = new URL(request.url()); if (url.pathname === "/api/v1/game/games") requests.push(url); });
    await page.goto("/tr/oyunlar");
    await expect(page.getByRole("link", { name: "Seed Oyun 01", exact: true })).toBeVisible();
    await expect(page.getByRole("status").filter({ hasText: /sonuç/ })).toContainText(String(state!.seeded + 1)); // 24 tohum + Manuel-kilit testi için eklenen harici oyun
    expect(await page.locator("main ul li h2").count()).toBe(20);
    await page.getByRole("button", { name: t.next, exact: true }).click();
    await expect(page).toHaveURL(/page=1/);
    await expect(page.getByRole("link", { name: "Seed Oyun 24", exact: true })).toBeVisible();
    await page.goBack();
    await expect(page).not.toHaveURL(/page=1/);
    await page.getByRole("searchbox", { name: t.search, exact: true }).fill("Seed Oyun 07");
    await expect(page.getByRole("link", { name: "Seed Oyun 07", exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "Seed Oyun 01", exact: true })).toHaveCount(0);
    expect(requests.some((url) => url.searchParams.get("q") === "Seed Oyun 07")).toBe(true);
    await noOverflow(page);
  });
  test("sıralama gerçekten uygulanır (Oyun adı azalan)", async ({ page }) => {
    await page.goto("/tr/oyunlar?sort=TITLE&direction=DESC");
    await expect(page.locator("main ul li h2").first()).toHaveText("Seed Oyun 24");
    await page.goto("/tr/oyunlar");
    await expect(page.locator("main ul li h2").first()).toHaveText("Dış Kaynaklı Oyun");
  });
  test("kapaksız kayıtlar yer tutucu; puan UNAVAILABLE olarak gösterilir, 0 olarak değil", async ({ page }) => {
    await page.goto("/tr/oyunlar");
    await expect(page.getByTestId("cover-placeholder")).toHaveCount(20);
    await expect(page.getByText(t.ratingUnavailable)).toHaveCount(20);
    await expect(page.getByText(/^0([,/]|$)/)).toHaveCount(0);
  });
  test("RATING_INTEGRATION_PENDING: ilk Review taraması yokken minRating gerçek 400'ü anlaşılır mesajla gösterilir", async ({ page }) => {
    await page.goto("/tr/oyunlar?minRating=7");
    await expect(page.getByText(t.ratingPending)).toBeVisible();
    await expect(page.locator("main ul li h2")).toHaveCount(0);
    await page.goto("/tr/oyunlar?sort=RATING");
    await expect(page.getByText(t.ratingPending)).toBeVisible();
  });
  test("Editörün Seçtikleri boş liste: sahte oyun yok", async ({ page }) => {
    await page.goto("/tr/oyunlar?tab=picks");
    await expect(page.getByText(t.emptyPicks)).toBeVisible();
    await expect(page.getByText(t.editorsPicksHelp)).toBeVisible();
  });
  test("katalog durumu gerçek uçtan okunur; providerTotal null iken yüzde yok", async ({ page }) => {
    await page.goto("/tr/oyunlar");
    await page.getByRole("button", { name: t.catalogStatus, exact: true }).click();
    await expect(page.getByText(t.statusFields.indexedEntries)).toBeVisible();
    await expect(page.getByText("STEAM", { exact: true })).toBeVisible();
    await expect(page.getByText(t.providerTotalUnknown)).toBeVisible();
    await expect(page.getByText(/%s?d/)).toHaveCount(0);
  });
});

test.describe("3 · Admin oyun oluşturma ve herkese açık detay (gerçek Game API)", () => {
  test("ADMIN arayüzden oyun oluşturur: çoklu değer çipleri, sunucu alanları gönderilmez", async ({ browser }) => {
    const { context, page } = await openAs(browser, "admin");
    await page.goto("/tr/oyunlar/yonetim");
    const form = page.getByRole("form", { name: t.createGame, exact: true });
    await form.getByRole("textbox", { name: t.fields.title, exact: true }).fill(game.title);
    await form.getByRole("textbox", { name: t.fields.description, exact: true }).fill("Gerçek Game servisine yazılan açıklama.");
    await form.getByLabel(t.fields.releaseDate, { exact: true }).fill("2026-03-05");
    await form.getByLabel(t.fields.earlyAccess, { exact: true }).selectOption("true");
    for (const [field, values] of [["developers", ["Studio X"]], ["publishers", ["Pub Y"]], ["languages", ["tr", "en"]], ["genres", ["rpg"]], ["categories", ["single-player"]], ["tags", ["indie"]], ["platforms", ["pc"]]] as const) {
      const input = form.getByRole("textbox", { name: t.fields[field], exact: true });
      for (const value of values) { await input.fill(value); await input.press("Enter"); }
    }
    await form.getByLabel(t.fields.coverUrl, { exact: true }).fill("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/570/header.jpg");
    const created = page.waitForResponse((response) => response.url().endsWith("/api/v1/game/admin/games") && response.request().method() === "POST");
    await form.getByRole("button", { name: t.save, exact: true }).click();
    const response = await created;
    expect(response.status(), `${await response.text()} <= ${response.request().postData()}`).toBe(201);
    const sent = response.request().postDataJSON() as Record<string, unknown>;
    expect(["gameId", "origin", "status", "version"].filter((key) => key in sent)).toEqual([]);
    expect(sent).toMatchObject({ title: game.title, gameType: "MAIN_GAME", earlyAccess: true, developers: ["Studio X"], languages: ["tr", "en"], genres: ["rpg"], platforms: ["pc"] });
    const body = await response.json() as { gameId?: string; game?: { gameId: string } };
    game.id = body.gameId ?? body.game?.gameId ?? "";
    expect(game.id).toMatch(/^[0-9a-f-]{36}$/);
    await expect(page.getByRole("heading", { name: game.title, exact: true })).toBeVisible();
    await expect(page.getByText(/Sürüm \d+/)).toBeVisible();
    await keep(context);
  });

  test("misafir detayda oluşturulan gerçek metadata'yı görür (tür/platform çipleri, erken erişim, tarih, bilinmeyen alanlar)", async ({ page }) => {
    await page.goto(`/tr/oyunlar/${game.id}`);
    await expect(page.getByRole("heading", { level: 1, name: game.title })).toBeVisible();
    await expect(page.getByText(t.earlyAccessBadge).first()).toBeVisible();
    await expect(page.getByText("Studio X").first()).toBeVisible();
    await expect(page.getByText("5 Mar 2026")).toBeVisible();
    await expect(page.getByText("Gerçek Game servisine yazılan açıklama.")).toBeVisible();
    await expect(page.getByRole("link", { name: /rpg/i })).toHaveAttribute("href", /genre=rpg/);
    await expect(page.getByRole("link", { name: /pc/i }).first()).toHaveAttribute("href", /platform=pc/);
    // Dil desteği/sistem gereksinimi verisi yok: uydurulmaz.
    await expect(page.getByRole("table")).toHaveCount(0);
    await expect(page.getByText(t.requirements)).toHaveCount(0);
    // Fiyat: ITAD eşlemesi yok → UNAVAILABLE/NO_OFFER, ücretsiz veya 0 olarak gösterilmez.
    await expect(page.getByRole("region", { name: t.prices })).toBeVisible();
    await expect(page.getByText(/0,00/)).toHaveCount(0);
    await expect(page.getByText(t.priceStates.UNAVAILABLE).or(page.getByText(t.priceStates.NO_OFFER))).toBeVisible();
    await noOverflow(page);
  });

  test("tür çipi katalogda gerçek süzgeci uygular; birleşik filtreler ve tarih aralığı backend'de çalışır", async ({ page }) => {
    await page.goto(`/tr/oyunlar/${game.id}`);
    await page.getByRole("link", { name: /rpg/i }).click();
    await expect(page).toHaveURL(/genre=rpg/);
    await expect(page.getByRole("link", { name: game.title, exact: true })).toBeVisible();
    await expect(page.locator("main ul li h2")).toHaveCount(1);
    await page.goto("/tr/oyunlar?genre=rpg&platform=pc&earlyAccess=true&releasedAfter=2026-01-01&releasedBefore=2026-12-31");
    await expect(page.getByRole("link", { name: game.title, exact: true })).toBeVisible();
    await page.goto("/tr/oyunlar?genre=rpg&releasedBefore=2025-01-01");
    await expect(page.getByText(t.emptyFilters)).toBeVisible();
    await page.goto("/tr/oyunlar?genre=rpg&earlyAccess=false");
    await expect(page.getByText(t.emptyFilters)).toBeVisible();
    await page.goto("/tr/oyunlar?sort=RELEASE_DATE&direction=DESC");
    await expect(page.locator("main ul li h2").first()).toHaveText(game.title);
  });

  test("TR/EN dil değişimi gerçek veriyle aynı oyun kimliğini korur", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(`/tr/oyunlar/${game.id}`);
    await page.getByRole("button", { name: "Dil: Türkçe" }).click();
    await page.getByRole("menuitemradio", { name: "English" }).click();
    await expect(page).toHaveURL(new RegExp(`/en/games/${game.id}$`));
    await expect(page.getByRole("heading", { level: 1, name: game.title })).toBeVisible();
    await expect(page.getByRole("navigation", { name: "Page sections" })).toBeVisible();
  });
});

test.describe("4 · Oyuncu geri bildirimi (gerçek Auth + Game; IDOR ve EARLY_ACCESS kuralı)", () => {
  test("PLAYER oyun detayından geri bildirim gönderir ve kendi listesinde görür", async ({ browser }) => {
    const { context, page } = await openAs(browser, "player");
    const admin = await openAs(browser, "admin");
    const created = await api(admin.context, "POST", "/api/v1/game/admin/games", { title: game2.title, gameType: "MAIN_GAME", earlyAccess: false });
    expect(created.status).toBe(201);
    game2.id = (created.body as { gameId: string }).gameId;
    await keep(admin.context);
    await page.goto(`/tr/oyunlar/${game.id}`);
    const form = page.getByRole("form", { name: t.sendFeedback, exact: true });
    await expect(form.getByRole("option", { name: t.choices.EARLY_ACCESS })).toBeAttached();
    await form.getByLabel(t.fields.type, { exact: true }).selectOption("BUG_REPORT");
    await form.getByRole("textbox", { name: t.fields.title, exact: true }).fill("Başlangıçta çöküyor");
    await form.getByRole("textbox", { name: t.fields.body, exact: true }).fill("Ana menüden oyuna girerken uygulama kapanıyor.");
    const post = page.waitForResponse((response) => response.url().endsWith("/api/v1/game/feedback") && response.request().method() === "POST");
    await form.getByRole("button", { name: t.save, exact: true }).click();
    const response = await post;
    expect(response.status()).toBe(201);
    expect(response.request().headers()["x-xsrf-token"]).toBeTruthy();
    feedbackId = ((await response.json()) as { id: string }).id;
    await expect(page.getByRole("heading", { name: "Başlangıçta çöküyor", exact: true })).toBeVisible();
    await expect(page.getByText(t.choices.OPEN).first()).toBeVisible();
    await keep(context);
  });

  test("Geri bildirimlerim sekmesi: oyun adı, durum akışı, tekil detay ve tür/durum süzgeci", async ({ browser }) => {
    const { context, page } = await openAs(browser, "player");
    const requests: URL[] = [];
    page.on("request", (request) => { const url = new URL(request.url()); if (url.pathname.startsWith("/api/v1/game/feedback")) requests.push(url); });
    await page.goto("/tr/oyunlar?tab=feedback");
    await expect(page.getByRole("heading", { name: "Başlangıçta çöküyor", exact: true })).toBeVisible();
    await expect(page.getByText(game.title).first()).toBeVisible();
    await expect(page.getByRole("button", { name: t.startFeedback })).toHaveCount(0);
    await expect(page.getByRole("list", { name: t.fields.status }).getByRole("listitem").filter({ hasText: t.choices.OPEN })).toHaveAttribute("aria-current", "step");
    await page.getByRole("button", { name: t.showDetail }).click();
    await expect(page.getByText("Ana menüden oyuna girerken uygulama kapanıyor.")).toBeVisible();
    expect(requests.some((url) => url.pathname === `/api/v1/game/feedback/${feedbackId}`)).toBe(true);
    await page.getByRole("combobox", { name: t.fields.type, exact: true }).selectOption("GENERAL");
    await expect(page.getByText(t.emptyFeedback)).toBeVisible();
    await page.getByRole("combobox", { name: t.fields.type, exact: true }).selectOption("BUG_REPORT");
    await page.getByRole("combobox", { name: t.fields.status, exact: true }).selectOption("RESOLVED");
    await expect(page.getByText(t.emptyFeedback)).toBeVisible();
    await page.getByRole("combobox", { name: t.fields.status, exact: true }).selectOption("OPEN");
    await expect(page.getByRole("heading", { name: "Başlangıçta çöküyor", exact: true })).toBeVisible();
    expect(requests.some((url) => url.searchParams.get("type") === "BUG_REPORT" && url.searchParams.get("status") === "OPEN")).toBe(true);
    await noOverflow(page);
    await keep(context);
  });

  test("başka oyuncu özel geri bildirimi göremez (IDOR): liste boş, tekil okuma 404, misafir 401", async ({ browser, page }) => {
    const other = await openAs(browser, "other");
    await other.page.goto("/tr/oyunlar?tab=feedback");
    await expect(other.page.getByText(t.emptyFeedback)).toBeVisible();
    expect((await api(other.context, "GET", `/api/v1/game/feedback/${feedbackId}`)).status).toBe(404);
    const list = await api(other.context, "GET", `/api/v1/game/feedback?gameId=${game.id}`);
    expect((list.body as { totalElements: number }).totalElements).toBe(0);
    await keep(other.context);
    await page.goto("/tr/oyunlar?tab=feedback");
    await expect(page.getByText(t.feedbackSignIn)).toBeVisible();
    expect(await page.evaluate(async (base) => (await fetch(`${base}/api/v1/game/feedback`, { credentials: "include" })).status, gw)).toBe(401);
  });

  test("EARLY_ACCESS yalnız erken erişimdeki oyunda: arayüz seçeneği sunmaz, sunucu GAME_NOT_EARLY_ACCESS ile reddeder", async ({ browser }) => {
    const { context, page } = await openAs(browser, "player");
    await page.goto("/tr/oyunlar?tab=feedback");
    await page.getByRole("searchbox", { name: t.feedbackPickGame, exact: true }).fill(game2.title);
    await page.getByRole("button", { name: new RegExp(game2.title) }).click();
    const form = page.getByRole("form", { name: t.sendFeedback, exact: true });
    await expect(form).toBeVisible();
    await expect(form.getByRole("option", { name: t.choices.EARLY_ACCESS })).toHaveCount(0);
    const forced = await api(context, "POST", "/api/v1/game/feedback", { gameId: game2.id, type: "EARLY_ACCESS", title: "Zorla", body: "Erken erişim değil" });
    expect(forced.status).toBe(400);
    expect((forced.body as { code?: string }).code).toBe("GAME_NOT_EARLY_ACCESS");
    await keep(context);
  });

  test("PLAYER geri bildirim durumunu değiştiremez (sunucu 403)", async ({ browser }) => {
    const { context } = await openAs(browser, "player");
    const attempt = await api(context, "PATCH", `/api/v1/game/developer/games/${game.id}/feedback/${feedbackId}/status`, { status: "IN_PROGRESS", version: 0 });
    expect(attempt.status).toBe(403);
    await keep(context);
  });
});

test.describe("5 · Admin yönetimi (gerçek Auth + Game; izole geçici veri)", () => {
  async function openRecord(browser: Browser) {
    const session = await openAs(browser, "admin");
    await session.page.goto(`/tr/oyunlar/yonetim?gameId=${game.id}`);
    await expect(session.page.getByRole("heading", { name: game.title, exact: true })).toBeVisible();
    return session;
  }
  const reasonOf = (form: Locator) => form.getByRole("textbox", { name: t.fields.reason, exact: true });

  test("sürüm çakışması: 409 gerçek sunucudan gelir, taslak korunur; güncel kaydı yükleyip yeniden kaydeder", async ({ browser }) => {
    const a = await openRecord(browser), b = await openRecord(browser);
    const edit = (page: Page) => page.getByRole("form", { name: t.editGame, exact: true });
    await edit(b.page).getByRole("textbox", { name: t.fields.description, exact: true }).fill("Açıklama B (ilk kaydeden)");
    await edit(b.page).getByRole("button", { name: t.save, exact: true }).click();
    await expect(b.page.getByRole("status").filter({ hasText: t.saved })).toBeVisible();
    await edit(a.page).getByRole("textbox", { name: t.fields.description, exact: true }).fill("Açıklama A (gecikmiş kayıt)");
    await edit(a.page).getByRole("button", { name: t.save, exact: true }).click();
    await expect(edit(a.page).getByRole("alert")).toContainText(t.errors["409"]);
    await expect(edit(a.page).getByRole("alert")).toContainText(t.conflictDraftKept);
    await expect(edit(a.page).getByRole("textbox", { name: t.fields.description, exact: true })).toHaveValue("Açıklama A (gecikmiş kayıt)");
    await a.page.getByRole("button", { name: t.reload, exact: true }).click();
    await expect(edit(a.page).getByRole("textbox", { name: t.fields.description, exact: true })).toHaveValue("Açıklama B (ilk kaydeden)");
    await edit(a.page).getByRole("textbox", { name: t.fields.description, exact: true }).fill("Açıklama A (yeniden uygulandı)");
    await edit(a.page).getByRole("button", { name: t.save, exact: true }).click();
    await expect(a.page.getByRole("status").filter({ hasText: t.saved })).toBeVisible();
    await keep(a.context); await keep(b.context);
    await expect.poll(async () => ((await (await fetch(`${gw}/api/v1/game/games/${game.id}`)).json()) as { description: string }).description).toBe("Açıklama A (yeniden uygulandı)");
  });

  test("harici kökenli oyunda yönetici düzeltmesi alanı manuel korur; gerekçe + onayla serbest bırakılır", async ({ browser }) => {
    const { context, page } = await openAs(browser, "admin");
    await page.goto(`/tr/oyunlar/yonetim?gameId=${state!.externalGameId}`);
    await expect(page.getByRole("heading", { name: "Dış Kaynaklı Oyun", exact: true })).toBeVisible();
    await expect(page.getByText(t.noOverrides)).toBeVisible();
    const edit = page.getByRole("form", { name: t.editGame, exact: true });
    await edit.getByRole("textbox", { name: t.fields.title, exact: true }).fill("Dış Kaynaklı Oyun (düzeltildi)");
    await edit.getByRole("button", { name: t.save, exact: true }).click();
    await expect(page.getByRole("status").filter({ hasText: t.saved })).toBeVisible();
    const release = page.getByRole("form", { name: new RegExp(`^${t.releaseOverride}`) }).first();
    await expect(release).toBeVisible();
    await expect(release).toHaveAccessibleName(new RegExp(t.overrideFields.TITLE));
    await reasonOf(release).fill("Sağlayıcı verisi yeniden kullanılacak");
    await expect(release.getByRole("button", { name: t.save, exact: true })).toBeDisabled();
    await release.getByRole("checkbox", { name: t.confirmAction }).check();
    await release.getByRole("button", { name: t.save, exact: true }).click();
    await expect(page.getByText(t.noOverrides)).toBeVisible();
    await keep(context);
  });

  test("arşivle → katalogdan kalkar (detay 404); geri al → yeniden listelenir", async ({ browser, page }) => {
    const { context, page: admin } = await openRecord(browser);
    await admin.getByRole("tab", { name: t.lifecycle, exact: true }).click();
    const archive = admin.getByRole("form", { name: t.archiveGame, exact: true });
    await expect(archive.getByRole("button", { name: t.save, exact: true })).toBeDisabled();
    await reasonOf(archive).fill("E2E arşivleme sınaması");
    await archive.getByRole("checkbox", { name: t.confirmAction }).check();
    await archive.getByRole("button", { name: t.save, exact: true }).click();
    await expect(admin.getByText(t.choices.ARCHIVED, { exact: true })).toBeVisible();
    await page.goto(`/tr/oyunlar?q=${encodeURIComponent(game.title)}`);
    await expect(page.getByText(t.emptySearch.replace("{query}", game.title))).toBeVisible();
    await page.goto(`/tr/oyunlar/${game.id}`);
    await expect(page.getByText(t.errors["404"])).toBeVisible();
    const restore = admin.getByRole("form", { name: t.restoreGame, exact: true });
    await reasonOf(restore).fill("E2E geri alma sınaması");
    await restore.getByRole("checkbox", { name: t.confirmAction }).check();
    await restore.getByRole("button", { name: t.save, exact: true }).click();
    await expect(admin.getByText(t.choices.ACTIVE, { exact: true })).toBeVisible();
    await page.goto(`/tr/oyunlar?q=${encodeURIComponent(game.title)}`);
    await expect(page.getByRole("link", { name: game.title, exact: true })).toBeVisible();
    await keep(context);
  });

  test("geliştirici yetkisi verilir ve listelenir (rol ile oyun bazlı yetki ayrı)", async ({ browser }) => {
    const dev = await openAs(browser, "developer");
    developerAccountId = ((await api(dev.context, "GET", "/api/v1/auth/me")).body as { accountId: string }).accountId;
    await keep(dev.context);
    const { context, page } = await openRecord(browser);
    await page.getByRole("tab", { name: t.developerGrants, exact: true }).click();
    await expect(page.getByText(t.noGrants)).toBeVisible();
    const grant = page.getByRole("form", { name: t.grantDeveloper, exact: true });
    await grant.getByRole("textbox", { name: t.fields.developerUserId, exact: true }).fill(developerAccountId);
    await reasonOf(grant).fill("E2E geliştirici yetkisi");
    await grant.getByRole("button", { name: t.save, exact: true }).click();
    await expect(page.getByText(developerAccountId)).toBeVisible();
    await keep(context);
  });

  test("başarım tanımı oluştur → düzenle → gizle; herkese açık modül yalnız görünür olanı gösterir", async ({ browser, page }) => {
    const { context, page: admin } = await openRecord(browser);
    await admin.getByRole("tab", { name: t.manageAchievements, exact: true }).click();
    const create = admin.getByRole("form", { name: t.addAchievement, exact: true });
    await create.getByRole("textbox", { name: t.fields.code, exact: true }).fill("ilk-galibiyet");
    await create.getByRole("textbox", { name: t.fields.title, exact: true }).fill("İlk galibiyet");
    await create.getByRole("textbox", { name: t.fields.description, exact: true }).fill("İlk oyununu kazan.");
    await reasonOf(create).fill("E2E başarım tanımı");
    await create.getByRole("button", { name: t.save, exact: true }).click();
    await expect(admin.getByText(`İlk galibiyet · ${t.visible}`)).toBeVisible();
    await page.goto(`/tr/oyunlar/${game.id}`);
    await page.getByRole("button", { name: t.types.ACHIEVEMENTS, exact: true }).click();
    await expect(page.getByRole("heading", { name: "İlk galibiyet", exact: true })).toBeVisible();
    // Başarım tanımı oyuncunun kazandığı başarım değildir: kazanma/ilerleme alanı yok.
    await expect(page.getByText(/ilerleme|kazandın/i)).toHaveCount(0);
    await admin.getByRole("button", { name: t.edit, exact: true }).first().click();
    const editForm = admin.getByRole("form", { name: t.editAchievement, exact: true });
    await editForm.getByLabel(t.fields.visible, { exact: true }).selectOption("false");
    await reasonOf(editForm).fill("E2E başarımı gizle");
    await editForm.getByRole("button", { name: t.save, exact: true }).click();
    await expect(admin.getByText(`İlk galibiyet · ${t.hidden}`)).toBeVisible();
    await page.goto(`/tr/oyunlar/${game.id}`);
    await page.getByRole("button", { name: t.types.ACHIEVEMENTS, exact: true }).click();
    await expect(page.getByRole("heading", { name: "İlk galibiyet", exact: true })).toHaveCount(0);
    await keep(context);
  });

  test("editör listesi: oyun ekle, gerekçeyle kaydet → Editörün Seçtikleri'nde görünür; boş liste temizler", async ({ browser, page }) => {
    const { context, page: admin } = await openAs(browser, "admin");
    await admin.goto("/tr/oyunlar/yonetim");
    await admin.getByRole("tab", { name: t.popularSelection, exact: true }).click();
    await expect(admin.getByText(t.popularEmptyList)).toBeVisible();
    await admin.getByRole("searchbox", { name: t.popularAdd, exact: true }).fill(game.title);
    await admin.getByRole("button", { name: new RegExp(`^${game.title}\\s*${t.pickerSelect}$`) }).click();
    const form = admin.getByRole("form", { name: t.popularSave, exact: true });
    await reasonOf(form).fill("E2E editör seçimi");
    await form.getByRole("checkbox", { name: t.confirmAction }).check();
    await form.getByRole("button", { name: t.save, exact: true }).click();
    await expect(admin.getByRole("status").filter({ hasText: t.saved })).toBeVisible();
    await page.goto("/tr/oyunlar?tab=picks");
    await expect(page.getByRole("link", { name: game.title, exact: true })).toBeVisible();
    await expect(page.getByText(t.editorRank.replace("{rank}", "1"))).toBeVisible();
    await page.goto("/tr/oyunlar?popularOnly=true");
    await expect(page.getByRole("link", { name: game.title, exact: true })).toBeVisible();
    await admin.reload();
    await admin.getByRole("tab", { name: t.popularSelection, exact: true }).click();
    await expect(admin.getByText(game.title, { exact: true })).toBeVisible();
    await admin.getByRole("button", { name: t.removeFromList.replace("{title}", game.title) }).click();
    await expect(admin.getByText(t.popularClearWarning)).toBeVisible();
    const clear = admin.getByRole("form", { name: t.popularSave, exact: true });
    await reasonOf(clear).fill("E2E editör listesini temizle");
    await clear.getByRole("checkbox", { name: t.confirmAction }).check();
    await clear.getByRole("button", { name: t.save, exact: true }).click();
    await expect(admin.getByRole("status").filter({ hasText: t.saved })).toBeVisible();
    await page.goto("/tr/oyunlar?tab=picks");
    await expect(page.getByText(t.emptyPicks)).toBeVisible();
    await keep(context);
  });

  test("katalog durumu ve dış kimlik bakım alanı: sahte düğme yok", async ({ browser }) => {
    const { context, page } = await openAs(browser, "admin");
    await page.goto("/tr/oyunlar/yonetim");
    await page.getByRole("tab", { name: t.catalogStatus, exact: true }).click();
    await expect(page.getByText(t.statusFields.mainGameEntries)).toBeVisible();
    await page.getByRole("tab", { name: t.identityTitle, exact: true }).click();
    await expect(page.getByText(t.identityBlockedBody)).toBeVisible();
    await expect(page.getByRole("tabpanel").getByRole("button")).toHaveCount(0);
    await expect(page.getByRole("tabpanel").getByRole("form")).toHaveCount(0);
    await keep(context);
  });
});

const steam = {
  image: "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/570/ss_e2e.jpg",
  video: "https://www.youtube.com/watch?v=e2eVideo01",
  demo: "https://store.steampowered.com/app/570/"
};
const mediaIds: { image: string; video: string } = { image: "", video: "" };
let noteId = "";

test.describe("6 · Geliştirici yönetimi (gerçek GAME_DEVELOPER + oyun bazlı yetki)", () => {
  async function openDeveloper(browser: Browser) {
    const session = await openAs(browser, "developer");
    await session.page.goto("/tr/oyunlar/yonetim");
    await expect(session.page.getByRole("combobox", { name: t.yourGames, exact: true })).toBeVisible();
    return session;
  }

  test("yalnız yetkili oyunlar listelenir; yetkisiz oyunun içeriğine erişim reddedilir", async ({ browser }) => {
    const { context, page } = await openDeveloper(browser);
    const options = await page.getByRole("combobox", { name: t.yourGames, exact: true }).locator("option").allTextContents();
    expect(options).toEqual([game.title]);
    const own = await api(context, "GET", "/api/v1/game/developer/games");
    expect((own.body as { gameId: string }[]).map((value) => value.gameId)).toEqual([game.id]);
    expect([403, 404]).toContain((await api(context, "GET", `/api/v1/game/developer/games/${game2.id}/content`)).status);
    expect([403, 404]).toContain((await api(context, "PUT", `/api/v1/game/developer/games/${game2.id}/description`, { description: "Yetkisiz" })).status);
    await keep(context);
  });

  test("açıklama güncellenir ve herkese açık detayda geliştirici etiketiyle görünür", async ({ browser, page }) => {
    const { context, page: dev } = await openDeveloper(browser);
    const form = dev.getByRole("form", { name: t.editDeveloperDescription, exact: true });
    await form.getByRole("textbox", { name: t.fields.description, exact: true }).fill("Geliştiriciden gelen resmi açıklama.");
    await form.getByRole("button", { name: t.save, exact: true }).click();
    await expect(dev.getByRole("status").filter({ hasText: t.saved }).first()).toBeVisible();
    await page.goto(`/tr/oyunlar/${game.id}`);
    const section = page.getByRole("region", { name: new RegExp(t.developerContent) });
    await expect(section.getByText("Geliştiriciden gelen resmi açıklama.")).toBeVisible();
    await expect(section.getByText(t.providedByDeveloper)).toBeVisible();
    await keep(context);
  });

  test("medya: yalnız izinli kaynaklar kabul edilir (INVALID_MEDIA_URL mesajı); görsel/video/demo eklenir", async ({ browser }) => {
    const { context, page } = await openDeveloper(browser);
    await page.getByRole("tab", { name: t.tabs.media, exact: true }).click();
    const form = page.getByRole("form", { name: t.addMedia, exact: true });
    await expect(form.getByText(t.mediaSources.all)).toBeVisible();
    await form.getByRole("textbox", { name: t.fields.url, exact: true }).fill("https://evil.example/pic.png");
    await form.getByRole("button", { name: t.save, exact: true }).click();
    await expect(form.getByRole("alert")).toContainText(t.invalidMediaUrl);
    const add = async (type: string, url: string, alt?: string) => {
      const created = page.waitForResponse((response) => response.url().endsWith("/developer/games/" + game.id + "/media") && response.request().method() === "POST");
      await form.getByLabel(t.fields.type, { exact: true }).selectOption(type);
      await form.getByRole("textbox", { name: t.fields.url, exact: true }).fill(url);
      if (alt) await form.getByRole("textbox", { name: t.fields.altText, exact: true }).fill(alt);
      await form.getByRole("button", { name: t.save, exact: true }).click();
      const response = await created;
      expect(response.status()).toBe(201);
      return ((await response.json()) as { id: string }).id;
    };
    mediaIds.image = await add("IMAGE", steam.image, "Oyun ekran görüntüsü");
    mediaIds.video = await add("VIDEO", steam.video);
    await add("DEMO", steam.demo);
    await expect(page.getByText("Oyun ekran görüntüsü", { exact: true }).first()).toBeVisible();
    await expect(page.getByRole("link", { name: t.openVideo })).toHaveAttribute("href", steam.video);
    await expect(page.getByRole("link", { name: t.openDemo })).toHaveAttribute("href", steam.demo);
    await keep(context);
  });

  test("güncelleme notu oluştur → düzenle (sürümlü) → herkese açık görünür → sil", async ({ browser, page }) => {
    const { context, page: dev } = await openDeveloper(browser);
    await dev.getByRole("tab", { name: t.tabs.notes, exact: true }).click();
    const create = dev.getByRole("form", { name: t.addNote, exact: true });
    await create.getByRole("textbox", { name: t.fields.title, exact: true }).fill("1.0 yaması");
    await create.getByRole("textbox", { name: t.fields.body, exact: true }).fill("Çökme hataları giderildi.");
    const created = dev.waitForResponse((response) => response.url().endsWith("/update-notes") && response.request().method() === "POST");
    await create.getByRole("button", { name: t.save, exact: true }).click();
    noteId = ((await (await created).json()) as { id: string }).id;
    await expect(dev.getByRole("heading", { name: "1.0 yaması", exact: true })).toBeVisible();
    await dev.getByRole("button", { name: t.edit, exact: true }).first().click();
    const edit = dev.getByRole("form", { name: t.editNote, exact: true });
    await edit.getByRole("textbox", { name: t.fields.title, exact: true }).fill("1.0.1 yaması");
    const updated = dev.waitForResponse((response) => response.url().endsWith(`/update-notes/${noteId}`) && response.request().method() === "PUT");
    await edit.getByRole("button", { name: t.save, exact: true }).click();
    expect(((await updated).request().postDataJSON() as { version?: number }).version).toBeGreaterThanOrEqual(0);
    await expect(dev.getByRole("heading", { name: "1.0.1 yaması", exact: true })).toBeVisible();
    await page.goto(`/tr/oyunlar/${game.id}`);
    await expect(page.getByRole("heading", { name: "1.0.1 yaması", exact: true })).toBeVisible();
    await keep(context);
  });

  test("geri bildirim kutusu: oyuncunun kaydı görünür; OPEN → IN_PROGRESS → RESOLVED; çözülünce başka geçiş yok", async ({ browser }) => {
    const { context, page } = await openDeveloper(browser);
    await page.getByRole("tab", { name: t.receivedFeedback, exact: true }).click();
    await expect(page.getByRole("heading", { name: "Başlangıçta çöküyor", exact: true })).toBeVisible();
    await page.getByRole("button", { name: t.showDetail }).click();
    await expect(page.getByText("Ana menüden oyuna girerken uygulama kapanıyor.")).toBeVisible();
    await page.getByRole("button", { name: t.startFeedback, exact: true }).click();
    await expect(page.getByRole("list", { name: t.fields.status }).getByRole("listitem").filter({ hasText: t.choices.IN_PROGRESS })).toHaveAttribute("aria-current", "step");
    await page.getByRole("button", { name: t.resolveFeedback, exact: true }).click();
    await expect(page.getByRole("list", { name: t.fields.status }).getByRole("listitem").filter({ hasText: t.choices.RESOLVED })).toHaveAttribute("aria-current", "step");
    await expect(page.getByRole("button", { name: t.startFeedback })).toHaveCount(0);
    await expect(page.getByRole("button", { name: t.resolveFeedback })).toHaveCount(0);
    await keep(context);
    const player = await openAs(browser, "player");
    await player.page.goto("/tr/oyunlar?tab=feedback");
    await expect(player.page.getByRole("list", { name: t.fields.status }).getByRole("listitem").filter({ hasText: t.choices.RESOLVED })).toHaveAttribute("aria-current", "step");
    await keep(player.context);
  });

  test("MODERATOR tek başına geliştirici uçlarına erişemez (rol ≠ yetki)", async ({ browser }) => {
    const { context } = await openAs(browser, "moderator");
    expect((await api(context, "GET", "/api/v1/game/developer/games")).status).toBe(403);
    expect([403, 404]).toContain((await api(context, "GET", `/api/v1/game/developer/games/${game.id}/content`)).status);
    await keep(context);
  });
});

test.describe("7 · Moderasyon (gerçek MODERATOR; gizle/geri al)", () => {
  async function openModeration(browser: Browser) {
    const session = await openAs(browser, "moderator");
    await session.page.goto("/tr/oyunlar/yonetim");
    await session.page.getByRole("searchbox", { name: t.pickerLabel, exact: true }).fill(game.title);
    await session.page.getByRole("button", { name: new RegExp(`^${game.title}\\s*${t.pickerSelect}$`) }).click();
    await expect(session.page.getByText(t.moderationVisibleHelp)).toBeVisible();
    return session;
  }
  const publicContent = async () => (await (await fetch(`${gw}/api/v1/game/games/${game.id}/developer-content`)).json()) as { description: { text: string } | null; media: { id: string }[]; updateNotes: { id: string }[] };
  const moderate = async (page: Page, target: string, action: string, reason: string, targetId?: string) => {
    const form = page.getByRole("form", { name: t.moderateContent, exact: true });
    await form.getByLabel(t.fields.target, { exact: true }).selectOption(target);
    await form.getByLabel(t.fields.action, { exact: true }).selectOption(action);
    if (targetId) await form.getByRole("textbox", { name: t.fields.targetId, exact: true }).fill(targetId);
    await form.getByRole("textbox", { name: t.fields.reason, exact: true }).fill(reason);
    await form.getByRole("checkbox", { name: t.confirmAction }).check();
    const sent = page.waitForResponse((response) => response.url().includes("/moderation/games/") && response.request().method() === "POST");
    await form.getByRole("button", { name: t.save, exact: true }).click();
    const response = await sent;
    expect(response.status()).toBe(200);
    return response.request().postDataJSON() as Record<string, unknown>;
  };

  test("açıklama gizle (targetId gönderilmez) → herkese açık görünümden çıkar; geliştirici gerekçeyi görür ve düzenleyemez; geri al", async ({ browser }) => {
    const { context, page } = await openModeration(browser);
    const body = await moderate(page, "DESCRIPTION", "HIDE", "E2E uygunsuz açıklama");
    expect(body).toEqual({ target: "DESCRIPTION", action: "HIDE", reason: "E2E uygunsuz açıklama" });
    expect((await publicContent()).description).toBeNull();
    const dev = await openAs(browser, "developer");
    await dev.page.goto("/tr/oyunlar/yonetim");
    await expect(dev.page.getByText(t.hiddenEditBlocked)).toBeVisible();
    await expect(dev.page.getByText(/E2E uygunsuz açıklama/)).toBeVisible();
    await expect(dev.page.getByRole("form", { name: t.editDeveloperDescription, exact: true }).getByRole("button", { name: t.save, exact: true })).toBeDisabled();
    await keep(dev.context);
    await moderate(page, "DESCRIPTION", "RESTORE", "E2E açıklamayı geri yayımla");
    expect((await publicContent()).description?.text).toBe("Geliştiriciden gelen resmi açıklama.");
    await keep(context);
  });

  test("medya ve güncelleme notu: görünür listeden seçilerek gizlenir; geri alma kimlikle yapılır", async ({ browser }) => {
    const dev = await openAs(browser, "developer");
    const content = (await api(dev.context, "GET", `/api/v1/game/developer/games/${game.id}/content`)).body as { media: { id: string; type: string }[]; updateNotes: { id: string }[] };
    await keep(dev.context);
    const imageId = content.media.find((value) => value.type === "IMAGE")!.id, noteIdNow = content.updateNotes[0].id;
    const { context, page } = await openModeration(browser);
    // Görünür medyayı listeden seç → hedef türü ve kimliği forma taşınır.
    await page.getByRole("listitem").filter({ hasText: "Oyun ekran görüntüsü" }).getByRole("button", { name: t.pickerSelect }).click();
    const form = page.getByRole("form", { name: t.moderateContent, exact: true });
    await expect(form.getByLabel(t.fields.target, { exact: true })).toHaveValue("MEDIA");
    await expect(form.getByRole("textbox", { name: t.fields.targetId, exact: true })).toHaveValue(imageId);
    const hide = await moderate(page, "MEDIA", "HIDE", "E2E uygunsuz medya");
    expect(hide).toMatchObject({ target: "MEDIA", targetId: imageId, action: "HIDE" });
    expect((await publicContent()).media.map((value) => value.id)).not.toContain(imageId);
    await moderate(page, "MEDIA", "RESTORE", "E2E medyayı geri yayımla", imageId);
    expect((await publicContent()).media.map((value) => value.id)).toContain(imageId);
    await moderate(page, "UPDATE_NOTE", "HIDE", "E2E uygunsuz not", noteIdNow);
    expect((await publicContent()).updateNotes.map((value) => value.id)).not.toContain(noteIdNow);
    await moderate(page, "UPDATE_NOTE", "RESTORE", "E2E notu geri yayımla", noteIdNow);
    expect((await publicContent()).updateNotes.map((value) => value.id)).toContain(noteIdNow);
    await keep(context);
  });

  test("yetki sınırları: PLAYER moderasyon yapamaz (403); MODERATOR admin işlemi yapamaz (403); sahte kuyruk yok", async ({ browser }) => {
    const player = await openAs(browser, "player");
    expect((await api(player.context, "POST", `/api/v1/game/moderation/games/${game.id}/developer-content/actions`, { target: "DESCRIPTION", action: "HIDE", reason: "yetkisiz deneme" })).status).toBe(403);
    await keep(player.context);
    const { context, page } = await openAs(browser, "moderator");
    expect((await api(context, "POST", "/api/v1/game/admin/games", { title: "Yetkisiz", gameType: "MAIN_GAME" })).status).toBe(403);
    await page.goto("/tr/oyunlar/yonetim");
    await expect(page.getByRole("tab", { name: t.adminGames, exact: true })).toHaveCount(0);
    await expect(page.getByRole("table")).toHaveCount(0);
    await keep(context);
  });
});

test.describe("8 · Community modülleri (contractVersion=2, gerçek Community)", () => {
  test("GUIDES Community'den okunur (EXTERNAL); detay düz metin; diğer türler boş koleksiyon", async ({ browser, page }) => {
    const { context } = await openAs(browser, "player");
    const created = await api(context, "POST", "/api/v1/community/posts", { gameId: game.id, contentType: "GUIDES", title: "Başlangıç rehberi", body: "<script>window.__ugc = true</script> İlk adımlar." });
    expect(created.status, JSON.stringify(created.body)).toBe(201);
    await keep(context);
    const modules = page.waitForResponse((response) => response.url().includes(`/games/${game.id}/modules/GUIDES`));
    await page.goto(`/tr/oyunlar/${game.id}`);
    await page.getByRole("button", { name: t.types.GUIDES, exact: true }).click();
    const descriptor = (await (await modules).json()) as { state: string; contentSource: { service: string; listPath: string } };
    expect(descriptor.state).toBe("EXTERNAL");
    expect(descriptor.contentSource.service).toBe("COMMUNITY");
    await page.getByRole("button", { name: "Başlangıç rehberi", exact: true }).click();
    await expect(page.getByText("<script>window.__ugc = true</script> İlk adımlar.")).toBeVisible();
    expect(await page.evaluate(() => "__ugc" in window)).toBe(false);
    await page.getByRole("button", { name: t.backToContent, exact: true }).click();
    for (const type of ["EASTER_EGGS", "EXPERIENCES"] as const) {
      await page.getByRole("button", { name: t.types[type], exact: true }).click();
      await expect(page.getByText(t.emptyModule)).toBeVisible();
    }
  });
});

test.describe("9 · Oturum süresi (kısa ömürlü access token + otomatik yenileme)", () => {
  test("access token süresi dolunca 401 → /session/refresh → istek tekrarlanır; çıkıştan sonra oturum yok", async ({ browser }) => {
    test.setTimeout(180000);
    const { context, page } = await openAs(browser, "other");
    await page.goto("/tr/oyunlar?tab=feedback");
    await expect(page.getByText(t.emptyFeedback)).toBeVisible();
    const calls: string[] = [];
    page.on("response", (response) => { const path = new URL(response.url()).pathname; if (path.startsWith("/api/v1/auth/session") || path === "/api/v1/game/feedback") calls.push(`${response.request().method()} ${path} ${response.status()}`); });
    await page.waitForTimeout(65000);
    await page.getByRole("button", { name: t.reload, exact: true }).click();
    await expect(page.getByText(t.emptyFeedback)).toBeVisible();
    expect(calls).toEqual(expect.arrayContaining(["GET /api/v1/game/feedback 401", "POST /api/v1/auth/session/refresh 200", "GET /api/v1/game/feedback 200"]));
    expect((await api(context, "POST", "/api/v1/auth/session/logout")).status).toBeLessThan(300);
    await page.goto("/tr/oyunlar?tab=feedback");
    await expect(page.getByText(t.feedbackSignIn)).toBeVisible();
    await keep(context);
  });
});

const axeSource = fs.readFileSync(require.resolve("axe-core/axe.min.js"), "utf8");
type AxeViolation = { id: string; impact: string | null; help: string; nodes: { target: string[]; failureSummary?: string }[] };
/** Yalnız <main> (Game içeriği) taranır; kabuk (navbar/sidebar/footer) ortak altyapıdır ve bu denetimin kapsamı dışındadır. */
async function axeAudit(page: Page): Promise<AxeViolation[]> {
  await page.addScriptTag({ content: axeSource });
  return page.evaluate(async () => {
    const axe = (window as unknown as { axe: { run: (context: unknown, options: unknown) => Promise<{ violations: AxeViolation[] }> } }).axe;
    const result = await axe.run({ include: [["main"]] }, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"] } });
    return result.violations.map((violation) => ({ id: violation.id, impact: violation.impact, help: violation.help, nodes: violation.nodes.map((node) => ({ target: node.target as string[], failureSummary: node.failureSummary?.slice(0, 160) })) }));
  });
}
const describeViolations = (violations: AxeViolation[]) => violations.map((value) => `${value.id} [${value.impact}] ${value.help}: ${value.nodes.slice(0, 3).map((node) => node.target.join(" ") + " — " + node.failureSummary).join(" | ")}`).join("\n");
async function setTheme(page: Page, theme: "light" | "dark") {
  await page.evaluate((value) => { document.documentElement.dataset.theme = value; }, theme);
  await page.waitForTimeout(150);
}
async function headings(page: Page) {
  return page.evaluate(() => [...document.querySelectorAll("main h1, main h2, main h3, main h4, main h5")].filter((node) => (node as HTMLElement).offsetParent !== null).map((node) => Number(node.tagName.slice(1))));
}
function hierarchyProblems(levels: number[]) {
  const problems: string[] = [];
  if (levels.filter((level) => level === 1).length !== 1) problems.push(`h1 sayısı ${levels.filter((level) => level === 1).length}`);
  levels.forEach((level, index) => { if (index > 0 && level > levels[index - 1] + 1) problems.push(`h${levels[index - 1]} → h${level} atlaması (sıra ${index})`); });
  return problems;
}

const surfaces: { name: string; role: Role | null; open: (page: Page) => Promise<void> }[] = [
  { name: "katalog (kart)", role: null, open: async (page) => { await page.goto("/tr/oyunlar"); await expect(page.getByRole("link", { name: "Seed Oyun 01", exact: true })).toBeVisible(); } },
  { name: "katalog (liste + filtre paneli)", role: null, open: async (page) => { await page.goto("/tr/oyunlar?view=list&genre=rpg"); await page.getByRole("button", { name: new RegExp(String.raw`^${t.filters}\s*\d*$`) }).click(); await expect(page.getByRole("form", { name: t.filters })).toBeVisible(); } },
  { name: "oyun detayı", role: null, open: async (page) => { await page.goto(`/tr/oyunlar/${game.id}`); await expect(page.getByRole("heading", { level: 1, name: game.title })).toBeVisible(); await expect(page.locator("#game-information")).toBeVisible(); } },
  { name: "geri bildirimlerim", role: "player", open: async (page) => { await page.goto("/tr/oyunlar?tab=feedback"); await expect(page.getByRole("heading", { name: "Başlangıçta çöküyor", exact: true })).toBeVisible(); } },
  { name: "admin: oyun düzenleme", role: "admin", open: async (page) => { await page.goto(`/tr/oyunlar/yonetim?gameId=${game.id}`); await expect(page.getByRole("form", { name: t.editGame, exact: true })).toBeVisible(); } },
  { name: "admin: editör listesi", role: "admin", open: async (page) => { await page.goto("/tr/oyunlar/yonetim"); await page.getByRole("tab", { name: t.popularSelection, exact: true }).click(); await expect(page.getByText(t.popularEmptyList)).toBeVisible(); } },
  { name: "admin: katalog durumu", role: "admin", open: async (page) => { await page.goto("/tr/oyunlar/yonetim"); await page.getByRole("tab", { name: t.catalogStatus, exact: true }).click(); await expect(page.getByText(t.statusFields.mainGameEntries)).toBeVisible(); } },
  { name: "geliştirici: medya", role: "developer", open: async (page) => { await page.goto("/tr/oyunlar/yonetim"); await page.getByRole("tab", { name: t.tabs.media, exact: true }).click(); await expect(page.getByRole("form", { name: t.addMedia, exact: true })).toBeVisible(); } },
  { name: "geliştirici: geri bildirim", role: "developer", open: async (page) => { await page.goto("/tr/oyunlar/yonetim"); await page.getByRole("tab", { name: t.receivedFeedback, exact: true }).click(); await expect(page.getByRole("heading", { name: "Başlangıçta çöküyor", exact: true })).toBeVisible(); } },
  { name: "moderasyon", role: "moderator", open: async (page) => { await page.goto("/tr/oyunlar/yonetim"); await page.getByRole("searchbox", { name: t.pickerLabel, exact: true }).fill(game.title); await page.getByRole("button", { name: new RegExp(`^${game.title}\\s*${t.pickerSelect}$`) }).click(); await expect(page.getByRole("form", { name: t.moderateContent, exact: true })).toBeVisible(); } }
];

test.describe("10 · Responsive, erişilebilirlik, klavye ve reduced-motion (gerçek veriyle)", () => {
  for (const width of [390, 768, 1440]) {
    test(`yatay taşma yok: tüm Game yüzeyleri (${width} px, açık+koyu)`, async ({ browser, page: guest }) => {
      for (const surface of surfaces) {
        const session = surface.role ? await openAs(browser, surface.role, { width, height: 900 }) : { context: null, page: guest };
        await session.page.setViewportSize({ width, height: 900 });
        await surface.open(session.page);
        for (const theme of ["light", "dark"] as const) {
          await setTheme(session.page, theme);
          const overflow = await session.page.evaluate(() => ({ page: document.documentElement.scrollWidth - innerWidth, wide: [...document.querySelectorAll("main *")].filter((node) => (node as HTMLElement).offsetParent !== null && node.getBoundingClientRect().right > innerWidth + 1).slice(0, 3).map((node) => node.tagName + "." + String((node as HTMLElement).className).slice(0, 60)) }));
          expect(overflow.page, `${surface.name} @${width}/${theme}: taşan öğeler ${JSON.stringify(overflow.wide)}`).toBeLessThanOrEqual(0);
        }
        await session.page.screenshot({ path: `test-results/game-real-${surface.name.replace(/[^a-z0-9]+/gi, "-")}-${width}.png`, fullPage: true });
        if (session.context) await session.page.close();
      }
    });
  }

  for (const theme of ["light", "dark"] as const) {
    test(`otomatik erişilebilirlik (axe-core, WCAG 2.2 AA, ${theme}): tüm Game yüzeyleri`, async ({ browser, page: guest }) => {
      const failures: string[] = [];
      for (const surface of surfaces) {
        const session = surface.role ? await openAs(browser, surface.role, { width: 1280, height: 900 }) : { context: null, page: guest };
        await session.page.setViewportSize({ width: 1280, height: 900 });
        await surface.open(session.page);
        await setTheme(session.page, theme);
        const violations = await axeAudit(session.page);
        if (violations.length) failures.push(`## ${surface.name}\n${describeViolations(violations)}`);
        if (session.context) await session.page.close();
      }
      expect(failures.join("\n\n"), "axe ihlalleri").toBe("");
    });
  }

  test("başlık hiyerarşisi: her yüzeyde tek h1 ve seviye atlaması yok", async ({ browser, page: guest }) => {
    const problems: string[] = [];
    for (const surface of surfaces) {
      const session = surface.role ? await openAs(browser, surface.role) : { context: null, page: guest };
      await surface.open(session.page);
      const found = hierarchyProblems(await headings(session.page));
      if (found.length) problems.push(`${surface.name}: ${found.join(", ")}`);
      if (session.context) await session.page.close();
    }
    expect(problems.join("\n")).toBe("");
  });

  test("klavye: katalogda Tab ile kartlara ulaşılır, odak görünür, Enter detaya götürür; sekmeler ok tuşlarıyla gezilir", async ({ page }) => {
    await page.goto("/tr/oyunlar");
    await expect(page.getByRole("link", { name: "Seed Oyun 01", exact: true })).toBeVisible();
    const reach = async (name: string) => {
      for (let step = 0; step < 60; step++) {
        await page.keyboard.press("Tab");
        if (await page.evaluate((label) => document.activeElement?.textContent?.trim() === label, name)) return true;
      }
      return false;
    };
    expect(await reach("Seed Oyun 01")).toBe(true);
    // Kartın bağlantısı odaklanınca odak göstergesi kartın çevresinde (has-[:focus-visible]) çizilir.
    const focus = await page.evaluate(() => { const style = getComputedStyle((document.activeElement?.closest("li") ?? document.activeElement) as Element); return { outline: style.outlineStyle !== "none" && parseFloat(style.outlineWidth) > 0, shadow: style.boxShadow !== "none" }; });
    expect(focus.outline || focus.shadow, "odak göstergesi").toBe(true);
    await page.keyboard.press("Enter");
    await expect(page).toHaveURL(/\/oyunlar\/[0-9a-f-]{36}$/);
    await page.goto("/tr/oyunlar");
    // Bölüm gezintisi: bağlantılar klavyeyle etkinleştirilir; etkin bölüm aria-current ile işaretlenir; geri tuşu önceki bölüme döner.
    const nav = page.getByRole("navigation", { name: t.catalogue, exact: true });
    await expect(nav.getByRole("link", { name: t.allGames, exact: true })).toHaveAttribute("aria-current", "page");
    await nav.getByRole("link", { name: t.editorsPicks, exact: true }).focus();
    await page.keyboard.press("Enter");
    await expect(page).toHaveURL(/tab=picks/);
    await expect(nav.getByRole("link", { name: t.editorsPicks, exact: true })).toHaveAttribute("aria-current", "page");
    await page.goBack();
    await expect(nav.getByRole("link", { name: t.allGames, exact: true })).toHaveAttribute("aria-current", "page");
  });

  test("klavye: detayda bölüm bağlantıları ve galeri ok tuşlarıyla kullanılır", async ({ page }) => {
    await page.goto(`/tr/oyunlar/${game.id}`);
    // Kapak sayfanın üstündedir ve galeride tekrarlanmaz: galeri yalnız ek görsel (ekran görüntüsü) varsa bulunur.
    const gallery = page.getByRole("group", { name: t.gallery });
    if (await gallery.count()) await gallery.focus();
    const position = gallery.getByText(/^\d+ \/ \d+$/);
    if (await position.count()) {
      const before = await position.textContent();
      await page.keyboard.press("ArrowRight");
      await expect(position).not.toHaveText(before ?? "");
    }
    await page.getByRole("navigation", { name: t.pageSections }).getByRole("link", { name: t.sections.developer }).focus();
    await page.keyboard.press("Enter");
    await expect(page).toHaveURL(/#game-developer$/);
  });

  test("reduced-motion: tercih açıkken iskelet animasyonu ve kart kayması kapalı; kapalıyken açık", async ({ browser }) => {
    for (const reduced of [true, false]) {
      const context = await browser.newContext({ baseURL: state!.frontend, locale: "tr-TR", reducedMotion: reduced ? "reduce" : "no-preference" });
      await stubImages(context);
      const page = await context.newPage();
      let release: () => void = () => {};
      const gate = new Promise<void>((resolve) => { release = resolve; });
      await page.route(new RegExp(`${gw.replace(/[.]/g, "\\.")}/api/v1/game/games\\?`), async (route) => { await gate; await route.continue(); });
      await page.goto("/tr/oyunlar");
      const skeleton = page.locator("ul[aria-hidden='true'] li > div").first();
      await expect(skeleton).toBeVisible();
      const animation = await skeleton.evaluate((node) => getComputedStyle(node).animationName);
      expect(animation, `reducedMotion=${reduced}`).toBe(reduced ? "none" : "pulse");
      release();
      const card = page.getByRole("link", { name: "Seed Oyun 01", exact: true }).locator("xpath=ancestor::li[1]");
      await expect(card).toBeVisible();
      await card.hover();
      await page.waitForTimeout(350);
      // Tailwind v4 kaymayı `translate` özelliğiyle uygular (transform değil).
      const transform = await card.evaluate((node) => getComputedStyle(node).translate);
      if (reduced) expect(transform, "kart hover kayması").toBe("none");
      else expect(transform, "kart hover kayması").not.toBe("none");
      await context.close();
    }
  });
});

test.describe("11 · Sayfa envanteri ve rol bazlı erişim (gezinti → sayfa; gerçek Auth rolleri)", () => {
  const publicLinks = [t.allGames, t.editorsPicks];
  const matrix: { role: Role | null; visible: string[]; hidden: string[] }[] = [
    { role: null, visible: publicLinks, hidden: [t.myFeedback, t.workspace.developer, t.workspace.admin, t.workspace.moderation] },
    { role: "player", visible: [...publicLinks, t.myFeedback], hidden: [t.workspace.developer, t.workspace.admin, t.workspace.moderation] },
    { role: "developer", visible: [...publicLinks, t.myFeedback, t.workspace.developer], hidden: [t.workspace.admin, t.workspace.moderation] },
    { role: "admin", visible: [...publicLinks, t.myFeedback, t.workspace.admin, t.workspace.moderation], hidden: [t.workspace.developer] },
    { role: "moderator", visible: [...publicLinks, t.myFeedback, t.workspace.moderation], hidden: [t.workspace.developer, t.workspace.admin] }
  ];
  const destination = (label: string, page: Page) => {
    if (label === t.allGames) return expect(page).toHaveURL(/\/tr\/oyunlar$/);
    if (label === t.editorsPicks) return expect(page.getByRole("heading", { level: 2, name: t.editorsPicks, exact: true })).toBeVisible();
    if (label === t.myFeedback) return expect(page.getByRole("heading", { level: 2, name: t.myFeedback, exact: true })).toBeVisible();
    return expect(page.getByRole("heading", { level: 1, name: label, exact: true })).toBeVisible();
  };

  for (const row of matrix) {
    test(`gezinti görünürlüğü ve her bağlantının açtığı sayfa: ${row.role ?? "misafir"}`, async ({ browser, page: guest }) => {
      const session = row.role ? await openAs(browser, row.role, { width: 1280, height: 900 }) : { context: null, page: guest };
      const page = session.page;
      await page.goto("/tr/oyunlar");
      const nav = page.getByRole("navigation", { name: t.catalogue, exact: true });
      for (const label of row.visible) await expect(nav.getByRole("link", { name: label, exact: true })).toBeVisible();
      for (const label of row.hidden) await expect(nav.getByRole("link", { name: label, exact: true })).toHaveCount(0);
      for (const label of row.visible) {
        await page.goto("/tr/oyunlar");
        await nav.getByRole("link", { name: label, exact: true }).click();
        await destination(label, page);
        await expect(page.getByRole("main").getByRole("alert")).toHaveCount(0);
        await noOverflow(page);
      }
      if (session.context) await session.page.close();
    });
  }

  test("oyun detayı: bölüm gezintisi görünür; ADMIN detaydan yönetime geçebilir", async ({ browser, page }) => {
    await page.goto(`/tr/oyunlar/${game.id}`);
    await expect(page.getByRole("navigation", { name: t.catalogue, exact: true }).getByRole("link", { name: t.allGames, exact: true })).toBeVisible();
    const admin = await openAs(browser, "admin");
    await admin.page.goto(`/tr/oyunlar/${game.id}`);
    await admin.page.getByRole("navigation", { name: t.catalogue, exact: true }).getByRole("link", { name: t.workspace.admin, exact: true }).click();
    await expect(admin.page.getByRole("heading", { level: 1, name: t.workspace.admin, exact: true })).toBeVisible();
    await keep(admin.context);
  });

  test("doğrudan URL yetki denetimi: yetkisiz bölüm istenirse ilk yetkili bölüme düşer; yetkisiz kullanıcı hiçbir yönetim formu görmez", async ({ browser, page: guest }) => {
    const player = await openAs(browser, "player");
    await player.page.goto("/tr/oyunlar/yonetim?section=admin");
    await expect(player.page.getByText(t.noWorkspaceAccess)).toBeVisible();
    await expect(player.page.getByRole("form")).toHaveCount(0);
    await keep(player.context);
    const developer = await openAs(browser, "developer");
    await developer.page.goto("/tr/oyunlar/yonetim?section=admin");
    await expect(developer.page.getByRole("heading", { level: 1, name: t.workspace.developer, exact: true })).toBeVisible();
    await expect(developer.page.getByRole("form", { name: t.createGame })).toHaveCount(0);
    await keep(developer.context);
    const moderator = await openAs(browser, "moderator");
    await moderator.page.goto("/tr/oyunlar/yonetim?section=developer");
    await expect(moderator.page.getByRole("heading", { level: 1, name: t.workspace.moderation, exact: true })).toBeVisible();
    await keep(moderator.context);
    await guest.goto("/tr/oyunlar/yonetim?section=admin");
    await expect(guest.getByText(t.signInRequired)).toBeVisible();
    await expect(guest.getByRole("form")).toHaveCount(0);
  });
});

test.describe("12 · Steam indeks araması (GET /api/v1/game/catalog, gerçek Game API)", () => {
  test("ADMIN katalog sekmesinde indeks arar: gerçek istek parametreleri ve dürüst boş sonuç (test DB'sinde Steam referanslı kayıt yok)", async ({ browser }) => {
    const { context, page } = await openAs(browser, "admin");
    await page.goto("/tr/oyunlar/yonetim");
    await page.getByRole("tab", { name: t.catalogStatus, exact: true }).click();
    const requests: URL[] = [];
    page.on("request", (request) => { const url = new URL(request.url()); if (url.pathname === "/api/v1/game/catalog") requests.push(url); });
    const form = page.getByRole("form", { name: t.indexSearch, exact: true });
    await expect(form.getByRole("button", { name: t.indexSearchButton, exact: true })).toBeDisabled();
    await form.getByRole("searchbox", { name: t.search, exact: true }).fill("Seed");
    await form.getByRole("checkbox", { name: t.includeUnclassified }).check();
    const response = page.waitForResponse((value) => new URL(value.url()).pathname === "/api/v1/game/catalog");
    await form.getByRole("button", { name: t.indexSearchButton, exact: true }).click();
    expect((await response).status()).toBe(200);
    expect(requests[0].searchParams.get("q")).toBe("Seed");
    expect(requests[0].searchParams.get("includeUnclassified")).toBe("true");
    await expect(page.getByText(t.indexNoResults)).toBeVisible();
    await keep(context);
  });
  test("PLAYER bu uca arayüzden ulaşamaz (yönetim bölümü yok); uç herkese açık okunur ama sahte sonuç üretilmez", async ({ browser }) => {
    const { context, page } = await openAs(browser, "player");
    await page.goto("/tr/oyunlar/yonetim");
    await expect(page.getByText(t.noWorkspaceAccess)).toBeVisible();
    await expect(page.getByRole("form", { name: t.indexSearch })).toHaveCount(0);
    await keep(context);
  });
});

test.describe("13 · Herkese açık Steam indeksi (GET /api/v1/game/catalog; gerçek Game API)", () => {
  test("herkese açık indeks sekmesi yoktur; ?tab=index adresi yalnız Tüm oyunlar görünümüne düşer ve ana katalog sınıflandırılmamış kayıtları listelemez", async ({ page }) => {
    await page.goto("/tr/oyunlar");
    await expect(page.getByRole("navigation", { name: t.catalogue, exact: true }).getByRole("link", { name: t.indexSearch, exact: true })).toHaveCount(0);
    await page.goto("/tr/oyunlar?tab=index");
    await expect(page.getByRole("form", { name: t.indexSearch })).toHaveCount(0);
    await page.goto("/tr/oyunlar?q=Dead%20Index");
    await expect(page.getByText(t.emptySearch.replace("{query}", "Dead Index"))).toBeVisible();
    await expect(page.getByRole("link", { name: /Dead Index/ })).toHaveCount(0);
  });

  test("boş arama ve boş filtre sonucu ayrı mesajlarla gösterilir", async ({ page }) => {
    await page.goto("/tr/oyunlar?q=zzzz-yok");
    await expect(page.getByText(t.emptySearch.replace("{query}", "zzzz-yok"))).toBeVisible();
    await page.goto("/tr/oyunlar?genre=yok-boyle-bir-tur");
    await expect(page.getByRole("button", { name: t.clearFilters }).first()).toBeVisible();
  });
});
