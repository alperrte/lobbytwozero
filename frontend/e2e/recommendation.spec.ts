import { expect, test, type Page } from "@playwright/test";
import { chooseOption } from "./fixtures/select";

// Tarayıcı sözleşme fixture'ları: Gateway yanıtları `docs/api.md` RECOMMENDATION sözleşmesine göre taklit edilir.
// Canlı backend yetkilendirmesini veya gerçek öneri kalitesini kanıtlamaz.
const accountId = "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66";
const profileId = "0198a3c2-cccc-7d44-9a55-3f1c2b4d5e66";
const runId = "0198a3c2-dddd-7d44-9a55-3f1c2b4d5e66";
const freshRunId = "0198a3c2-eeee-7d44-9a55-3f1c2b4d5e66";
const friendId = "0198a3c2-ffff-7d44-9a55-3f1c2b4d5e66";
const eventId = "0198a3c2-1111-7d44-9a55-3f1c2b4d5e66";
const gameA = "0198a3c2-2222-7d44-9a55-3f1c2b4d5e66";
const gameB = "0198a3c2-3333-7d44-9a55-3f1c2b4d5e66";
const now = "2026-11-20T10:00:00Z";

const titles: Record<string, string> = { [gameA]: "Kuzey Yolu", [gameB]: "Demir Liman" };
const item = (index: number, gameId: string) => ({ itemId: `0198a3c2-${index}${index}${index}${index}-7d44-9a55-3f1c2b4d5e66`, gameId, score: 90 - index, rank: index, reasonCode: "GENRE_RATING_AFFINITY", contribution: 90 - index, matchedGenres: ["rpg"] });
const run = (id: string, genre: string | null = null) => ({ runId: id, algorithmVersion: "rec-v1", createdAt: now, status: "PARTIAL", genre, platform: null, items: [item(1, gameA), item(2, gameB)], limitations: [] });
const levelOf = (level: string) => ({ level, computeStatus: "UNKNOWN", overallStatus: "UNKNOWN", checks: [{ field: "GPU", status: "UNKNOWN", actual: "RTX 3060", required: null, reason: "MODEL_ORDER_OR_REQUIREMENT_UNKNOWN" }] });
const assessment = {
  hardwareProfileId: profileId, hardwareProfileVersion: 2, gameId: gameA, platform: "windows", assessedAt: now, suitability: "UNKNOWN",
  minimum: levelOf("MINIMUM"), recommended: levelOf("RECOMMENDED"),
  fps: { status: "INSUFFICIENT_DATA", estimatedLowerFps: null, estimatedUpperFps: null, confidence: "NONE", observedSampleCount: 0, observedSessionCount: 0, producerSampleThresholdMet: false, lastObservedAt: null, requestedResolution: null, requestedPreset: null, reason: "NO_FPS_SAMPLES", evidence: null },
  graphics: { status: "INSUFFICIENT_DATA", suggestedPreset: null, reason: "MINIMUM_COMPUTE_OR_COMPONENTS_NOT_CONFIRMED" },
  upgrades: [], componentCompatibility: { status: "UNKNOWN", checks: [], limitations: [] }, limitations: ["UNKNOWN_IS_NOT_KNOWN_INCOMPATIBLE"]
};

type Options = { consent?: boolean; friendsStatus?: number; emptyRuns?: boolean };
type Logged = { method: string; path: string; body: unknown; csrf: string | undefined };

async function installFixture(page: Page, options: Options = {}) {
  await page.context().addCookies([{ name: "ltz_session", value: "1", url: "http://localhost:5101" }, { name: "XSRF-TOKEN", value: "test-csrf", url: "http://localhost:5101" }]);
  const requests: Logged[] = [];
  let consent = { recommendation: options.consent ?? true, version: 3 };
  await page.route("http://localhost:5080/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    const headers = { "access-control-allow-origin": "http://localhost:5101", "access-control-allow-credentials": "true", "access-control-allow-methods": "GET, POST, PUT, OPTIONS", "access-control-allow-headers": "content-type, accept, x-xsrf-token", "cache-control": "private, no-store" };
    const reply = (json: unknown, status = 200) => route.fulfill({ json, status, headers });
    if (method === "OPTIONS") return route.fulfill({ status: 204, headers });
    const body = request.postData() ? JSON.parse(request.postData() as string) : null;
    requests.push({ method, path: path + url.search, body, csrf: request.headers()["x-xsrf-token"] });

    if (path === "/api/v1/auth/me") return reply({ accountId, roles: ["PLAYER"], authMethods: ["pwd"], accessTokenExpiresAt: "2030-01-01T00:00:00Z" });
    if (path === "/api/v1/user/me") return reply({ userId: accountId, displayName: "Fixture player", username: "fixture", avatarUrl: null });
    if (path === "/api/v1/user/me/consents") {
      if (method === "PUT") consent = { recommendation: body.recommendation, version: consent.version + 1 };
      return reply({ ...consent, marketing: false });
    }
    if (!consent.recommendation && path.startsWith("/api/v1/recommendation/")) return reply({ type: "about:blank", title: "Forbidden", status: 403, code: "CONSENT_REQUIRED" }, 403);

    if (path === "/api/v1/recommendation/runs") return reply({ items: options.emptyRuns ? [] : [{ runId, algorithmVersion: "rec-v1", createdAt: now, resultCount: 2 }], nextCursor: null });
    if (path === `/api/v1/recommendation/runs/${runId}`) return reply(run(runId));
    if (path === `/api/v1/recommendation/runs/${freshRunId}`) return reply(run(freshRunId, "rpg"));
    const feedback = path.match(/^\/api\/v1\/recommendation\/runs\/[^/]+\/items\/([^/]+)\/feedback$/);
    if (feedback) return reply(method === "PUT" ? { itemId: feedback[1], value: body.feedback, version: body.version + 1 } : { itemId: feedback[1], value: null, version: 0 });
    if (path === "/api/v1/recommendation/games" && method === "POST") return reply(run(freshRunId, body.genre ?? null), 201);
    if (path === "/api/v1/recommendation/games/hardware-compatible") return reply({ algorithmVersion: "rec-v1", createdAt: now, hardwareProfileId: profileId, platform: "windows", items: [{ gameId: gameA, affinity: 4, matchedGenres: ["rpg"], compatibility: "UNKNOWN", reason: "MINIMUM_NOT_PROVEN" }, { gameId: gameB, affinity: 3, matchedGenres: [], compatibility: "MEETS_MINIMUM_COMPUTE", reason: "MINIMUM_CPU_GPU_RAM_PROVEN" }], excludedBelowMinimum: 1, limitations: [] });
    if (path === "/api/v1/recommendation/hardware/assessments") return reply(assessment);
    if (path === "/api/v1/recommendation/friends") return options.friendsStatus ? reply({ type: "about:blank", title: "Unavailable", status: options.friendsStatus }, options.friendsStatus) : reply({ kind: "FRIEND_SUGGESTIONS", algorithmVersion: "rec-v1", createdAt: now, items: [{ userId: friendId, score: 5, sharedGenres: ["rpg"], sharedPlatforms: ["pc"], sharedPlayerTypes: [] }], limitations: [] });
    if (path === "/api/v1/recommendation/players") return reply({ kind: "SIMILAR_PLAYERS", algorithmVersion: "rec-v1", createdAt: now, items: [{ userId: friendId, score: 5, sharedGenres: ["rpg"], sharedPlatforms: [], sharedPlayerTypes: [] }], limitations: [] });
    if (path === "/api/v1/recommendation/events") return reply({ algorithmVersion: "rec-v1", createdAt: now, items: [{ eventId, type: "TOURNAMENT", title: "Kış Kupası", startsAt: "2026-12-05T17:00:00Z", endsAt: "2026-12-05T20:00:00Z", gameId: gameA, score: 3, sharedGenres: ["rpg"], sharedPlatforms: [] }], limitations: [] });
    if (path === "/api/v1/recommendation/assistant") return reply({ contractVersion: "assistant-v1", algorithmVersion: "rec-v1", topic: body.topic, intent: body.intent, locale: body.locale, status: "GUIDANCE", template: "history", message: "Geçmiş listelerin Öneri geçmişi ekranında.", steps: ["OPEN_RECOMMENDATION_HISTORY"], missingFields: [] });

    if (path === "/api/v1/game/terms") return reply({ items: url.searchParams.get("kind") === "GENRE" ? [{ code: "rpg", nameTr: "Rol yapma", nameEn: "Role-playing", gameCount: 2 }] : [{ code: "pc", nameTr: "PC", nameEn: "PC", gameCount: 2 }] });
    if (path === "/api/v1/game/games/batch") return reply({ items: body.gameIds.map((gameId: string) => ({ gameId, title: titles[gameId] ?? "Oyun", coverUrl: null, externalReferences: [] })) });
    if (path === "/api/v1/game/games/recommendation-metadata/batch") return reply({ items: body.gameIds.map((gameId: string) => ({ gameId, genres: [{ code: "rpg" }], platforms: [{ code: "pc" }], systemRequirements: [{ platform: "windows" }] })) });
    if (path === "/api/v1/user/profiles/batch") return reply({ items: [{ userId: friendId, username: "deniz", displayName: "Deniz", avatar: null }] });
    if (path === "/api/v1/social/friend-requests") return reply({ id: "r-1" }, 201);
    if (path === `/api/v1/event/events/${eventId}/participations`) return reply({ id: "p-1", eventId, status: "JOINED" }, 201);
    if (path === "/api/v1/hardware/profiles") return reply({ items: [{ id: profileId, name: "Fixture computer", deviceType: "DESKTOP", caseColor: "BLACK", source: "MANUAL", version: 0, createdAt: now, updatedAt: now }], page: 0, size: 12, totalElements: 1, totalPages: 1 });
    return reply({}, 404);
  });
  return requests;
}

const generationPosts = (requests: Logged[]) => requests.filter((entry) => entry.method === "POST" && entry.path === "/api/v1/recommendation/games");

test("kenar çubuğu Sana Özel'i açar; açılışta öneri üretilmez", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  const requests = await installFixture(page);
  await page.goto("/tr");
  await page.getByRole("navigation", { name: "Ana gezinme" }).getByRole("link", { name: "Sana Özel", exact: true }).click();
  await expect(page).toHaveURL(/\/tr\/sana-ozel$/);
  const main = page.getByRole("main");
  await expect(main.getByRole("heading", { level: 1, name: "Sana Özel" })).toBeVisible();
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
  await expect(main.getByRole("link", { name: "Kuzey Yolu" }).first()).toBeVisible();
  await expect(main.getByText("Deniz").first()).toBeVisible();
  await expect(main.getByText("Kış Kupası")).toBeVisible();
  expect(generationPosts(requests)).toHaveLength(0);
  expect(await main.textContent()).not.toMatch(/\d+\s?%/);
});

test("oyun önerisi: filtreyle oluştur, beğen (CSRF) ve geçmişte aç", async ({ page }) => {
  const requests = await installFixture(page);
  await page.goto("/tr/sana-ozel/oyunlar");
  const main = page.getByRole("main");
  await expect(main.getByRole("heading", { name: "Son önerilerin" })).toBeVisible();
  expect(generationPosts(requests)).toHaveLength(0);

  await chooseOption(main.getByRole("combobox", { name: "Tür" }), "rpg");
  await main.getByRole("button", { name: "Öneri oluştur", exact: true }).click();
  await expect(main.getByRole("heading", { name: "Yeni önerilerin" })).toBeVisible();
  const [post] = generationPosts(requests);
  expect(post?.body).toMatchObject({ genre: "rpg" });
  expect(post?.csrf).toBe("test-csrf");

  const like = main.getByRole("group", { name: "Kuzey Yolu için geri bildirim" }).getByRole("button", { name: "Beğendim" });
  await like.click();
  await expect(like).toHaveAttribute("aria-pressed", "true");
  const put = requests.find((entry) => entry.method === "PUT" && entry.path.endsWith("/feedback"));
  expect(put?.body).toMatchObject({ feedback: "LIKE" });
  expect(put?.csrf).toBe("test-csrf");

  await page.goto("/tr/sana-ozel/gecmis");
  await main.getByRole("link", { name: /Aç/ }).first().click();
  await expect(page).toHaveURL(new RegExp(`/tr/sana-ozel/gecmis/${runId}$`));
  await expect(main.getByRole("link", { name: "Demir Liman" }).first()).toBeVisible();
});

test("geçmiş boşsa dürüst boş durum gösterilir", async ({ page }) => {
  await installFixture(page, { emptyRuns: true });
  await page.goto("/tr/sana-ozel/oyunlar");
  await expect(page.getByRole("main").getByText("Henüz öneri oluşturmadın")).toBeVisible();
});

test("donanım: doğrulanamayan oyun 'uyumlu' gösterilmez; ayrıntılı değerlendirme açılır", async ({ page }) => {
  const requests = await installFixture(page);
  await page.goto("/tr/sana-ozel/donanim");
  const main = page.getByRole("main");
  await chooseOption(main.getByRole("combobox", { name: "Bilgisayar" }), profileId);
  await chooseOption(main.getByRole("combobox", { name: "Platform" }), "windows");
  await main.getByRole("button", { name: "Uygun oyunları getir" }).click();
  await expect(main.getByText("Doğrulanamadı", { exact: true })).toBeVisible();
  await expect(main.getByText("Minimum CPU/GPU/RAM karşılanıyor", { exact: true })).toBeVisible();
  expect(requests.find((entry) => entry.path.startsWith("/api/v1/recommendation/games/hardware-compatible"))?.path).toContain(`profileId=${profileId}`);

  await main.getByRole("button", { name: /Ayrıntılı değerlendir/ }).first().click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("button", { name: "Değerlendir", exact: true }).click();
  await expect(dialog.getByText("Uyumluluk doğrulanamadı")).toBeVisible();
  const post = requests.find((entry) => entry.path === "/api/v1/recommendation/hardware/assessments");
  expect(post?.body).toMatchObject({ hardwareProfileId: profileId, platform: "windows" });
});

test("arkadaş önerisi ve etkinlik katılımı gerçek uçlara gider", async ({ page }) => {
  const requests = await installFixture(page);
  await page.goto("/tr/sana-ozel/arkadaslar");
  const main = page.getByRole("main");
  await main.getByRole("button", { name: "Deniz kişisini arkadaş olarak ekle" }).click();
  await expect(main.getByRole("status").filter({ hasText: "İstek gönderildi" })).toBeVisible();
  expect(requests.find((entry) => entry.path === "/api/v1/social/friend-requests")?.body).toEqual({ receiverId: friendId });

  await page.goto("/tr/sana-ozel/etkinlikler");
  await main.getByRole("button", { name: "Kış Kupası etkinliğine katıl" }).click();
  await expect(main.getByRole("status").filter({ hasText: "Katıldın" })).toBeVisible();
  expect(requests.some((entry) => entry.method === "POST" && entry.path === `/api/v1/event/events/${eventId}/participations`)).toBe(true);
});

test("rıza yoksa kapı gösterilir; 'İzin ver' gerçek PUT atar", async ({ page }) => {
  const requests = await installFixture(page, { consent: false });
  await page.goto("/tr/sana-ozel");
  const main = page.getByRole("main");
  await expect(main.getByRole("heading", { name: "Kişisel öneriler için izin gerekiyor" })).toBeVisible();
  expect(requests.some((entry) => entry.path.startsWith("/api/v1/recommendation/"))).toBe(false);
  await main.getByRole("button", { name: "İzin ver" }).click();
  await expect(main.getByRole("heading", { name: "Kişisel öneriler için izin gerekiyor" })).toHaveCount(0);
  const put = requests.find((entry) => entry.method === "PUT" && entry.path === "/api/v1/user/me/consents");
  expect(put?.body).toEqual({ version: 3, recommendation: true });
  expect(put?.csrf).toBe("test-csrf");
});

test("bir bölüm düşerse yalnız o bölüm hata gösterir", async ({ page }) => {
  await installFixture(page, { friendsStatus: 503 });
  await page.goto("/tr/sana-ozel");
  const main = page.getByRole("main");
  await expect(main.getByRole("alert").first()).toBeVisible();
  await expect(main.getByText("Kış Kupası")).toBeVisible();
  await expect(main.getByRole("link", { name: "Kuzey Yolu" }).first()).toBeVisible();
});

test("LTZ Rehber serbest metin almaz ve adımı gerçek rotaya bağlar", async ({ page }) => {
  const requests = await installFixture(page);
  await page.goto("/tr/sana-ozel");
  await page.getByRole("button", { name: "LTZ Rehber" }).first().click();
  const dialog = page.getByRole("dialog", { name: "LTZ Rehber" });
  await expect(dialog.getByRole("textbox")).toHaveCount(0);
  await dialog.getByText("Eski önerilerimi görmek istiyorum", { exact: true }).click();
  await expect(dialog.getByRole("radio", { name: /Eski önerilerimi görmek istiyorum/ })).toBeChecked();
  await dialog.getByRole("button", { name: "Yol göster" }).click();
  await expect(dialog.getByText("Geçmiş listelerin Öneri geçmişi ekranında.")).toBeVisible();
  expect(requests.find((entry) => entry.path === "/api/v1/recommendation/assistant")?.body).toEqual({ contractVersion: "assistant-v1", topic: "LTZ_USAGE", intent: "VIEW_HISTORY", locale: "tr" });
  await dialog.getByRole("link").first().click();
  await expect(page).toHaveURL(/\/tr\/sana-ozel\/gecmis$/);
});

for (const viewport of [{ width: 375, height: 812 }, { width: 768, height: 1024 }, { width: 1440, height: 1000 }]) {
  for (const scheme of ["light", "dark"] as const) {
    test(`hub ${viewport.width}px ${scheme}: taşma yok (tr + en)`, async ({ page }) => {
      await page.setViewportSize(viewport);
      await page.emulateMedia({ colorScheme: scheme });
      await installFixture(page);
      for (const [locale, path, heading] of [["tr", "/tr/sana-ozel", "Sana Özel"], ["en", "/en/for-you", "For You"]] as const) {
        await page.goto(path);
        await expect(page.getByRole("main").getByRole("heading", { level: 1, name: heading })).toBeVisible();
        await expect(page.getByRole("main").getByText("Kış Kupası")).toBeVisible();
        await expect(page.locator("html")).toHaveAttribute("lang", locale);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await page.screenshot({ path: `test-results/playwright/recommendation-${locale}-${viewport.width}-${scheme}.png`, fullPage: true });
      }
    });
  }
}
